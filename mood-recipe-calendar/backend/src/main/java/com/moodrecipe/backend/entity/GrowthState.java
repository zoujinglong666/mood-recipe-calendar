package com.moodrecipe.backend.entity;

import com.moodrecipe.backend.config.AppClock;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 锅仔养成状态：一个用户一行（openid 唯一），points 是累计成长值，level 为缓存值。 */
@Data
@Entity
@Table(name = "growth_states", uniqueConstraints =
        @UniqueConstraint(name = "uk_growth_openid", columnNames = "openid"))
public class GrowthState {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String openid;

    /** 累计成长值（与 growth_events 流水求和保持一致，由 GrowthService 维护）。 */
    @Column(nullable = false)
    private Integer points = 0;

    /** 当前等级缓存（1 起），由 GrowthService 按阈值重算。 */
    @Column(nullable = false)
    private Integer level = 1;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() { updatedAt = AppClock.now(); }
}
