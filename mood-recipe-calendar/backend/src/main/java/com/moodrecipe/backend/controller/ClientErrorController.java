package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.common.HealthMetrics;
import com.moodrecipe.backend.service.WxPusherNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [2026-10-26] 前端错误上报接口（架构评审 P1-4）。
 *
 * <p>此前前端白屏 / JS 报错 / 接口失败用户只会流失，后端完全无感知。
 * 小程序端在 App.vue 的 onError / uni.onUnhandledRejection 里调用本接口，
 * 服务端去重后推 WxPusher 告警（复用既有 500 告警通道）。
 *
 * <p>安全考量：接口免鉴权（错误可能在登录前发生），但 payload 全字段截断、
 * 不回显、不入库；同指纹（页面+消息）10 分钟内只告警一次，防刷屏；
 * IP 维度已有 RateLimitFilter（general 120/min）兜底。
 */
@RestController
@RequestMapping("/api/client-errors")
public class ClientErrorController {

    private static final Logger log = LoggerFactory.getLogger(ClientErrorController.class);

    /** 同指纹告警的去重窗口。 */
    private static final long DEDUPE_WINDOW_MS = 10 * 60_000L;
    /** 各字段截断上限（防超长 payload / Data too long）。 */
    private static final int MAX_MESSAGE = 500;
    private static final int MAX_STACK = 2000;
    private static final int MAX_PAGE = 200;

    private final WxPusherNotifier notifier;
    private final HealthMetrics metrics;
    /** 指纹 -> 最近一次告警时间戳。超量时清理过期项，防止内存无界。 */
    private final Map<String, Long> recentFingerprints = new ConcurrentHashMap<>();

    public ClientErrorController(WxPusherNotifier notifier, HealthMetrics metrics) {
        this.notifier = notifier;
        this.metrics = metrics;
    }

    public record ClientErrorPayload(String kind, String message, String stack, String page, String version) { }

    @PostMapping
    public ApiResponse<Void> report(@RequestBody ClientErrorPayload payload) {
        metrics.incrementClientErrors();
        String kind = truncate(payload == null || payload.kind() == null ? "unknown" : payload.kind(), 32);
        String page = truncate(payload == null ? null : payload.page(), MAX_PAGE);
        String message = truncate(payload == null ? null : payload.message(), MAX_MESSAGE);
        String stack = truncate(payload == null ? null : payload.stack(), MAX_STACK);

        String fingerprint = page + "|" + message;
        long now = System.currentTimeMillis();
        Long last = recentFingerprints.get(fingerprint);
        if (last != null && now - last < DEDUPE_WINDOW_MS) {
            log.info("[client-error·去重] {} {} {}", kind, page, message);
            return ApiResponse.ok();
        }
        recentFingerprints.put(fingerprint, now);
        if (recentFingerprints.size() > 200) {
            recentFingerprints.entrySet().removeIf(e -> now - e.getValue() > DEDUPE_WINDOW_MS);
        }

        log.warn("[client-error] {} page={} message={}", kind, page, message);
        notifier.send(String.format("[锅仔前端错误·%s] %s%n%s%n%s", kind,
                page == null ? "-" : page, message, stack == null ? "" : stack));
        return ApiResponse.ok();
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
