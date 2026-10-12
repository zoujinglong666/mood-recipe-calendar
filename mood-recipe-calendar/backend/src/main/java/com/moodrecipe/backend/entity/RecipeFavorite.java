package com.moodrecipe.backend.entity;

import com.moodrecipe.backend.config.AppClock;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户想吃清单：对一道菜的长期收藏（区别于 recipe_interactions 的一次性行为事件）。 */
@Data
@Entity
@Table(name = "recipe_favorites", indexes = {
        @Index(name = "idx_recipe_favorite_user_recipe", columnList = "openid,recipe_id"),
        @Index(name = "idx_recipe_favorite_user_time", columnList = "openid,created_at")
})
public class RecipeFavorite {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String openid;

    @Column(name = "recipe_id", nullable = false)
    private Long recipeId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() { createdAt = AppClock.now(); }
}
