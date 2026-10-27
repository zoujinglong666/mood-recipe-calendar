package com.moodrecipe.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 轻量限流（单实例内存实现，无需 Redis）：缓解扫描、爆破与突发流量。
 *
 * <p>身份与维度（[2026-10-27] 安全加固，修复审查指出的 P0/中危）：
 * <ul>
 *   <li><b>可信代理校验</b>：只有当直连对端（remoteAddr）落在可信代理网段内才采信
 *       X-Forwarded-For，并从最右向左跳过可信代理取第一个不可信 IP；否则一律用
 *       remoteAddr——攻击者直连应用端口伪造 XFF 无法再让限流桶分裂失效；</li>
 *   <li><b>鉴权/管理路径一律按 IP</b>：防爆破维度不能被伪造 token 分裂，session token
 *       在验证前只是客户端自报的一串字符；</li>
 *   <li><b>业务路径按 token 分桶，但单 IP 的 token 桶数封顶</b>（{@link #MAX_TOKENS_PER_IP}）：
 *       超限自动塌缩回 IP 桶——伪造海量 token 的增殖攻击失效；上限设宽（64）以容纳
 *       校园/公司 WiFi 下多真实用户共享出口 IP 的 NAT 场景；</li>
 *   <li><b>业务路径叠加单 IP 硬顶</b>（general×4）：即使 token 分桶合法存在，
 *       单一出口 IP 的总吞吐也被封顶，内存与后端都有界。</li>
 * </ul>
 *
 * <p><b>不封禁，只渐进限速</b>（用户红线：任何 IP 永不被完全锁死）：/auth/ 路径每出现
 * 一次 401，该 IP 的登录配额按 AUTH_FAIL_PENALTY 递减（下限 AUTH_MIN_FLOOR），失败窗口
 * 60s 内未再失败即自然恢复。正常用户几乎无感；爆破者被连续压到每分钟个位数尝试。
 *
 * <p>已知取舍：固定时间窗在窗口切换瞬间存在 ~2×limit 的边界突发。当前规模下防滥用足够；
 * 若未来有对外 API 或真实流量压力，把限流上移到 nginx limit_req / 网关令牌桶（Redis），
 * 应用内保留本兜底（见 AGENTS.md）。
 *
 * <p>放行健康检查、静态资源、微信回调等低频/可信路径；超限返回 429。
 */
@Component
@Order(-100)
@EnableScheduling
public class RateLimitFilter extends OncePerRequestFilter {

    private final long generalLimit;
    private final long authLimit;
    private final long pollLimit;
    private final List<Cidr> trustedProxies;
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(RateLimitFilter.class);
    private static final long WINDOW_MS = 60_000;
    /** /auth/ 每次登录失败（401）扣减的配额；失败窗口 60s 内未再失败即自然恢复。 */
    private static final long AUTH_FAIL_PENALTY = 8;
    /** 渐进限速的下限：永不归零，保证"最差是慢"，而不是"进不来"。 */
    private static final long AUTH_MIN_FLOOR = 6;
    /** 单 IP 允许出现的不同 token 桶数上限：超过即视为伪造 token 增殖，整 IP 塌缩回 IP 桶。 */
    private static final int MAX_TOKENS_PER_IP = 64;
    /** 业务路径单 IP 硬顶 = generalLimit × 此倍数（轮询桶不参与，伪造 token 的轮询请求在会话校验即被 401，成本极低）。 */
    private static final long IP_CAP_MULTIPLIER = 4;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Map<String, FailBucket> fails = new ConcurrentHashMap<>();
    private final Map<String, TokenWindow> tokensPerIp = new ConcurrentHashMap<>();

    public RateLimitFilter(@Value("${security.ratelimit.general-per-minute:180}") long generalLimit,
                           @Value("${security.ratelimit.auth-per-minute:90}") long authLimit,
                           @Value("${security.ratelimit.poll-per-minute:240}") long pollLimit,
                           @Value("${security.ratelimit.trusted-proxies:127.0.0.1/32,::1,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16}")
                           String trustedProxies) {
        this.generalLimit = generalLimit;
        this.authLimit = authLimit;
        this.pollLimit = pollLimit;
        this.trustedProxies = parseCidrs(trustedProxies);
    }

    /** [2026-10-27] 429 可观测：每次限流拦截都计数，进每日健康摘要。红线：正常用户 429 应恒为 0。 */
    private com.moodrecipe.backend.common.HealthMetrics healthMetrics;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setHealthMetrics(com.moodrecipe.backend.common.HealthMetrics healthMetrics) {
        this.healthMetrics = healthMetrics;
    }

    /** [2026-10-27] 限流告警（WxPusher）：429 出现即主动上报（10 分钟窗口去重），可选依赖同款 setter 注入。 */
    private com.moodrecipe.backend.service.RateLimitAlertService rateLimitAlertService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setRateLimitAlertService(com.moodrecipe.backend.service.RateLimitAlertService rateLimitAlertService) {
        this.rateLimitAlertService = rateLimitAlertService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (shouldSkip(uri)) {
            chain.doFilter(request, response);
            return;
        }
        String ip = clientIp(request);
        boolean sensitive = uri.contains("/auth/") || uri.startsWith("/api/admin/");
        // [2026-10-27] 推荐任务状态轮询是幂等 GET 且前端 900ms 一次（≈66 次/分），
        // 若计入用户 general 预算，轮询 + 详情/反馈/分享卡请求会蹭爆 120/min 造成误伤。
        // 独立大桶：既保护后端，又不挤占用户正常请求配额。
        boolean jobPoll = "GET".equalsIgnoreCase(request.getMethod())
                && uri.startsWith("/api/recipes/recommend-jobs/");
        String bucketKind = sensitive ? "auth" : jobPoll ? "poll" : "gen";
        long limit = sensitive ? effectiveAuthLimit(ip) : jobPoll ? pollLimit : generalLimit;
        // 鉴权/管理路径一律按 IP（防爆破维度不可被伪造 token 分裂）；业务路径按 token（有塌缩保护）。
        String identity = identityKey(request, sensitive, ip);
        boolean tokenIdentity = identity.startsWith("tok:");
        Bucket bucket = buckets.computeIfAbsent(identity + "|" + bucketKind, k -> new Bucket());
        if (!bucket.tryAcquire(limit)) {
            if (healthMetrics != null) healthMetrics.incrementRateLimited();
            log.warn("[限流] kind={} identity={} uri={} ip={} limit={}", bucketKind, identity, uri, ip, limit);
            if (rateLimitAlertService != null) {
                rateLimitAlertService.onRateLimited(bucketKind, identity, uri, ip, limit);
            }
            rejectTooMany(response);
            return;
        }
        // [2026-10-27] 单 IP 硬顶只约束"没有已验证 token"的请求（未登录/伪造 token 塌缩后）。
        // 已登录用户的聚合由各自 token 桶约束——共享硬顶会把 NAT（校园/公司 WiFi）下的一群
        // 真实用户连坐进同一个 720/分钟桶，这正是修复后仍出现"请求过于频繁"的元凶。
        if (!sensitive && !jobPoll && !tokenIdentity && !tryIpCap(ip)) {
            if (healthMetrics != null) healthMetrics.incrementRateLimited();
            log.warn("[限流·IP硬顶] identity={} uri={} ip={} cap={}", identity, uri, ip, generalLimit * IP_CAP_MULTIPLIER);
            if (rateLimitAlertService != null) {
                rateLimitAlertService.onRateLimited("ipcap", identity, uri, ip, generalLimit * IP_CAP_MULTIPLIER);
            }
            rejectTooMany(response);
            return;
        }
        chain.doFilter(request, response);
        // [2026-10-27] 401 只统计 /auth/ 路径（登录爆破防护的本意），且**只降配额、不封禁**：
        // 此前"15 次 401 → 封 IP 10 分钟"曾把会话过期的正常用户连同整个 WiFi 的邻居全部锁死。
        if (response.getStatus() == HttpServletResponse.SC_UNAUTHORIZED && sensitive) {
            fails.computeIfAbsent(ip, k -> new FailBucket()).record401();
        }
    }

    /** 业务路径单 IP 硬顶：token 分桶再合法，单一出口 IP 的 general 总吞吐也有界。 */
    private boolean tryIpCap(String ip) {
        Bucket cap = buckets.computeIfAbsent("ipcap|" + ip, k -> new Bucket());
        return cap.tryAcquire(generalLimit * IP_CAP_MULTIPLIER);
    }

    private void rejectTooMany(HttpServletResponse response) throws IOException {
        if (response.isCommitted()) return;
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Retry-After", "60");
        response.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\"}");
    }

    /** 登录配额 = authLimit - 失败数×惩罚，触底下限；失败窗口（60s）过了自然恢复，无需任何解封动作。 */
    private long effectiveAuthLimit(String ip) {
        FailBucket fb = fails.get(ip);
        if (fb == null) return authLimit;
        return Math.max(AUTH_MIN_FLOOR, authLimit - fb.currentCount() * AUTH_FAIL_PENALTY);
    }

    private boolean shouldSkip(String uri) {
        return uri.startsWith("/api/health")
                || uri.startsWith("/uploads/")
                || uri.startsWith("/api/wechat/virtual-payment/callback")
                || uri.startsWith("/api/breakfast-pool/seed");
    }

    /**
     * 真实客户端 IP（[2026-10-27] P0 修复：可信代理校验）。
     * 只有直连对端（remoteAddr）属于可信代理网段时才采信 XFF：从最右向左遍历、
     * 跳过可信代理，取第一个不可信 IP 作为客户端——nginx 追加模式与多级代理都正确。
     * 对端不可信（如应用端口被直连）时一律用 remoteAddr，伪造 XFF 不再生效。
     * 注意：刻意不使用 X-Real-IP——若 nginx 未配置 proxy_set_header X-Real-IP $remote_addr
     * 覆盖，客户端自带的 X-Real-IP 会透传进来，重新打开伪造绕过的口子。
     * 部署要求：nginx 必须配置 proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for。
     */
    private String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (!isTrustedProxy(remote)) return remote;
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] parts = xff.split(",");
            for (int i = parts.length - 1; i >= 0; i--) {
                String candidate = parts[i].trim();
                if (candidate.isEmpty()) continue;
                if (!isTrustedProxy(candidate)) return candidate;
            }
        }
        return remote;
    }

    private boolean isTrustedProxy(String ip) {
        if (ip == null || ip.isBlank()) return false;
        try {
            InetAddress addr = InetAddress.getByName(ip);
            for (Cidr cidr : trustedProxies) {
                if (cidr.matches(addr)) return true;
            }
        }
        catch (Exception ignored) {
            // 非法 IP 字符串按不可信处理
        }
        return false;
    }

    private static List<Cidr> parseCidrs(String value) {
        List<Cidr> result = new ArrayList<>();
        if (value == null) return result;
        for (String entry : value.split(",")) {
            String item = entry.trim();
            if (item.isEmpty()) continue;
            try {
                int slash = item.indexOf('/');
                if (slash > 0) {
                    result.add(new Cidr(InetAddress.getByName(item.substring(0, slash)),
                            Integer.parseInt(item.substring(slash + 1)), false));
                }
                else {
                    result.add(new Cidr(InetAddress.getByName(item), 0, true));
                }
            }
            catch (Exception ignored) {
                // 配置错误条目跳过，不阻断启动
            }
        }
        return result;
    }

    /** CIDR 网段匹配（IPv4 前缀位比较；IPv6 用精确匹配即可覆盖 ::1 等本机场景）。 */
    record Cidr(InetAddress network, int prefixBits, boolean exact) {
        boolean matches(InetAddress addr) {
            byte[] net = network.getAddress();
            byte[] target = addr.getAddress();
            if (net.length != target.length) return false;
            if (exact) return java.util.Arrays.equals(net, target);
            int fullBytes = prefixBits / 8;
            int remBits = prefixBits % 8;
            if (fullBytes > net.length) return false;
            for (int i = 0; i < fullBytes; i++) {
                if (net[i] != target[i]) return false;
            }
            if (remBits > 0 && fullBytes < net.length) {
                int mask = 0xFF << (8 - remBits);
                if ((net[fullBytes] & mask) != (target[fullBytes] & mask)) return false;
            }
            return true;
        }
    }

    /**
     * 限流身份（[2026-10-27] 中危修复：token 不再无限增殖）：
     * - 鉴权/管理路径一律 "ip:"——session token 在验证前是攻击者可任意自报的字符串；
     * - 业务路径优先 "tok:"，但该 IP 窗口内出现过的不同 token 数超过
     *   {@link #MAX_TOKENS_PER_IP} 即塌缩回 "ip:"（增殖攻击失效），
     *   token 还须是 ≤128 的可见 ASCII（廉价格式闸）。
     */
    private String identityKey(HttpServletRequest request, boolean sensitive, String ip) {
        if (sensitive) return "ip:" + ip;
        String token = request.getHeader("X-Session-Token");
        if (token == null || token.isBlank() || token.length() > 128) return "ip:" + ip;
        boolean printableAscii = token.chars().allMatch(c -> c > 32 && c < 127);
        if (!printableAscii) return "ip:" + ip;
        TokenWindow window = tokensPerIp.computeIfAbsent(ip, k -> new TokenWindow());
        window.add(token);
        if (window.size() > MAX_TOKENS_PER_IP) return "ip:" + ip;
        return "tok:" + token;
    }

    /** 定时清理过期桶，避免长期运行内存膨胀。 */
    @Scheduled(fixedDelay = 300_000)
    public void cleanup() {
        long threshold = System.currentTimeMillis() - WINDOW_MS * 2;
        buckets.values().removeIf(b -> b.windowStart.get() < threshold);
        fails.values().removeIf(f -> f.windowStart.get() < threshold);
        tokensPerIp.values().removeIf(t -> t.windowStart.get() < threshold);
    }

    /** 固定时间窗计数器：窗口过期后重置；并发下允许极少量突发超阈，用于防滥用足够（边界取舍见类注释）。 */
    static final class Bucket {
        final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());
        final AtomicLong count = new AtomicLong(0);

        boolean tryAcquire(long limit) {
            long now = System.currentTimeMillis();
            long start = windowStart.get();
            if (now - start >= WINDOW_MS) {
                if (windowStart.compareAndSet(start, now)) count.set(0);
            }
            return count.incrementAndGet() <= limit;
        }
    }

    /** /auth/ 路径 401 计数桶：驱动渐进限速（只降配额、不封禁），窗口过期自动归零。 */
    static final class FailBucket {
        final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());
        final AtomicLong count = new AtomicLong(0);

        int record401() {
            long now = System.currentTimeMillis();
            long start = windowStart.get();
            if (now - start >= WINDOW_MS) {
                if (windowStart.compareAndSet(start, now)) count.set(0);
            }
            return (int) count.incrementAndGet();
        }

        /** 当前有效失败数：窗口已过期视为 0（自然恢复，无需解封）。 */
        int currentCount() {
            if (System.currentTimeMillis() - windowStart.get() >= WINDOW_MS) return 0;
            return (int) count.get();
        }
    }

    /** 单 IP 的 token 观察窗：记录 60s 内出现过的不同 token，驱动桶数封顶塌缩。 */
    static final class TokenWindow {
        final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());
        final Set<String> tokens = ConcurrentHashMap.newKeySet();

        void add(String token) {
            long now = System.currentTimeMillis();
            long start = windowStart.get();
            if (now - start >= WINDOW_MS) {
                if (windowStart.compareAndSet(start, now)) tokens.clear();
            }
            tokens.add(token);
        }

        int size() {
            if (System.currentTimeMillis() - windowStart.get() >= WINDOW_MS) return 0;
            return tokens.size();
        }
    }
}
