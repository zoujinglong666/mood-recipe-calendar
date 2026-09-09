package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.entity.UserFoodPreference;
import com.moodrecipe.backend.repository.RecipeInteractionRepository;
import com.moodrecipe.backend.repository.RecipeRepository;
import com.moodrecipe.backend.repository.UserFoodPreferenceRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 测试 GuozaiAgent 的本地回退推荐逻辑（AI 不可用时的菜谱筛选、偏好过滤、评分排序）。
 */
class GuozaiAgentTest {

    private GuozaiAgent buildAgent(RecipeRepository recipes, RecipeInteractionRepository interactions,
                                   UserFoodPreferenceRepository preferences) {
        AiRecipeService ai = mock(AiRecipeService.class);
        // AI 始终返回 empty，触发本地回退
        when(ai.recommendWithPersona(anyString(), anyString(), any())).thenReturn(Optional.empty());
        when(ai.recommend(anyString(), anyString(), any())).thenReturn(Optional.empty());
        when(ai.recommend(anyString(), anyString())).thenReturn(Optional.empty());

        return buildAgent(recipes, interactions, preferences, ai, mock(RecommendationExposureService.class));
    }

    private GuozaiAgent buildAgent(RecipeRepository recipes, RecipeInteractionRepository interactions,
                                   UserFoodPreferenceRepository preferences, AiRecipeService ai,
                                   RecommendationExposureService exposures) {
        GuozaiMemory memory = mock(GuozaiMemory.class);
        when(memory.snapshot(anyString(), anyInt())).thenReturn(
                new GuozaiMemory.MemorySnapshot("午间", "还在了解", "", "", 0, false,
                        "", "", null, true, null));

        GuozaiPersona persona = new GuozaiPersona();
        OperationalEventService events = mock(OperationalEventService.class);

        when(exposures.recordShown(anyString(), any(Recipe.class), anyString())).thenReturn("exposure-1");
        return new GuozaiAgent(ai, memory, persona, recipes, interactions, preferences, events, exposures);
    }

    @Test
    void excludesExplicitAvoidIngredients() {
        RecipeRepository recipes = mock(RecipeRepository.class);
        RecipeInteractionRepository interactions = mock(RecipeInteractionRepository.class);
        UserFoodPreferenceRepository preferences = mock(UserFoodPreferenceRepository.class);
        GuozaiAgent agent = buildAgent(recipes, interactions, preferences);

        Recipe peanutDish = recipe(1L, "老醋花生", "花生 200g");
        Recipe tomatoDish = recipe(2L, "番茄炒蛋", "番茄 2 个，鸡蛋 3 个");
        UserFoodPreference preference = new UserFoodPreference();
        preference.setAvoidIngredients("花生");

        when(interactions.findTop30ByOpenidOrderByCreatedAtDesc("user-1")).thenReturn(List.of());
        when(interactions.findByOpenidAndAction("user-1", "DISLIKE")).thenReturn(List.of());
        when(preferences.findByOpenid("user-1")).thenReturn(Optional.of(preference));
        when(recipes.findByMoodTag("平静")).thenReturn(List.of(peanutDish, tomatoDish));

        Recipe result = agent.recommend("user-1", "平静", null);
        assertEquals("番茄炒蛋", result.getName());
    }

    @Test
    void boostsRecipesMatchingFavoriteCuisine() {
        RecipeRepository recipes = mock(RecipeRepository.class);
        RecipeInteractionRepository interactions = mock(RecipeInteractionRepository.class);
        UserFoodPreferenceRepository preferences = mock(UserFoodPreferenceRepository.class);
        GuozaiAgent agent = buildAgent(recipes, interactions, preferences);

        Recipe tomatoDish = recipe(1L, "番茄炒蛋", "番茄 2 个，鸡蛋 3 个");
        Recipe sichuanDish = recipe(2L, "麻婆豆腐", "豆腐 1 块，豆瓣酱适量");
        UserFoodPreference preference = new UserFoodPreference();
        preference.setFavoriteCuisines("川菜");

        when(interactions.findTop30ByOpenidOrderByCreatedAtDesc("user-1")).thenReturn(List.of());
        when(interactions.findByOpenidAndAction("user-1", "DISLIKE")).thenReturn(List.of());
        when(preferences.findByOpenid("user-1")).thenReturn(Optional.of(preference));
        when(recipes.findByMoodTag("平静")).thenReturn(List.of(tomatoDish, sichuanDish));

        Recipe result = agent.recommend("user-1", "平静", null);
        assertEquals("麻婆豆腐", result.getName());
        assertTrue(result.getRecommendationReason().contains("川菜"));
    }

    @Test
    void fallsBackWhenNoRecipeMatchesFavoriteCuisine() {
        RecipeRepository recipes = mock(RecipeRepository.class);
        RecipeInteractionRepository interactions = mock(RecipeInteractionRepository.class);
        UserFoodPreferenceRepository preferences = mock(UserFoodPreferenceRepository.class);
        GuozaiAgent agent = buildAgent(recipes, interactions, preferences);

        Recipe tomatoDish = recipe(1L, "番茄炒蛋", "番茄 2 个，鸡蛋 3 个");
        UserFoodPreference preference = new UserFoodPreference();
        preference.setFavoriteCuisines("粤菜");

        when(interactions.findTop30ByOpenidOrderByCreatedAtDesc("user-1")).thenReturn(List.of());
        when(interactions.findByOpenidAndAction("user-1", "DISLIKE")).thenReturn(List.of());
        when(preferences.findByOpenid("user-1")).thenReturn(Optional.of(preference));
        when(recipes.findByMoodTag("平静")).thenReturn(List.of(tomatoDish));

        Recipe result = agent.recommend("user-1", "平静", null);
        assertEquals("番茄炒蛋", result.getName());
    }

    @Test
    void rejectsAiRecipeContainingAvoidIngredient() {
        RecipeRepository recipes = mock(RecipeRepository.class);
        RecipeInteractionRepository interactions = mock(RecipeInteractionRepository.class);
        UserFoodPreferenceRepository preferences = mock(UserFoodPreferenceRepository.class);
        AiRecipeService ai = mock(AiRecipeService.class);
        RecommendationExposureService exposures = mock(RecommendationExposureService.class);
        Recipe peanutDish = recipe(null, "老醋花生", "花生 200g");
        Recipe tomatoDish = recipe(2L, "番茄炒蛋", "番茄 2 个，鸡蛋 3 个");
        UserFoodPreference preference = new UserFoodPreference();
        preference.setAvoidIngredients("花生");

        when(ai.recommendWithPersona(anyString(), anyString(), any())).thenReturn(Optional.of(peanutDish));
        when(preferences.findByOpenid("user-1")).thenReturn(Optional.of(preference));
        when(interactions.findTop30ByOpenidOrderByCreatedAtDesc("user-1")).thenReturn(List.of());
        when(interactions.findByOpenidAndAction("user-1", "DISLIKE")).thenReturn(List.of());
        when(recipes.findByMoodTag("平静")).thenReturn(List.of(tomatoDish));

        Recipe result = buildAgent(recipes, interactions, preferences, ai, exposures)
                .recommend("user-1", "平静", null);
        assertEquals("番茄炒蛋", result.getName());
    }

    @Test
    void givesValidAiRecipeAnExposureId() {
        RecipeRepository recipes = mock(RecipeRepository.class);
        RecipeInteractionRepository interactions = mock(RecipeInteractionRepository.class);
        UserFoodPreferenceRepository preferences = mock(UserFoodPreferenceRepository.class);
        AiRecipeService ai = mock(AiRecipeService.class);
        RecommendationExposureService exposures = mock(RecommendationExposureService.class);
        Recipe generated = recipe(null, "番茄炒蛋", "番茄 2 个，鸡蛋 3 个");

        when(ai.recommendWithPersona(anyString(), anyString(), any())).thenReturn(Optional.of(generated));
        when(preferences.findByOpenid("user-1")).thenReturn(Optional.empty());
        when(exposures.recordShown(anyString(), any(Recipe.class), anyString())).thenReturn("exposure-1");

        Recipe result = buildAgent(recipes, interactions, preferences, ai, exposures)
                .recommend("user-1", "平静", null);
        assertEquals("exposure-1", result.getExposureId());
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
