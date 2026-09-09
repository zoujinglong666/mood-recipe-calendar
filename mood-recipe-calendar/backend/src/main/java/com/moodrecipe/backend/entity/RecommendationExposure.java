package com.moodrecipe.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "recommendation_exposures", indexes = {
        @Index(name = "idx_recommendation_exposure_user_time", columnList = "openid,created_at")
})
public class RecommendationExposure {
    @Id
    @Column(length = 36)
    private String id;

    @JsonIgnore
    @Column(nullable = false)
    private String openid;

    @JsonIgnore
    @Column(name = "dish_key", nullable = false, length = 64)
    private String dishKey;

    @Column(nullable = false, length = 16)
    private String source;

    @JsonIgnore
    @Column(nullable = false)
    private boolean liked;

    @JsonIgnore
    @Column(nullable = false)
    private boolean disliked;

    @JsonIgnore
    @Column(nullable = false)
    private boolean made;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void create() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void update() {
        updatedAt = LocalDateTime.now();
    }
}
