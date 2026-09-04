package com.moodrecipe.backend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "recipe_interactions", indexes = {
        @Index(name = "idx_recipe_interaction_user_recipe", columnList = "openid,recipe_id"),
        @Index(name = "idx_recipe_interaction_user_time", columnList = "openid,created_at")
})
public class RecipeInteraction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String openid;

    @Column(name = "recipe_id", nullable = false)
    private Long recipeId;

    /** LIKE、DISLIKE、MADE 或 SHOWN。仅存行为和时间，不存菜谱正文。 */
    @Column(nullable = false, length = 16)
    private String action;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() { createdAt = LocalDateTime.now(); }
}
