package com.moodrecipe.backend.entity;

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

    @Column(name = "avoid_ingredients", length = 500)
    private String avoidIngredients = "";

    @Column(length = 500)
    private String allergens = "";

    @Column(name = "eat_scallion")
    private Boolean eatScallion;

    @Column(name = "eat_cilantro")
    private Boolean eatCilantro;

    @Column(name = "spice_level", length = 16)
    private String spiceLevel = "NORMAL";

    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist @PreUpdate
    void touch() { updatedAt = LocalDateTime.now(); }
}
