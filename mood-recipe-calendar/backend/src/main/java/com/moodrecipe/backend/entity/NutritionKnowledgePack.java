package com.moodrecipe.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.time.LocalDateTime;

/** 可追溯的日常饮食知识包；不存储未获授权的指南原文。 */
@Data
@Entity
@Table(name = "nutrition_knowledge_packs", uniqueConstraints =
        @UniqueConstraint(name = "uk_nutrition_knowledge_pack_code_version", columnNames = {"code", "version"}))
public class NutritionKnowledgePack {

    public static final String COMMERCIALLY_USABLE = "commercially-usable";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String code;

    @Column(nullable = false, length = 32)
    private String version;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 80)
    private String audience;

    @Column(name = "recommendation_text", nullable = false, columnDefinition = "TEXT")
    private String recommendation;

    @Column(name = "ingredient_notes", columnDefinition = "TEXT")
    private String ingredientNotes;

    @Column(name = "hard_constraints", columnDefinition = "TEXT")
    private String hardConstraints;

    @Column(name = "explanation_text", nullable = false, columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "source_kind", nullable = false, length = 32)
    private String sourceKind;

    @Column(name = "source_reference", nullable = false, length = 500)
    private String sourceReference;

    @Column(name = "license_status", nullable = false, length = 32)
    private String licenseStatus;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public boolean isEligible() {
        return enabled && COMMERCIALLY_USABLE.equals(licenseStatus);
    }
}
