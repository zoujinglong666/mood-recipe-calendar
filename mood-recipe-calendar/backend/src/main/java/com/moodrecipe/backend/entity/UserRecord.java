package com.moodrecipe.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户每日伙食记录（对应 PRD user_records）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_records")
public class UserRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 微信 openid */
    private String openid;

    /** 菜品图片地址 */
    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    /** 菜名 */
    @Column(name = "dish_name")
    private String dishName;

    /** 心情标签 */
    @Column(name = "mood_tag")
    private String moodTag;

    /** 心情日记 */
    private String note;

    /** 关联推荐菜谱ID */
    @Column(name = "recipe_id")
    private String recipeId;

    /** 烹饪分钟数 */
    @Column(name = "cooking_time")
    private Integer cookingTime;

    /** 记录日期 YYYY-MM-DD */
    @Column(name = "record_date")
    private String recordDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
