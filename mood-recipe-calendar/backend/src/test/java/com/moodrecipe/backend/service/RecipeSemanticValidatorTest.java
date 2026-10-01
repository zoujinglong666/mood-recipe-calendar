package com.moodrecipe.backend.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeSemanticValidatorTest {
    @Test
    void rejectsRepeatedIngredientAction() {
        assertFalse(RecipeSemanticValidator.isValid(List.of("枸杞"), List.of("出锅前撒枸杞和枸杞")));
    }

    @Test
    void acceptsExecutableSteps() {
        assertTrue(RecipeSemanticValidator.isValid(List.of("鸡蛋 2 个"), List.of("鸡蛋打散。", "锅中加油炒熟。")));
    }

    @Test
    void rejectsUrlEncodedOrReplacementText() {
        assertFalse(RecipeSemanticValidator.isDisplayable("%E8%8F%9C%E5%90%8D", 40));
        assertFalse(RecipeSemanticValidator.isDisplayable("菜名�", 40));
    }
}
