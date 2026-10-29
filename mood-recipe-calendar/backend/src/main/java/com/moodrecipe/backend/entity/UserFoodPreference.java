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

    // ==== [2026-10-28] EER 营养目标输入（全部可空；未填时目标计算返回 NEEDS_INPUT） ====
    /** 生理性别：MALE/FEMALE，仅决定 EER 方程系数组。 */
    @Column(length = 8)
    private String gender;

    /** 年龄（岁），EER 支持 19-64。 */
    private Integer age;

    /** 身高 cm。 */
    @Column(name = "height_cm", precision = 5, scale = 1)
    private java.math.BigDecimal heightCm;

    /** 体重 kg。 */
    @Column(name = "weight_kg", precision = 5, scale = 1)
    private java.math.BigDecimal weightKg;

    /** 活动等级：INACTIVE/LOW_ACTIVE/ACTIVE/VERY_ACTIVE。 */
    @Column(name = "activity_level", length = 16)
    private String activityLevel;

    /** 是否规律力量训练：决定蛋白质范围系数组（1.4/1.6/2.0 vs 1.2/1.4/1.6 g/kg）。 */
    @Column(name = "resistance_training")
    private Boolean resistanceTraining;

    /** 进食节奏：REGULAR/NO_BREAKFAST/LATE_NIGHT/INTERMITTENT。 */
    @Column(name = "meal_timing", length = 32)
    private String mealTiming;

    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist @PreUpdate
    void touch() { updatedAt = AppClock.now(); }
}
