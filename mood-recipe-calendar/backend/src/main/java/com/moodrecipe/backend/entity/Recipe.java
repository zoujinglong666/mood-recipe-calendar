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

    private String season;

    /** 仅用于本次响应，不进入菜谱表。 */
    @Transient
    private String recommendationReason;

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
