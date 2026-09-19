package com.moodrecipe.backend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "cooking_learning_events")
public class CookingLearningEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 64) private String openid;
    @Column(name = "recipe_id") private Long recipeId;
    @Column(name = "step_index") private Integer stepIndex;
    @Column(name = "step_type", length = 48) private String stepType;
    @Column(name = "event_type", nullable = false, length = 24) private String eventType;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @PrePersist void create() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
