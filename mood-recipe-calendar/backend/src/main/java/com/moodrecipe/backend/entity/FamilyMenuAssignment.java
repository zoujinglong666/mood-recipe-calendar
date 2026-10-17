package com.moodrecipe.backend.entity;

import com.moodrecipe.backend.config.AppClock;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家庭菜单分工：某份周计划的某一天由谁掌勺。
 * 挂在 planId 上（换新计划即新分工），(plan_id, day_index) 唯一；cook_openid 为空 = 未定。
 */
@Data
@Entity
@Table(name = "family_menu_assignments", uniqueConstraints =
        @UniqueConstraint(name = "uk_family_menu_plan_day", columnNames = {"plan_id", "day_index"}))
public class FamilyMenuAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    /** 计划主人的 openid（计划的归属者，成员都能看到这份计划）。 */
    @Column(name = "owner_openid", nullable = false, length = 64)
    private String ownerOpenid;

    @Column(name = "day_index", nullable = false)
    private Integer dayIndex;

    /** 掌勺人 openid；null = 未定。 */
    @Column(name = "cook_openid", length = 64)
    private String cookOpenid;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() { updatedAt = AppClock.now(); }
}
