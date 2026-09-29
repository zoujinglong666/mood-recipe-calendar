package com.moodrecipe.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/** 可维护的当季食材条目，和知识包一样必须携带来源与许可状态。 */
@Data
@Entity
@Table(name = "seasonal_ingredients")
public class SeasonalIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 32)
    private String region;

    @Column(name = "start_month", nullable = false)
    private Integer startMonth;

    @Column(name = "end_month", nullable = false)
    private Integer endMonth;

    @Column(name = "substitutes", columnDefinition = "TEXT")
    private String substitutes;

    @Column(name = "source_reference", nullable = false, length = 500)
    private String sourceReference;

    @Column(name = "license_status", nullable = false, length = 32)
    private String licenseStatus;

    @Column(nullable = false)
    private boolean enabled;

    public boolean isEligible() {
        return enabled && NutritionKnowledgePack.COMMERCIALLY_USABLE.equals(licenseStatus);
    }
}
