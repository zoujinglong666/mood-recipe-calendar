package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.entity.DailyMenu;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.repository.DailyMenuRepository;
import com.moodrecipe.backend.repository.RecipeInteractionRepository;
import com.moodrecipe.backend.repository.RecipeRepository;
import com.moodrecipe.backend.repository.UserFoodPreferenceRepository;
import com.moodrecipe.backend.repository.UserRecordRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DailyMenuServiceTest {

    @Test
    void doesNotReturnCachedRecipeWithQuestionMarkCorruption() {
        DailyMenuRepository dailyMenus = mock(DailyMenuRepository.class);
        RecipeRepository recipes = mock(RecipeRepository.class);
        DailyMenu cached = new DailyMenu();
        cached.setOpenid("user-1");
        cached.setMenuDate(LocalDate.now());
        cached.setRecipeId(9L);
        cached.setGuozaiLine("锅仔今天给你留了 ?????");
        when(dailyMenus.findByOpenidAndMenuDate(any(), any())).thenReturn(Optional.of(cached));

        Recipe broken = new Recipe();
        broken.setId(9L);
        broken.setName("?????");
        broken.setDescription("今晚 ?????");
        when(recipes.findById(9L)).thenReturn(Optional.of(broken));
        when(recipes.findAiWithImages()).thenReturn(java.util.List.of());
        when(recipes.findAll()).thenReturn(java.util.List.of());

        DailyMenuService service = new DailyMenuService(dailyMenus, recipes,
                mock(RecipeInteractionRepository.class), mock(UserRecordRepository.class),
                mock(UserFoodPreferenceRepository.class),
                new AllergenNormalizationService(null, new ObjectMapper()), new RecipePool(recipes));

        assertTrue(service.today("user-1").isEmpty());
    }
}
