package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.entity.UserFoodPreference;
import com.moodrecipe.backend.repository.RecipeInteractionRepository;
import com.moodrecipe.backend.repository.UserFoodPreferenceRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserFoodPreferenceControllerTest {
    @Test
    void savesFavoriteCuisines() {
        UserFoodPreferenceRepository preferences = mock(UserFoodPreferenceRepository.class);
        UserFoodPreferenceController controller = new UserFoodPreferenceController(
                preferences, mock(RecipeInteractionRepository.class));
        when(preferences.findByOpenid("user-1")).thenReturn(Optional.empty());
        when(preferences.save(any(UserFoodPreference.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserFoodPreference result = controller.save("user-1", new UserFoodPreferenceController.PreferenceRequest(
                "家常菜", "川菜,湘菜", "番茄炒蛋", "", "", true, false, "NORMAL")).getData();

        assertEquals("川菜,湘菜", result.getFavoriteCuisines());
    }
}
