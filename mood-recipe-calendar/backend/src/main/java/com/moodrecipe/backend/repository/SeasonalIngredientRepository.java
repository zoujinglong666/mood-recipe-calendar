package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.SeasonalIngredient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeasonalIngredientRepository extends JpaRepository<SeasonalIngredient, Long> {

    // [2026-10-20] 时令食材是慢变数据，按授权维度缓存即可让今日卡排序零额外 DB 开销地拿到"当季食材"。
    @Cacheable(cacheNames = "seasonalIngredients", key = "#licenseStatus")
    List<SeasonalIngredient> findByEnabledTrueAndLicenseStatus(String licenseStatus);
}
