package com.moodrecipe.backend.entity;

import com.moodrecipe.backend.config.AppClock;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * [2026-10-26] LLM 用量每日落库（架构评审 P2-2）。
 *
 * <p>此前 {@code LlmUsageCounter} 只在内存计数、重启清零，成本/会员对账没有历史。
 * 现在每天 23:57 由 {@code LlmUsageDailyService} 把内存计数快照并清零，
 * 按日 + 档位（free / paid_pro / paid_flash）落一张表，形成可持续对账的用量史。
 * token 级归因待供应商 usage 字段接入后再升级。
 */
@Data
@Entity
@Table(name = "llm_usage_daily",
        uniqueConstraints = @UniqueConstraint(name = "uk_llm_usage_daily_date_tier",
                columnNames = {"usage_date", "tier"}))
public class LlmUsageDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 业务日期（Asia/Shanghai）。 */
    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    /** 档位：free / paid_pro / paid_flash（与 LlmUsageCounter 常量一致）。 */
    @Column(nullable = false, length = 32)
    private String tier;

    /** complete() 调用次数。 */
    private long calls;

    /** 失败次数（LlmResult.failure != null）。 */
    private long failures;

    /** 总尝试次数（含重试）。 */
    private long totalAttempts;

    /** 总时延 ms，除以 calls 得均值。 */
    private long totalLatencyMs;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = AppClock.now();
    }
}
