package com.moodrecipe.backend.entity;

import com.moodrecipe.backend.config.AppClock;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户主动确认的长期口味记忆。 */
@Data
@Entity
@Table(name = "user_food_preferences")
public class UserFoodPreference {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @Column(unique = true, nullable = false)
    private String openid;

    @Column(name = "favorite_tags", length = 500)
    private String favoriteTags = "";

    @Column(name = "favorite_cuisines", length = 500)
    private String favoriteCuisines = "";

    @Column(name = "favorite_dishes", length = 500)
    private String favoriteDishes = "";

    @Column(name = "avoid_ingredients", length = 500)
    private String avoidIngredients = "";

    @Column(length = 500)
    private String allergens = "";

    @JsonIgnore
    @Column(name = "normalized_blocked_terms", columnDefinition = "TEXT")
    private String normalizedBlockedTerms = "[]";

    @Column(name = "eat_scallion")
    private Boolean eatScallion;

    @Column(name = "eat_cilantro")
    private Boolean eatCilantro;

    @Column(name = "spice_level", length = 16)
    private String spiceLevel = "NORMAL";

    /** 籍贯/家乡口味：自由文本（如「四川成都」），让推荐懂家乡味。 */
    @Column(length = 100)
    private String hometown = "";

    /** 做菜水平：NOVICE=小白 / HOME=家常 / PRO=大厨，决定步骤颗粒度与菜谱难度。 */
    @Column(name = "cooking_skill", length = 16)
    private String cookingSkill = "HOME";

    @Column(name = "health_goal", length = 16)
    private String healthGoal = "BALANCED";

    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist @PreUpdate
    void touch() { updatedAt = AppClock.now(); }
}
