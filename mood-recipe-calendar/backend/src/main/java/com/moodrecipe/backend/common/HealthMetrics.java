package com.moodrecipe.backend.common;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * [2026-10-26] 跨切面健康指标（架构评审 P2-9：发现「没报错但一直在降级」的问题）。
 *
 * <p>只存当日计数（进程内存）：后端 500 次数、前端错误上报次数。
 * 每天 09:00 由 {@code DailyHealthReportService} 读取并清零，随日报推送。
 * 重启清零对「当日计数」可接受（500 同时有 WxPusher 实时告警兜底）。
 */
@Component
public class HealthMetrics {

    private final AtomicLong serverErrors = new AtomicLong();
    private final AtomicLong clientErrors = new AtomicLong();
    /** [2026-10-27] 限流拦截次数：设计红线是正常用户恒为 0，非 0 即误伤信号。 */
    private final AtomicLong rateLimited = new AtomicLong();

    public void incrementServerErrors() {
        serverErrors.incrementAndGet();
    }

    public void incrementClientErrors() {
        clientErrors.incrementAndGet();
    }

    public void incrementRateLimited() {
        rateLimited.incrementAndGet();
    }

    public long snapshotAndResetServerErrors() {
        return serverErrors.getAndSet(0);
    }

    public long snapshotAndResetClientErrors() {
        return clientErrors.getAndSet(0);
    }

    public long snapshotAndResetRateLimited() {
        return rateLimited.getAndSet(0);
    }
}
