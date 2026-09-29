package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.SeasonalIngredient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeasonalIngredientRepository extends JpaRepository<SeasonalIngredient, Long> {

    List<SeasonalIngredient> findByEnabledTrueAndLicenseStatus(String licenseStatus);
}
