package com.moodrecipe.backend.entity;

import com.moodrecipe.backend.config.AppClock;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.moodrecipe.backend.model.RecommendationInsight;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "recipes")
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String description;
    private String image;

    @Column(columnDefinition = "TEXT")
    private String ingredients;

    @Column(columnDefinition = "TEXT")
    private String steps;

    @Column(name = "cooking_time")
    private Integer cookingTime;

    private String difficulty;

    @Column(name = "mood_tags")
    private String moodTags;

    /** 上次 LLM 精准心情分类时间；NULL=尚未分类（定时任务优先补齐），最旧的会被周期性复核。 */
    @Column(name = "mood_classified_at")
    private LocalDateTime moodClassifiedAt;

    /** 口味：清淡/辣/甜/酸/鲜等，逗号分隔。 */
    private String taste;

    /** 餐别：lunch/dinner/all。 */
    @Column(name = "meal_type")
    private String mealType;

    /** 标签：快手菜/汤品/主食等，逗号分隔。 */
    private String tags;

    /** 调料：盐/酱油/醋等，跟食材分开，逗号分隔。 */
    private String seasonings;

    private String season;

    /** 仅用于本次响应，不进入菜谱表。 */
    @Transient
    private String recommendationReason;

    /** 推荐依据：本次推荐检索到的《中国居民膳食指南》要点（JSON 串）。仅用于本次响应，不持久化。 */
    @Transient
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String guidelineBasis;

    /** AI 临时推荐的反馈标识，不进入公共菜谱表。 */
    @Transient
    private String exposureId;

    /** 本次实际使用的记忆解释，不进入菜谱表。 */
    @Transient
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<RecommendationInsight> recommendationInsights = List.of();

    /** 菜谱来源：AI=锅仔智能体生成并落库，LOCAL=初始化种子数据。 */
    @Column(name = "source", length = 16)
    private String source;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = AppClock.now();
    }
}
