package com.moodrecipe.backend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

/**
 * 每日活跃用户去重表。
 * 一天一个 openid 最多一行，靠唯一约束 (active_date, openid) + INSERT IGNORE 去重，
 * 避免把每次请求都写进 operational_events 导致表膨胀。
 */
@Data
@Entity
@Table(name = "daily_active_users",
        uniqueConstraints = @UniqueConstraint(name = "uk_dau_date_openid", columnNames = {"active_date", "openid"}))
public class DailyActiveUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "active_date", nullable = false)
    private LocalDate activeDate;

    @Column(nullable = false, length = 64)
    private String openid;
}
