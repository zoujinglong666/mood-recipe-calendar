package com.moodrecipe.backend.agent;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * LLM 调用计数器（进程内存）：按「档位」统计调用次数/失败/时延，
 * 是 LLM 成本归因的最小实现——次数 × 各档单价（运营侧配置）即为成本估算。
 *
 * 只进内存、日报读取后清零：热路径零额外 DB 写入；进程重启丢当日已计数部分，
 * 精确逐次归因（token 级）留待供应商 usage 字段接入后再升级。
 */
@Component
public class LlmUsageCounter {

    /** free=免费层（Agnes）；paid_pro/paid_flash=会员付费层（DeepSeek Pro/Flash）。 */
    public static final String FREE = "free";
    public static final String PAID_PRO = "paid_pro";
    public static final String PAID_FLASH = "paid_flash";

    public record Bucket(long calls, long failures, long totalAttempts, long totalLatencyMs) { }

    private static final class Counter {
        final AtomicLong calls = new AtomicLong();
        final AtomicLong failures = new AtomicLong();
        final AtomicLong attempts = new AtomicLong();
        final AtomicLong latencyMs = new AtomicLong();
    }

    private final Map<String, Counter> buckets = new ConcurrentHashMap<>();

    /** 热路径：complete 返回后同步调用一次，纯内存操作。 */
    public void record(String tier, LlmResult result) {
        if (tier == null || result == null) return;
        Counter counter = buckets.computeIfAbsent(tier, k -> new Counter());
        counter.calls.incrementAndGet();
        counter.attempts.addAndGet(Math.max(1, result.attempts()));
        counter.latencyMs.addAndGet(Math.max(0, result.latencyMs()));
        if (result.failure() != null) counter.failures.incrementAndGet();
    }

    /** 读取快照（日报/管理端），silent=true 时读取后清零重新计数。 */
    public Map<String, Bucket> snapshotAndReset(boolean reset) {
        Map<String, Bucket> out = new ConcurrentHashMap<>();
        for (Map.Entry<String, Counter> entry : buckets.entrySet()) {
            Counter counter = entry.getValue();
            Bucket bucket = new Bucket(counter.calls.get(), counter.failures.get(),
                    counter.attempts.get(), counter.latencyMs.get());
            if (reset) {
                counter.calls.set(0);
                counter.failures.set(0);
                counter.attempts.set(0);
                counter.latencyMs.set(0);
            }
            out.put(entry.getKey(), bucket);
        }
        return out;
    }
}
