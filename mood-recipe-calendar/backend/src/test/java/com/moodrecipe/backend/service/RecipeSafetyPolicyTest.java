package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.Recipe;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeSafetyPolicyTest {
    @Test
    void rejectsHighRiskFoodAndMedicalClaims() {
        Recipe recipe = new Recipe();
        recipe.setName("野生菌炖鸡");
        recipe.setDescription("可治疗高血压");
        assertFalse(RecipeSafetyPolicy.isSafe(recipe));

        recipe.setName("番茄炒蛋");
        recipe.setDescription("普通家常菜");
        recipe.setIngredients("[番茄,鸡蛋]");
        recipe.setSteps("[全熟]");
        assertTrue(RecipeSafetyPolicy.isSafe(recipe));
    }
}
