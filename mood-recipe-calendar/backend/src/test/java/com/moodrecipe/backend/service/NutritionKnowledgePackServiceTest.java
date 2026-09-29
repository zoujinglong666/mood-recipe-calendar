package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.NutritionKnowledgePack;
import com.moodrecipe.backend.entity.SeasonalIngredient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NutritionKnowledgePackServiceTest {

    @Test
    void onlyEnabledCommerciallyUsableKnowledgePackIsEligible() {
        NutritionKnowledgePack pack = new NutritionKnowledgePack();
        pack.setEnabled(true);
        pack.setLicenseStatus("commercially-usable");
        assertTrue(pack.isEligible());

        pack.setEnabled(false);
        assertFalse(pack.isEligible());

        pack.setEnabled(true);
        pack.setLicenseStatus("unknown");
        assertFalse(pack.isEligible());
    }

    @Test
    void onlyEnabledCommerciallyUsableSeasonalIngredientIsEligible() {
        SeasonalIngredient ingredient = new SeasonalIngredient();
        ingredient.setEnabled(true);
        ingredient.setLicenseStatus("commercially-usable");
        assertTrue(ingredient.isEligible());

        ingredient.setLicenseStatus("restricted");
        assertFalse(ingredient.isEligible());
    }
}
