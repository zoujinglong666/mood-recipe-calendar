package com.moodrecipe.backend.entity;

import com.moodrecipe.backend.config.AppClock;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 锅仔养成的成长值流水：一次行为一条（记录一餐 / 消耗临期食材 / 签到 / 解锁成就 / 生成周菜单）。 */
@Data
@Entity
@Table(name = "growth_events", indexes =
        @Index(name = "idx_growth_event_openid_time", columnList = "openid, created_at"))
public class GrowthEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String openid;

    /** 事件类型：RECORD_MEAL / CHECKIN / WEEKLY_PLAN / EXPIRING_CONSUMED / ACHIEVEMENT。 */
    @Column(nullable = false, length = 32)
    private String type;

    @Column(nullable = false)
    private Integer points;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { createdAt = AppClock.now(); }
}
