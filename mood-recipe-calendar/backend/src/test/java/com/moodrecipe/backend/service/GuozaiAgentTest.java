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
    void boostsProteinRecipeForFitnessGoal() {
        RecipeRepository recipes = mock(RecipeRepository.class);
        RecipeInteractionRepository interactions = mock(RecipeInteractionRepository.class);
        UserFoodPreferenceRepository preferences = mock(UserFoodPreferenceRepository.class);
        GuozaiAgent agent = buildAgent(recipes, interactions, preferences);

        Recipe congee = recipe(1L, "青菜粥", "大米和青菜");
        Recipe chicken = recipe(2L, "鸡胸肉西兰花", "鸡胸肉、西兰花和米饭");
        UserFoodPreference preference = new UserFoodPreference();
        preference.setHealthGoal("FITNESS");

        when(interactions.findTop30ByOpenidOrderByCreatedAtDesc("user-1")).thenReturn(List.of());
        when(interactions.findByOpenidAndAction("user-1", "DISLIKE")).thenReturn(List.of());
        when(preferences.findByOpenid("user-1")).thenReturn(Optional.of(preference));
        when(recipes.findByMoodTag("平静")).thenReturn(List.of(congee, chicken));

        Recipe result = agent.recommend("user-1", "平静", null);
        assertEquals("鸡胸肉西兰花", result.getName());
        assertTrue(result.getRecommendationReason().contains("健身增肌"));
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

    @Test
    void plansTwoDifferentDishesAndHonorsAvoidIngredients() {
        RecipeRepository recipes = mock(RecipeRepository.class);
        RecipeInteractionRepository interactions = mock(RecipeInteractionRepository.class);
        UserFoodPreferenceRepository preferences = mock(UserFoodPreferenceRepository.class);
        GuozaiAgent agent = buildAgent(recipes, interactions, preferences);
        Recipe peanutDish = recipe(1L, "老醋花生", "花生 200g");
        Recipe chicken = recipe(2L, "清蒸鸡腿", "鸡腿 2 个");
        Recipe broccoli = recipe(3L, "蒜蓉西兰花", "西兰花 1 颗");
        UserFoodPreference preference = new UserFoodPreference();
        preference.setAvoidIngredients("花生");

        when(preferences.findByOpenid("user-1")).thenReturn(Optional.of(preference));
        when(interactions.findTop30ByOpenidOrderByCreatedAtDesc("user-1")).thenReturn(List.of());
        when(interactions.findByOpenidAndAction("user-1", "DISLIKE")).thenReturn(List.of());
        when(recipes.findAll()).thenReturn(List.of(peanutDish, chicken, broccoli));

        List<Recipe> menu = agent.planWeeklyMenu("user-1", 1, 2, "BALANCED");

        assertEquals(2, menu.size());
        assertTrue(menu.stream().noneMatch(recipe -> recipe.getName().contains("花生")));
        assertEquals("清蒸鸡腿", menu.get(0).getName());
        assertEquals("蒜蓉西兰花", menu.get(1).getName());
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
