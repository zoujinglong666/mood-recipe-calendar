package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.entity.RecipeInteraction;
import com.moodrecipe.backend.entity.UserFoodPreference;
import com.moodrecipe.backend.entity.UserRecord;
import com.moodrecipe.backend.repository.RecipeInteractionRepository;
import com.moodrecipe.backend.repository.RecipeRepository;
import com.moodrecipe.backend.repository.UserFoodPreferenceRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/**
 * 测试 GuozaiAgent 的本地回退推荐逻辑（AI 不可用时的菜谱筛选、偏好过滤、评分排序）。
 */
class GuozaiAgentTest {

    @Test
    void activatesFestivalScenesOnlyInsideTheirWindows() {
        assertEquals("MID_AUTUMN", GuozaiAgent.festivalAt(LocalDate.of(2026, 9, 19)).orElseThrow().scene());
        assertEquals("NATIONAL_DAY", GuozaiAgent.festivalAt(LocalDate.of(2026, 10, 1)).orElseThrow().scene());
        assertTrue(GuozaiAgent.festivalAt(LocalDate.of(2026, 9, 17)).isEmpty());
        assertTrue(GuozaiAgent.festivalAt(LocalDate.of(2026, 10, 8)).isEmpty());
    }

    private GuozaiAgent buildAgent(RecipeRepository recipes, RecipeInteractionRepository interactions,
                                   UserFoodPreferenceRepository preferences) {
        AiRecipeService ai = mock(AiRecipeService.class);
        // AI 始终返回 empty，触发本地回退
        when(ai.recommendWithPersona(anyString(), anyString(), any())).thenReturn(Optional.empty());
        when(ai.recommend(anyString(), anyString(), any())).thenReturn(Optional.empty());
        when(ai.recommend(anyString(), anyString())).thenReturn(Optional.empty());
        when(ai.recommendWeekly(anyString(), anyInt())).thenReturn(Optional.empty());
        when(ai.monthlyCompanionMessage(anyString())).thenReturn(Optional.empty());
        when(ai.companionMessageWithPersona(anyString())).thenReturn(Optional.empty());

        return buildAgent(recipes, interactions, preferences, ai, mock(RecommendationExposureService.class));
    }

    @Test
    void festivalCompanionOpensAgentWithFestivalContext() {
        RecipeRepository recipes = mock(RecipeRepository.class);
        RecipeInteractionRepository interactions = mock(RecipeInteractionRepository.class);
        UserFoodPreferenceRepository preferences = mock(UserFoodPreferenceRepository.class);

        CompanionMessageService.Message message = buildAgent(recipes, interactions, preferences)
                .companion("user-1", 18, LocalDate.of(2026, 9, 25));

        assertEquals("MID_AUTUMN", message.scene());
        assertEquals("meal-agent", message.actionTarget());
        assertTrue(message.greeting().contains("中秋快乐"));
        assertTrue(message.actionPrompt().contains("中秋"));
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
        return new GuozaiAgent(ai, memory, persona, recipes, interactions, preferences, events, exposures,
                new WechatContentSafetyService(new com.fasterxml.jackson.databind.ObjectMapper(), "", "", false, false));
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
        when(recipes.findAiWithImages()).thenReturn(List.of(peanutDish, tomatoDish));

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
        when(recipes.findAiWithImages()).thenReturn(List.of(tomatoDish, sichuanDish));

        Recipe result = agent.recommend("user-1", "平静", null);
        assertEquals("麻婆豆腐", result.getName());
        assertTrue(result.getRecommendationReason().contains("川菜"));
    }

    @Test
    void boostsJiangxiCuisineAfterItIsLearned() {
        RecipeRepository recipes = mock(RecipeRepository.class);
        RecipeInteractionRepository interactions = mock(RecipeInteractionRepository.class);
        UserFoodPreferenceRepository preferences = mock(UserFoodPreferenceRepository.class);
        UserFoodPreference preference = new UserFoodPreference();
        preference.setFavoriteCuisines("赣菜");
        Recipe jiangxiDish = recipe(1L, "宁都三杯鸡", "鸡腿 2 个，米酒 20ml");
        Recipe tomatoDish = recipe(2L, "番茄炒蛋", "番茄 2 个，鸡蛋 3 个");
        when(preferences.findByOpenid("user-1")).thenReturn(Optional.of(preference));
        when(interactions.findTop30ByOpenidOrderByCreatedAtDesc("user-1")).thenReturn(List.of());
        when(interactions.findByOpenidAndAction("user-1", "DISLIKE")).thenReturn(List.of());
        when(recipes.findAiWithImages()).thenReturn(List.of(tomatoDish, jiangxiDish));

        assertEquals("宁都三杯鸡", buildAgent(recipes, interactions, preferences)
                .recommend("user-1", "平静", null).getName());
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
        when(recipes.findAiWithImages()).thenReturn(List.of(tomatoDish));

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
        when(recipes.findAiWithImages()).thenReturn(List.of(congee, chicken));

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
        when(recipes.findAiWithImages()).thenReturn(List.of(tomatoDish));

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
        when(recipes.save(generated)).thenAnswer(invocation -> {
            generated.setId(99L);
            return generated;
        });
        when(exposures.recordShown(anyString(), any(Recipe.class), anyString())).thenReturn("exposure-1");

        Recipe result = buildAgent(recipes, interactions, preferences, ai, exposures)
                .recommend("user-1", "平静", null);
        assertEquals(99L, result.getId());
        assertEquals("AI", result.getSource());
        assertEquals("exposure-1", result.getExposureId());
        verify(recipes).save(generated);
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
        when(recipes.findAiWithImages()).thenReturn(List.of(peanutDish, chicken, broccoli));

        List<Recipe> menu = agent.planWeeklyMenu("user-1", 1, 2, "BALANCED");

        assertEquals(2, menu.size());
        assertTrue(menu.stream().noneMatch(recipe -> recipe.getName().contains("花生")));
        assertEquals("清蒸鸡腿", menu.get(0).getName());
        assertEquals("蒜蓉西兰花", menu.get(1).getName());
    }

    @Test
    void weeklyPlanUsesAiWithoutReadingLocalCatalogWhenAiReturnsEnough() {
        RecipeRepository recipes = mock(RecipeRepository.class);
        RecipeInteractionRepository interactions = mock(RecipeInteractionRepository.class);
        UserFoodPreferenceRepository preferences = mock(UserFoodPreferenceRepository.class);
        AiRecipeService ai = mock(AiRecipeService.class);
        when(preferences.findByOpenid("user-1")).thenReturn(Optional.empty());
        when(ai.recommendWeekly(anyString(), anyInt())).thenReturn(Optional.of(List.of(
                recipe(null, "AI 清蒸鸡腿", "鸡腿 2 个"),
                recipe(null, "AI 蒜蓉青菜", "青菜 300g"))));

        List<Recipe> menu = buildAgent(recipes, interactions, preferences, ai,
                mock(RecommendationExposureService.class)).planWeeklyMenu("user-1", 1, 2, "BALANCED");

        assertEquals(List.of("AI 清蒸鸡腿", "AI 蒜蓉青菜"), menu.stream().map(Recipe::getName).toList());
        verify(recipes, never()).findAll();
    }

    @Test
    void prefersEverydayIngredientsForSaveBudget() {
        RecipeRepository recipes = mock(RecipeRepository.class);
        RecipeInteractionRepository interactions = mock(RecipeInteractionRepository.class);
        UserFoodPreferenceRepository preferences = mock(UserFoodPreferenceRepository.class);
        Recipe steak = recipe(1L, "香煎牛排", "牛排 2 块");
        Recipe tofu = recipe(2L, "家常豆腐", "豆腐 1 块，白菜 200g");

        when(preferences.findByOpenid("user-1")).thenReturn(Optional.empty());
        when(interactions.findTop30ByOpenidOrderByCreatedAtDesc("user-1")).thenReturn(List.of());
        when(interactions.findByOpenidAndAction("user-1", "DISLIKE")).thenReturn(List.of());
        when(recipes.findAiWithImages()).thenReturn(List.of(steak, tofu));

        List<Recipe> menu = buildAgent(recipes, interactions, preferences)
                .planWeeklyMenu("user-1", 1, 1, "BALANCED", "SAVE");

        assertEquals("家常豆腐", menu.get(0).getName());
    }

    @Test
    void expandsWeeklyPoolBeyondSingleAiRecipeWithoutRepeatingNames() {
        RecipeRepository recipes = mock(RecipeRepository.class);
        RecipeInteractionRepository interactions = mock(RecipeInteractionRepository.class);
        UserFoodPreferenceRepository preferences = mock(UserFoodPreferenceRepository.class);
        Recipe tomato = recipe(1L, "番茄炒蛋", "番茄，鸡蛋");
        Recipe chicken = recipe(2L, "香菇鸡腿", "香菇，鸡腿");
        Recipe greens = recipe(3L, "蒜蓉青菜", "青菜，大蒜");

        when(preferences.findByOpenid("user-1")).thenReturn(Optional.empty());
        when(interactions.findTop30ByOpenidOrderByCreatedAtDesc("user-1")).thenReturn(List.of());
        when(interactions.findByOpenidAndAction("user-1", "DISLIKE")).thenReturn(List.of());
        when(recipes.findAiWithImages()).thenReturn(List.of(tomato));
        when(recipes.findAll()).thenReturn(List.of(tomato, chicken, greens));

        List<Recipe> menu = buildAgent(recipes, interactions, preferences)
                .planWeeklyMenu("user-1", 1, 2, "BALANCED");

        assertEquals(2, menu.stream().map(Recipe::getName).distinct().count());
    }

    @Test
    void skipsRecentlyShownLocalRecipe() {
        RecipeRepository recipes = mock(RecipeRepository.class);
        RecipeInteractionRepository interactions = mock(RecipeInteractionRepository.class);
        UserFoodPreferenceRepository preferences = mock(UserFoodPreferenceRepository.class);
        Recipe first = recipe(1L, "番茄炒蛋", "番茄 2 个，鸡蛋 3 个");
        Recipe second = recipe(2L, "青椒肉丝", "青椒 2 个，猪肉 200g");
        RecipeInteraction shown = new RecipeInteraction();
        shown.setRecipeId(1L);
        shown.setAction("SHOWN");

        when(preferences.findByOpenid("user-1")).thenReturn(Optional.empty());
        when(interactions.findTop30ByOpenidOrderByCreatedAtDesc("user-1")).thenReturn(List.of(shown));
        when(interactions.findByOpenidAndAction("user-1", "DISLIKE")).thenReturn(List.of());
        when(recipes.findAiWithImages()).thenReturn(List.of(first, second));

        Recipe result = buildAgent(recipes, interactions, preferences).recommend("user-1", "平静", null);

        assertEquals("青椒肉丝", result.getName());
        verify(interactions).save(any(RecipeInteraction.class));
    }

    @Test
    void monthlyLetterUsesThisUsersActualRecords() {
        RecipeRepository recipes = mock(RecipeRepository.class);
        RecipeInteractionRepository interactions = mock(RecipeInteractionRepository.class);
        UserFoodPreferenceRepository preferences = mock(UserFoodPreferenceRepository.class);
        GuozaiAgent agent = buildAgent(recipes, interactions, preferences);

        UserRecord first = UserRecord.builder().recordDate("2026-09-01").dishName("小炒黄牛肉").moodTag("开心").build();
        UserRecord second = UserRecord.builder().recordDate("2026-09-02").dishName("小炒黄牛肉").moodTag("开心").build();
        String letter = agent.monthlyLetter("2026-09", List.of(first, second));

        assertTrue(letter.contains("2天"));
        assertTrue(letter.contains("小炒黄牛肉"));
        assertTrue(letter.contains("开心"));
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
