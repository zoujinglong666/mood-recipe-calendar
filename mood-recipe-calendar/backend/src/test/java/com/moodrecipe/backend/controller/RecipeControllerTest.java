package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.entity.UserFoodPreference;
import com.moodrecipe.backend.repository.RecipeInteractionRepository;
import com.moodrecipe.backend.repository.RecipeRepository;
import com.moodrecipe.backend.repository.UserFoodPreferenceRepository;
import com.moodrecipe.backend.service.AiRecipeService;
import com.moodrecipe.backend.service.OperationalEventService;
import com.moodrecipe.backend.service.VirtualCommerceService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecipeControllerTest {
    @Test
    void excludesExplicitAvoidIngredients() {
        RecipeRepository recipes = mock(RecipeRepository.class);
        RecipeInteractionRepository interactions = mock(RecipeInteractionRepository.class);
        UserFoodPreferenceRepository preferences = mock(UserFoodPreferenceRepository.class);
        RecipeController controller = new RecipeController(recipes, interactions, preferences,
                mock(AiRecipeService.class), mock(VirtualCommerceService.class), mock(OperationalEventService.class));

        Recipe peanutDish = recipe(1L, "老醋花生", "花生 200g");
        Recipe tomatoDish = recipe(2L, "番茄炒蛋", "番茄 2 个，鸡蛋 3 个");
        UserFoodPreference preference = new UserFoodPreference();
        preference.setAvoidIngredients("花生");

        when(interactions.findTop30ByOpenidOrderByCreatedAtDesc("user-1")).thenReturn(List.of());
        when(interactions.findByOpenidAndAction("user-1", "DISLIKE")).thenReturn(List.of());
        when(preferences.findByOpenid("user-1")).thenReturn(Optional.of(preference));
        when(recipes.findByMoodTag("平静")).thenReturn(List.of(peanutDish, tomatoDish));

        assertEquals("番茄炒蛋", controller.recommend("user-1", "平静").getData().getName());
    }

    @Test
    void boostsRecipesMatchingFavoriteCuisine() {
        RecipeRepository recipes = mock(RecipeRepository.class);
        RecipeInteractionRepository interactions = mock(RecipeInteractionRepository.class);
        UserFoodPreferenceRepository preferences = mock(UserFoodPreferenceRepository.class);
        RecipeController controller = new RecipeController(recipes, interactions, preferences,
                mock(AiRecipeService.class), mock(VirtualCommerceService.class), mock(OperationalEventService.class));

        Recipe tomatoDish = recipe(1L, "番茄炒蛋", "番茄 2 个，鸡蛋 3 个");
        Recipe sichuanDish = recipe(2L, "麻婆豆腐", "豆腐 1 块，豆瓣酱适量");
        UserFoodPreference preference = new UserFoodPreference();
        preference.setFavoriteCuisines("川菜");

        when(interactions.findTop30ByOpenidOrderByCreatedAtDesc("user-1")).thenReturn(List.of());
        when(interactions.findByOpenidAndAction("user-1", "DISLIKE")).thenReturn(List.of());
        when(preferences.findByOpenid("user-1")).thenReturn(Optional.of(preference));
        when(recipes.findByMoodTag("平静")).thenReturn(List.of(tomatoDish, sichuanDish));

        Recipe result = controller.recommend("user-1", "平静").getData();
        assertEquals("麻婆豆腐", result.getName());
        assertTrue(result.getRecommendationReason().contains("川菜"));
    }

    @Test
    void fallsBackWhenNoRecipeMatchesFavoriteCuisine() {
        RecipeRepository recipes = mock(RecipeRepository.class);
        RecipeInteractionRepository interactions = mock(RecipeInteractionRepository.class);
        UserFoodPreferenceRepository preferences = mock(UserFoodPreferenceRepository.class);
        RecipeController controller = new RecipeController(recipes, interactions, preferences,
                mock(AiRecipeService.class), mock(VirtualCommerceService.class), mock(OperationalEventService.class));

        Recipe tomatoDish = recipe(1L, "番茄炒蛋", "番茄 2 个，鸡蛋 3 个");
        UserFoodPreference preference = new UserFoodPreference();
        preference.setFavoriteCuisines("粤菜");

        when(interactions.findTop30ByOpenidOrderByCreatedAtDesc("user-1")).thenReturn(List.of());
        when(interactions.findByOpenidAndAction("user-1", "DISLIKE")).thenReturn(List.of());
        when(preferences.findByOpenid("user-1")).thenReturn(Optional.of(preference));
        when(recipes.findByMoodTag("平静")).thenReturn(List.of(tomatoDish));

        assertEquals("番茄炒蛋", controller.recommend("user-1", "平静").getData().getName());
    }

    private Recipe recipe(Long id, String name, String ingredients) {
        Recipe recipe = new Recipe();
        recipe.setId(id);
        recipe.setName(name);
        recipe.setDescription("");
        recipe.setIngredients(ingredients);
        recipe.setMoodTags("平静");
        return recipe;
    }
}
