package com.moodrecipe.backend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能体的通用事实记忆。
 *
 * 一条事实 = 谁（openid）+ 什么（memory_key）+ 值（memory_value）+ 有多确定（confidence）
 * + 从哪来（source）+ 凭什么（evidence）+ 是否还有效（status / expires_at）。
 *
 * 置信度会随时间衰减，命中一次会增强；过期或衰减到阈值以下自动归档，
 * 这就是记忆的"更新与遗忘策略"。
 */
@Data
@Entity
@Table(name = "agent_memory_facts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"openid", "memory_key"}))
public class AgentMemoryFact {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    /** 低于该置信度的事实不再进入提示词。 */
    public static final double MIN_CONFIDENCE = 0.25;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String openid;

    @Column(name = "memory_key", nullable = false, length = 48)
    private String memoryKey;

    @Column(name = "memory_value", nullable = false, length = 200)
    private String memoryValue;

    @Column(nullable = false, length = 16)
    private String source = "CHAT";

    /** 0~1，1 表示用户明确且反复确认过。 */
    @Column(nullable = false)
    private Double confidence = 0.6;

    /** 这条记忆的依据，例如用户原话或行为统计，用于解释"为什么记住"。 */
    @Column(length = 500)
    private String evidence;

    @Column(nullable = false, length = 16)
    private String status = STATUS_ACTIVE;

    /** 被智能体成功引用过的次数，越高越可信也越优先。 */
    @Column(name = "hit_count", nullable = false)
    private Integer hitCount = 0;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    /** 有时效的事实（如"这周有客人"）到期后自动归档。 */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
