package com.moodrecipe.backend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 周计划的执行闭环：这道菜到底做了吗、剩了吗、太难了吗。
 * 没有这张表，周菜单就只有"生成"没有"结果"，智能体无法从真实结果里学习。
 */
@Data
@Entity
@Table(name = "plan_dish_outcomes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"openid", "plan_id", "day_index", "dish_index"}))
public class PlanDishOutcome {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String openid;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "day_index", nullable = false)
    private int dayIndex;

    @Column(name = "dish_index", nullable = false)
    private int dishIndex;

    @Column(name = "dish_name", nullable = false, length = 120)
    private String dishName;

    /** null = 还没反馈；true/false = 做了 / 没做。 */
    @Column(name = "cooked")
    private Boolean cooked;

    @Column(name = "leftover")
    private Boolean leftover;

    @Column(name = "too_hard")
    private Boolean tooHard;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
