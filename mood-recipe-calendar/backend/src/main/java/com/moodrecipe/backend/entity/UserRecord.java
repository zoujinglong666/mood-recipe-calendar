package com.moodrecipe.backend.entity;

import com.moodrecipe.backend.config.AppClock;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户每日伙食记录（对应 PRD user_records）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_records", uniqueConstraints =
        @UniqueConstraint(name = "uk_user_record_request", columnNames = {"openid", "client_request_id"}))
public class UserRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 微信 openid */
    private String openid;

    /** 菜品图片地址 */
    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "image_urls", columnDefinition = "TEXT")
    private String imageUrlsJson;

    @JsonProperty("imageUrls")
    public List<String> getImageUrls() {
        if (imageUrlsJson == null || imageUrlsJson.isBlank()) return imageUrl == null || imageUrl.isBlank() ? List.of() : List.of(imageUrl);
        return java.util.Arrays.stream(imageUrlsJson.split("\\n")).filter(value -> !value.isBlank()).toList();
    }
    public void setImageUrls(List<String> urls) { this.imageUrlsJson = String.join("\n", urls == null ? List.of() : urls); }

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
    private Long recipeId;

    /** 关联推荐曝光ID（recommendation_exposures.id），保存记录时持久化，供编辑回填与反馈关联。 */
    @Column(name = "exposure_id", length = 36)
    private String exposureId;

    /** 客户端重试标识；旧记录允许为空。 */
    @Column(name = "client_request_id", length = 64)
    private String clientRequestId;

    /** 烹饪分钟数 */
    @Column(name = "cooking_time")
    private Integer cookingTime;

    /** 记录日期 YYYY-MM-DD */
    @Column(name = "record_date")
    private String recordDate;

    /** 会员服务端生成的杂志海报地址。 */
    @Column(name = "poster_url", columnDefinition = "TEXT")
    private String posterUrl;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        this.createdAt = AppClock.now();
        this.updatedAt = AppClock.now();
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = AppClock.now();
    }
}
