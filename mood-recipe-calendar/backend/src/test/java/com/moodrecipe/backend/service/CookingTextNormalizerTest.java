package com.moodrecipe.backend.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CookingTextNormalizerTest {

    @Test
    void makesUnclearCutPhraseActionable() {
        List<String> steps = CookingTextNormalizer.normalizeSteps(List.of("西兰花切朵，焯水"));

        assertEquals("西兰花掰成小朵，焯水", steps.get(0));
    }

    @Test
    void removesRepeatedIngredientInOneAction() {
        List<String> steps = CookingTextNormalizer.normalizeSteps(List.of("出锅前撒枸杞和枸杞"));

        assertEquals("出锅前撒枸杞", steps.get(0));
    }

    @Test
    void deduplicatesIngredientEntriesAfterRemovingActionPrefix() {
        List<String> ingredients = CookingTextNormalizer.normalizeIngredients(List.of("枸杞", "撒枸杞", "盐 2克"));

        assertEquals(2, ingredients.size());
        assertFalse(ingredients.contains("撒枸杞"));
    }
}
