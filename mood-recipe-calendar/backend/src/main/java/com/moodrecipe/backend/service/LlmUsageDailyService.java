package com.moodrecipe.backend.service;

import com.moodrecipe.backend.agent.LlmUsageCounter;
import com.moodrecipe.backend.config.AppClock;
import com.moodrecipe.backend.entity.LlmUsageDaily;
import com.moodrecipe.backend.repository.LlmUsageDailyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * [2026-10-26] LLM 用量每日落库（架构评审 P2-2）。
 *
 * <p>每天 23:57 把 {@link LlmUsageCounter} 内存快照并清零，按日 + 档位 upsert 进
 * {@code llm_usage_daily}，形成可对账的用量史。读快照与写库之间极小概率漏掉
 * 正在发生的调用（被清零前 record），对账精度足够；重启丢失当日部分计数的旧问题
 * 从此只影响「当天」，历史不再丢。
 */
@Service
public class LlmUsageDailyService {

    private static final Logger log = LoggerFactory.getLogger(LlmUsageDailyService.class);

    private final LlmUsageCounter counter;
    private final LlmUsageDailyRepository repository;

    public LlmUsageDailyService(LlmUsageCounter counter, LlmUsageDailyRepository repository) {
        this.counter = counter;
        this.repository = repository;
    }

    /** 每日 23:57 落库（可配置）。读后清零，热路径仍是纯内存。 */
    @Scheduled(cron = "${app.llm-usage.flush-cron:0 57 23 * * ?}")
    @Transactional
    public void flushToday() {
        LocalDate today = AppClock.today();
        Map<String, LlmUsageCounter.Bucket> snapshot = counter.snapshotAndReset(true);
        snapshot.forEach((tier, bucket) -> upsert(today, tier, bucket));
        if (!snapshot.isEmpty()) {
            log.info("LLM 用量已落库 date={} tiers={}", today, snapshot.keySet());
        }
    }

    private void upsert(LocalDate date, String tier, LlmUsageCounter.Bucket bucket) {
        LlmUsageDaily row = repository.findByUsageDateAndTier(date, tier).orElseGet(() -> {
            LlmUsageDaily fresh = new LlmUsageDaily();
            fresh.setUsageDate(date);
            fresh.setTier(tier);
            return fresh;
        });
        row.setCalls(row.getCalls() + bucket.calls());
        row.setFailures(row.getFailures() + bucket.failures());
        row.setTotalAttempts(row.getTotalAttempts() + bucket.totalAttempts());
        row.setTotalLatencyMs(row.getTotalLatencyMs() + bucket.totalLatencyMs());
        repository.save(row);
    }

    /** 查某天用量（健康日报/管理端）。 */
    @Transactional(readOnly = true)
    public List<LlmUsageDaily> usageOf(LocalDate date) {
        return repository.findByUsageDate(date);
    }
}
