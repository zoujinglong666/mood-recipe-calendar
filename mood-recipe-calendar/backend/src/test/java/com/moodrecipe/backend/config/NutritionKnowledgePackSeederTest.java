package com.moodrecipe.backend.config;

import com.moodrecipe.backend.entity.NutritionKnowledgePack;
import com.moodrecipe.backend.repository.NutritionKnowledgePackRepository;
import com.moodrecipe.backend.repository.SeasonalIngredientRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NutritionKnowledgePackSeederTest {

    @Test
    void seedsOnlyEmptyCatalogsWithCommerciallyUsableHealthyAdultData() throws Exception {
        NutritionKnowledgePackRepository packs = mock(NutritionKnowledgePackRepository.class);
        SeasonalIngredientRepository ingredients = mock(SeasonalIngredientRepository.class);
        when(packs.findAll()).thenReturn(List.of());
        when(ingredients.findAll()).thenReturn(List.of());

        new NutritionKnowledgePackSeeder(packs, ingredients).run(new DefaultApplicationArguments(new String[0]));

        verify(packs).saveAll(anyList());
        verify(ingredients).saveAll(anyList());
    }

    @Test
    void neverOverwritesAnExistingReviewedKnowledgePack() throws Exception {
        NutritionKnowledgePackRepository packs = mock(NutritionKnowledgePackRepository.class);
        SeasonalIngredientRepository ingredients = mock(SeasonalIngredientRepository.class);
        when(packs.findAll()).thenReturn(List.of(new NutritionKnowledgePack()));
        when(ingredients.findAll()).thenReturn(List.of());

        new NutritionKnowledgePackSeeder(packs, ingredients).run(new DefaultApplicationArguments(new String[0]));

        verify(packs, never()).saveAll(anyList());
        verify(ingredients).saveAll(anyList());
    }
}
