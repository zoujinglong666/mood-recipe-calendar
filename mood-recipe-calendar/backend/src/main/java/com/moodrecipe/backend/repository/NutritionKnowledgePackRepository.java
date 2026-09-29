package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.NutritionKnowledgePack;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NutritionKnowledgePackRepository extends JpaRepository<NutritionKnowledgePack, Long> {

    List<NutritionKnowledgePack> findByEnabledTrueAndLicenseStatus(String licenseStatus);
}
