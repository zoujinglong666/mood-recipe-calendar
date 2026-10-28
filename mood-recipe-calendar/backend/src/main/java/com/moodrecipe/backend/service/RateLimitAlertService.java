package com.moodrecipe.backend.service;

import com.moodrecipe.backend.config.AppClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * [2026-10-27] 限流触发告警（WxPusher）：把"请求过于频繁"从用户侧投诉变成服务端主动上报。
 *
 * <p>触发条件：{@code RateLimitFilter} 每次返回 429 时调用 {@link #onRateLimited}——
 * 限流器是 429 的唯一权威产生地，在这里检测才能覆盖全部来源（前端 UI 检测会漏）。
 *
 * <p>防刷屏：10 分钟滑动窗口内只推一次；窗口内持续聚合计数，告警后窗口重新累计。
 *
 * <p>可靠性：聚合是无锁原子操作（429 热路径零阻塞）；推送在虚拟线程异步执行，
 * WxPusher 超时/失败只记日志，绝不影响限流决策本身；未配置 WxPusher 时
 * notifier 内部自然降级（既有的 send 语义）。
 *
 * <p>设计红线（AGENTS.md）：正常用户触发限流应≈0——这条告警出现即意味着有接口
 * 调用频率异常或限流配置误伤，内容里直接给出高发接口便于一秒定位。
 */
@Service
public class RateLimitAlertService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitAlertService.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");

    /** 告警静默窗口：窗口内只推一次，之后重新累计。 */
    private static final long WINDOW_MS = 10 * 60_000L;
    /** 通知里最多列出的高发接口数。 */
    private static final int MAX_URI_SAMPLES = 3;
    /** 单条 URI 采样截断，防止超长路径撑爆通知。 */
    private static final int MAX_URI_LENGTH = 80;

    private final WxPusherNotifier notifier;

    /** 窗口状态（单实例内存即可，与限流器同生命周期）。 */
    private final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong countInWindow = new AtomicLong();
    private final AtomicLong lastAlertAt = new AtomicLong(0);
    private final Map<String, AtomicLong> uriCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> kindCounts = new ConcurrentHashMap<>();
    private volatile String sampleIp = "";

    public RateLimitAlertService(WxPusherNotifier notifier) {
        this.notifier = notifier;
    }

    /**
     * 限流器每次 429 调用。热路径只做无锁聚合与一次 CAS 判断；
     * WxPusher 推送丢到虚拟线程异步执行，推送耗时不会拖慢被拒绝的请求。
     *
     * @param tokenPresent 请求是否带了会话 token——敏感路径（登录/管理端）按设计一律按 IP 计数，
     *                     即使带了 token；告警文案必须区分"真的没登录"和"登录了但按 IP 计"，
     *                     否则会像 2026-10-28 那样把管理员误导成"未登录异常"。
     */
    public void onRateLimited(String kind, String identity, boolean tokenPresent, String uri, String ip, long limit) {
        try {
            long now = System.currentTimeMillis();
            rollWindowIfNeeded(now);
            countInWindow.incrementAndGet();
            uriCounts.computeIfAbsent(shortUri(uri), k -> new AtomicLong()).incrementAndGet();
            String kindLabel = tokenPresent
                    ? "已登录会话（此路径按 IP 计数）"
                    : "IP（未登录/无有效会话）";
            kindCounts.computeIfAbsent(kindLabel, k -> new AtomicLong()).incrementAndGet();
            if (sampleIp.isBlank()) sampleIp = ip;

            long last = lastAlertAt.get();
            if (now - last < WINDOW_MS) return;
            if (!lastAlertAt.compareAndSet(last, now)) return;

            long total = countInWindow.getAndSet(0);
            Map<String, AtomicLong> uris = Map.copyOf(uriCounts);
            Map<String, AtomicLong> kinds = Map.copyOf(kindCounts);
            uriCounts.clear();
            kindCounts.clear();
            String ipSnapshot = sampleIp;
            sampleIp = "";

            String content = buildContent(total, uris, kinds, ipSnapshot, limit);
            Thread.startVirtualThread(() -> {
                try {
                    notifier.send(content);
                    log.warn("[限流告警] 已推送 WxPusher：窗口内 {} 次", total);
                }
                catch (RuntimeException ex) {
                    // 推送失败不影响任何业务，留日志即可（限流本身照常工作）
                    log.warn("[限流告警] WxPusher 推送失败: {}", ex.toString());
                }
            });
        }
        catch (RuntimeException ex) {
            // 告警链路的任何异常都不能反噬限流热路径
            log.warn("[限流告警] 聚合异常: {}", ex.toString());
        }
    }

    private void rollWindowIfNeeded(long now) {
        long start = windowStart.get();
        if (now - start < WINDOW_MS) return;
        if (!windowStart.compareAndSet(start, now)) return;
        countInWindow.set(0);
        uriCounts.clear();
        kindCounts.clear();
        sampleIp = "";
    }

    private String buildContent(long total, Map<String, AtomicLong> uris,
                                Map<String, AtomicLong> kinds, String ip, long limit) {
        var byCount = Map.Entry.<String, AtomicLong>comparingByValue(Comparator.comparingLong(AtomicLong::get));
        String topUris = uris.entrySet().stream()
                .sorted(byCount)
                .limit(MAX_URI_SAMPLES)
                .map(e -> "%s ×%d".formatted(e.getKey(), e.getValue().get()))
                .collect(Collectors.joining("；"));
        if (topUris.isBlank()) topUris = "（未采样到接口）";
        String kindsSummary = kinds.entrySet().stream()
                .sorted(byCount)
                .map(e -> "%s ×%d".formatted(e.getKey(), e.getValue().get()))
                .collect(Collectors.joining("，"));
        // [2026-10-28] 样本 IP 是内网/容器地址 = nginx 未配 X-Forwarded-For，
        // 全体用户共享这一个桶——这是"正常使用就触发限流"的头号环境性根因，直接写进告警。
        String ipHint = looksLikeInternalIp(ip)
                ? "⚠️ 样本 IP 是内网/容器地址：通常意味着 nginx 未配置 X-Forwarded-For，"
                        + "全体用户共享此桶。修复：nginx 加 proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;"
                : "";
        return String.format(
                "[锅仔限流告警] %s%n"
                        + "10 分钟内触发限流 %d 次（设计红线≈0）%n"
                        + "高发接口：%s%n"
                        + "请求身份：%s%n"
                        + "样本 IP：%s（桶上限 %d/分钟）%n%s"
                        + "请核对高发接口的调用频率与限流配置（AGENTS.md 限流红线）",
                TIME.format(AppClock.now()), total, topUris, kindsSummary, ip, limit, ipHint);
    }

    /** 内网/容器地址启发式：命中即在告警里给出 XFF 配置修复指引。 */
    private boolean looksLikeInternalIp(String ip) {
        if (ip == null) return false;
        return ip.startsWith("10.")
                || ip.startsWith("192.168.")
                || ip.startsWith("127.")
                || ip.startsWith("172.16.") || ip.startsWith("172.17.") || ip.startsWith("172.18.")
                || ip.startsWith("172.19.") || ip.startsWith("172.2") || ip.startsWith("172.30.")
                || ip.startsWith("172.31.")
                || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip);
    }

    /** 去掉查询串并截断，保留方法语义由调用方的 kind 区分；路径变量保留原样（可读性优先）。 */
    private String shortUri(String uri) {
        if (uri == null || uri.isBlank()) return "unknown";
        String item = uri.split("\\?")[0];
        return item.length() <= MAX_URI_LENGTH ? item : item.substring(0, MAX_URI_LENGTH) + "…";
    }

    /** 供测试/管理端查看窗口内计数（当前未接入查询接口，预留）。 */
    long countInWindowForTest() {
        return countInWindow.get();
    }
}
