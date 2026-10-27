package com.moodrecipe.backend.config.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * [2026-10-27] ERROR 日志 → WxPusher 告警 Appender（程序化挂载到 root logger，见
 * {@link ErrorLogAlertBridge}）——让"没有专属告警通道的报错"（定时任务里的意外异常、
 * DB 错误、未捕获降级等）不再只是躺在日志里，而是主动推给管理员。
 *
 * <p>防刷屏三件套（与 RateLimitAlertService 同款思路）：
 * <ul>
 *   <li>10 分钟滑动窗口：窗口内只推第一条，其后只计数（"已抑制"）；</li>
 *   <li>指纹聚合：logger+消息 相同的错误单独计数，推送里说明"另有 N 条完全相同"；</li>
 *   <li>排除清单：已有专属告警通道的 logger 不重复推（500 告警、限流告警等）。</li>
 * </ul>
 *
 * <p>可靠性约束：append() 绝不抛异常、绝不再打日志（重入保护 {@link #IN_ALERT}），
 * 推送由桥接层异步执行——日志系统必须永远先于告警系统活下来。
 */
public class WxPusherErrorAppender extends AppenderBase<ILoggingEvent> {

    /** 告警静默窗口：窗口内只推一次，其余计数聚合。 */
    static final long WINDOW_MS = 10 * 60_000L;
    private static final int MAX_MESSAGE = 600;
    private static final int MAX_STACK_LINE = 200;
    /** 已有专属 WxPusher 通道的 logger：不重复推送，避免一个故障两条告警。 */
    static final Set<String> EXCLUDED_LOGGER_PREFIXES = Set.of(
            "com.moodrecipe.backend.common.GlobalExceptionHandler",
            "com.moodrecipe.backend.config.RateLimitFilter",
            "com.moodrecipe.backend.service.RateLimitAlertService",
            "com.moodrecipe.backend.service.WxPusherNotifier");

    /** 单条告警的数据快照（格式化由桥接层负责，appender 只做检测与聚合）。 */
    public record ErrorAlert(String time, String loggerName, String thread,
                             String message, String stackTop, long suppressed, long sameDuplicate) { }

    /** 桥接层注入的发送端（Spring Bean 的 WxPusherNotifier 包装）。logback 先于 Spring 装配，故用静态槽位。 */
    private static volatile java.util.function.Consumer<ErrorAlert> sink;
    /** 重入保护：发送链路若再触发 ERROR 日志，直接跳过，杜绝递归。 */
    private static final ThreadLocal<Boolean> IN_ALERT = ThreadLocal.withInitial(() -> false);

    private static final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());
    private static final AtomicLong countInWindow = new AtomicLong();
    private static final AtomicLong lastAlertAt = new AtomicLong(0);
    private static final Map<String, AtomicLong> fingerprints = new ConcurrentHashMap<>();

    public static void setSink(java.util.function.Consumer<ErrorAlert> consumer) {
        sink = consumer;
    }

    public WxPusherErrorAppender() {
        setName("WxPusherErrorAppender");
    }

    @Override
    protected void append(ILoggingEvent event) {
        try {
            if (IN_ALERT.get()) return;
            if (sink == null) return;
            if (event.getLevel() == null || !event.getLevel().isGreaterOrEqual(Level.ERROR)) return;
            String loggerName = event.getLoggerName() == null ? "" : event.getLoggerName();
            for (String prefix : EXCLUDED_LOGGER_PREFIXES) {
                if (loggerName.startsWith(prefix)) return;
            }

            long now = System.currentTimeMillis();
            rollWindowIfNeeded(now);
            String message = truncate(event.getFormattedMessage(), MAX_MESSAGE);
            String fingerprint = loggerName + "|" + message;
            fingerprints.computeIfAbsent(fingerprint, k -> new AtomicLong()).incrementAndGet();
            long total = countInWindow.incrementAndGet();

            long last = lastAlertAt.get();
            if (now - last < WINDOW_MS) return;
            if (!lastAlertAt.compareAndSet(last, now)) return;

            long suppressed = total - 1;
            long sameDuplicate = fingerprints.getOrDefault(fingerprint, new AtomicLong(0)).get() - 1;
            Map<String, AtomicLong> snapshot = Map.copyOf(fingerprints);
            fingerprints.clear();
            countInWindow.set(0);

            String stackTop = "";
            if (event.getThrowableProxy() != null && event.getThrowableProxy().getStackTraceElementProxyArray() != null
                    && event.getThrowableProxy().getStackTraceElementProxyArray().length > 0) {
                stackTop = truncate(event.getThrowableProxy().getStackTraceElementProxyArray()[0].toString(), MAX_STACK_LINE);
            }
            ErrorAlert alert = new ErrorAlert(
                    java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
                            .format(java.time.LocalDateTime.now()),
                    loggerName, event.getThreadName(), message, stackTop, suppressed, sameDuplicate);

            java.util.function.Consumer<ErrorAlert> current = sink;
            Thread.startVirtualThread(() -> {
                IN_ALERT.set(true);
                try {
                    current.accept(alert);
                }
                catch (RuntimeException ignored) {
                    // 推送失败只可能以非 ERROR 日志暴露；绝不能再进入本 appender 造成递归
                }
                finally {
                    IN_ALERT.set(false);
                }
            });
        }
        catch (RuntimeException ignored) {
            // appender 内任何异常都吞掉：日志系统故障不能拖垮业务线程
        }
    }

    private void rollWindowIfNeeded(long now) {
        long start = windowStart.get();
        if (now - start < WINDOW_MS) return;
        if (!windowStart.compareAndSet(start, now)) return;
        countInWindow.set(0);
        fingerprints.clear();
    }

    private static String truncate(String value, int max) {
        if (value == null) return "（无消息）";
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
