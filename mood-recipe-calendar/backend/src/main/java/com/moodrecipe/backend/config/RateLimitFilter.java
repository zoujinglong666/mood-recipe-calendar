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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 轻量限流（单实例内存实现，无需 Redis）：缓解扫描、爆破与突发流量。
 * - 已登录请求按会话（X-Session-Token，≈按用户）限流，避免微信/反代共享出口 IP 把所有用户挤进同一桶；
 * - 未登录/鉴权请求按 IP 限流。
 * - 全局：每 IP 每分钟最多 general-per-minute 次
 * - 鉴权/管理接口：每 IP 每分钟最多 auth-per-minute 次（防会话 token 爆破）
 * - 登录/鉴权失败（401）连续超阈值：临时封禁该 IP（ban-minutes 分钟），压爆破与扫描噪声
 * 优先取 X-Forwarded-For / X-Real-IP 作为真实客户端 IP（适配 nginx 反代），都没有则取 remoteAddr。
 * 放行健康检查、静态资源、微信回调等低频/可信路径；超限返回 429。
 */
@Component
@Order(-100)
@EnableScheduling
public class RateLimitFilter extends OncePerRequestFilter {

    private final long generalLimit;
    private final long authLimit;
    private static final long WINDOW_MS = 60_000;
    private static final long BAN_MS = 10 * 60_000;        // 连续 401 超阈值后封禁 10 分钟
    private static final int BAN_THRESHOLD = 15;            // 1 分钟内 15 次 401 即触发封禁
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Map<String, Long> bannedUntil = new ConcurrentHashMap<>();
    private final Map<String, FailBucket> fails = new ConcurrentHashMap<>();

    public RateLimitFilter(@Value("${security.ratelimit.general-per-minute:120}") long generalLimit,
                           @Value("${security.ratelimit.auth-per-minute:20}") long authLimit) {
        this.generalLimit = generalLimit;
        this.authLimit = authLimit;
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
        Long until = bannedUntil.get(ip);
        if (until != null && until > System.currentTimeMillis()) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Retry-After", "600");
            response.getWriter().write("{\"code\":429,\"message\":\"尝试过于频繁，已临时限制，请稍后再试\"}");
            return;
        }
        boolean sensitive = uri.contains("/auth/") || uri.startsWith("/api/admin/");
        long limit = sensitive ? authLimit : generalLimit;
        // 限流身份：已登录请求带 X-Session-Token，按会话（≈按用户）限流；
        // 微信/反代场景下所有用户共享同一出口 IP，若仅按 IP 限流会把正常用户也频遭 429。
        // 未携带 token 的公开/鉴权请求退回按 IP 限流（保留防爆破能力）。
        String identity = identityKey(request);
        Bucket bucket = buckets.computeIfAbsent(identity + (sensitive ? "|auth" : "|gen"), k -> new Bucket());
        if (!bucket.tryAcquire(limit)) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Retry-After", "60");
            response.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\"}");
            return;
        }
        chain.doFilter(request, response);
        // 登录/鉴权失败（401）累计封禁，压爆破与扫描噪声
        if (response.getStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
            FailBucket fb = fails.computeIfAbsent(ip, k -> new FailBucket());
            if (fb.record()) {
                bannedUntil.put(ip, System.currentTimeMillis() + BAN_MS);
                fails.remove(ip);
            }
        }
    }

    private boolean shouldSkip(String uri) {
        return uri.startsWith("/api/health")
                || uri.startsWith("/uploads/")
                || uri.startsWith("/api/wechat/virtual-payment/callback")
                || uri.startsWith("/api/breakfast-pool/seed");
    }

    /**
     * 真实客户端 IP：X-Forwarded-For 是追加模式，客户端可自带伪造 IP 排在最前，
     * 必须取最右侧（由本层可信代理 nginx 追加的 $remote_addr），否则限流/封禁可被 XFF 轮换整体绕过。
     */
    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] parts = xff.split(",");
            return parts[parts.length - 1].trim();
        }
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank()) return xri.trim();
        return request.getRemoteAddr();
    }

    /**
     * 限流身份：已登录请求携带 X-Session-Token 时按会话（≈按用户）限流；
     * 微信小程序/云平台反代下所有用户共享同一出口 IP，若仅按 IP 限流会把正常用户也频遭 429。
     * 未携带 token 的公开/鉴权请求退回按 IP 限流（保留防爆破能力）。
     */
    private String identityKey(HttpServletRequest request) {
        String token = request.getHeader("X-Session-Token");
        if (token != null && !token.isBlank()) return "tok:" + token;
        return "ip:" + clientIp(request);
    }

    /** 定时清理过期桶与封禁记录，避免长期运行内存膨胀。 */
    @Scheduled(fixedDelay = 300_000)
    public void cleanup() {
        long now = System.currentTimeMillis();
        long threshold = now - WINDOW_MS * 2;
        buckets.values().removeIf(b -> b.windowStart.get() < threshold);
        bannedUntil.values().removeIf(exp -> exp < now);
        fails.values().removeIf(f -> f.windowStart.get() < threshold);
    }

    /** 固定时间窗计数器：窗口过期后重置；并发下允许极少量突发超阈，用于防滥用足够。 */
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

    /** 连续 401 计数桶：1 分钟窗口内累计达阈值即返回 true（触发封禁）并重置。 */
    static final class FailBucket {
        final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());
        final AtomicLong count = new AtomicLong(0);

        boolean record() {
            long now = System.currentTimeMillis();
            long start = windowStart.get();
            if (now - start >= WINDOW_MS) {
                if (windowStart.compareAndSet(start, now)) count.set(0);
            }
            return count.incrementAndGet() >= BAN_THRESHOLD;
        }
    }
}
