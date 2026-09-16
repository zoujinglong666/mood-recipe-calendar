package com.moodrecipe.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "weekly_meal_plans")
public class WeeklyMealPlan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @JsonIgnore @Column(nullable = false) private String openid;
    @Column(name = "plan_json", columnDefinition = "TEXT", nullable = false) private String planJson;
    @Column(name = "shopping_json", columnDefinition = "TEXT", nullable = false) private String shoppingJson;
    /** 智能体审计信息：质量分、降级原因、用到的记忆、traceId，用于事后复盘而非展示。 */
    @JsonIgnore @Column(name = "agent_json", columnDefinition = "TEXT") private String agentJson;
    @Column(nullable = false) private boolean favorite;
    @Column(name = "created_at") private LocalDateTime createdAt;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @PrePersist @PreUpdate void touch() { updatedAt = LocalDateTime.now(); if (createdAt == null) createdAt = updatedAt; }
}
