package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.agent.LlmClient;
import com.moodrecipe.backend.entity.NutritionKnowledgePack;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.entity.UserFoodPreference;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DailyMealPlanValidatorTest {

    private final AllergenNormalizationService allergens = allergens();
    private final DailyMealPlanValidator validator = new DailyMealPlanValidator(allergens);

    @Test
    void rejectsShrimpDishWhenUserIsAllergicToPrawns() {
        UserFoodPreference preference = new UserFoodPreference();
        preference.setAllergens("对虾过敏");

        DailyMealPlanValidator.ValidationResult result = validator.validate(List.of(
                recipe("虾仁炒蛋", "虾仁,鸡蛋"), recipe("番茄牛腩", "牛腩,番茄"), recipe("清炒油麦菜", "油麦菜")),
                preference, List.of(eligiblePack()));

        assertFalse(result.valid());
        assertTrue(result.reasons().stream().anyMatch(reason -> reason.contains("虾")));
    }

    @Test
    void rejectsRepeatedCoreProteinAcrossMeals() {
        DailyMealPlanValidator.ValidationResult result = validator.validate(List.of(
                recipe("鸡胸肉三明治", "鸡胸肉,全麦面包"), recipe("香煎鸡胸肉", "鸡胸肉,西兰花"), recipe("番茄豆腐汤", "豆腐,番茄")),
                new UserFoodPreference(), List.of(eligiblePack()));

        assertFalse(result.valid());
        assertTrue(result.reasons().stream().anyMatch(reason -> reason.contains("核心食材重复")));
    }

    @Test
    void rejectsMojibakeTitleBeforeItCanReachTheClient() {
        DailyMealPlanValidator.ValidationResult result = validator.validate(List.of(
                recipe("ç•ªéŒŒ‚ç’ë›×", "鸡蛋"), recipe("番茄牛腩", "牛腩,番茄"), recipe("清炒油麦菜", "油麦菜")),
                new UserFoodPreference(), List.of(eligiblePack()));

        assertFalse(result.valid());
        assertTrue(result.reasons().stream().anyMatch(reason -> reason.contains("菜名不可展示")));
    }

    @Test
    void rejectsPlanWhenAnyKnowledgePackCannotBeCommerciallyUsed() {
        NutritionKnowledgePack unknown = eligiblePack();
        unknown.setLicenseStatus("unknown");

        DailyMealPlanValidator.ValidationResult result = validator.validate(List.of(
                recipe("鸡蛋三明治", "鸡蛋,全麦面包"), recipe("番茄牛腩", "牛腩,番茄"), recipe("清炒油麦菜", "油麦菜")),
                new UserFoodPreference(), List.of(unknown));

        assertFalse(result.valid());
        assertTrue(result.reasons().stream().anyMatch(reason -> reason.contains("知识包")));
    }

    private AllergenNormalizationService allergens() {
        LlmClient client = mock(LlmClient.class);
        when(client.isConfigured()).thenReturn(false);
        return new AllergenNormalizationService(client, new ObjectMapper());
    }

    private NutritionKnowledgePack eligiblePack() {
        NutritionKnowledgePack pack = new NutritionKnowledgePack();
        pack.setEnabled(true);
        pack.setLicenseStatus("commercially-usable");
        return pack;
    }

    private Recipe recipe(String name, String ingredients) {
        Recipe recipe = new Recipe();
        recipe.setName(name);
        recipe.setIngredients(ingredients);
        recipe.setSteps("洗净后炒熟即可");
        return recipe;
    }
}
