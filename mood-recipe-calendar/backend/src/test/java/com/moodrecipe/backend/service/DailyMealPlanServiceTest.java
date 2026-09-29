package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.agent.LlmClient;
import com.moodrecipe.backend.agent.AgentMemoryStore;
import com.moodrecipe.backend.agent.UserProfile;
import com.moodrecipe.backend.entity.NutritionKnowledgePack;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.entity.SeasonalIngredient;
import com.moodrecipe.backend.repository.DailyMealPlanRepository;
import com.moodrecipe.backend.repository.NutritionKnowledgePackRepository;
import com.moodrecipe.backend.repository.RecipeRepository;
import com.moodrecipe.backend.repository.SeasonalIngredientRepository;
import com.moodrecipe.backend.repository.UserFoodPreferenceRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DailyMealPlanServiceTest {
    @Test void savesOnlyACompleteValidatedThreeMealPlan() {
        DailyMealPlanRepository plans = mock(DailyMealPlanRepository.class);
        RecipeRepository recipes = mock(RecipeRepository.class);
        NutritionKnowledgePackRepository packs = mock(NutritionKnowledgePackRepository.class);
        SeasonalIngredientRepository seasonal = mock(SeasonalIngredientRepository.class);
        when(plans.findByOpenidAndPlanDate("u", LocalDate.of(2026, 9, 28))).thenReturn(java.util.Optional.empty());
        when(packs.findByEnabledTrueAndLicenseStatus("commercially-usable")).thenReturn(List.of(pack()));
        when(seasonal.findByEnabledTrueAndLicenseStatus("commercially-usable")).thenReturn(List.of(ingredient("西兰花"), ingredient("番茄")));
        when(recipes.findAll()).thenReturn(List.of(recipe("西兰花鸡胸肉", "西兰花,鸡胸肉"), recipe("番茄牛腩", "番茄,牛腩"), recipe("西兰花豆腐汤", "西兰花,豆腐")));
        when(plans.save(any())).thenAnswer(i -> i.getArgument(0));
        LlmClient llm = mock(LlmClient.class); when(llm.isConfigured()).thenReturn(false);
        DailyMealPlanService service = new DailyMealPlanService(plans, recipes, mock(UserFoodPreferenceRepository.class), packs, seasonal,
                new DailyMealPlanValidator(new AllergenNormalizationService(llm, new ObjectMapper())), new ObjectMapper());
        var result = service.plan("u", LocalDate.of(2026, 9, 28));
        assertTrue(result.isPresent()); assertEquals(3, result.get().meals().size()); verify(plans).save(any());
    }
    @Test void replacesOnlyRequestedMealAndKeepsAValidPlan() {
        DailyMealPlanRepository plans = mock(DailyMealPlanRepository.class);
        RecipeRepository recipes = mock(RecipeRepository.class);
        NutritionKnowledgePackRepository packs = mock(NutritionKnowledgePackRepository.class);
        SeasonalIngredientRepository seasonal = mock(SeasonalIngredientRepository.class);
        var date = LocalDate.of(2026, 9, 28);
        var existing = new com.moodrecipe.backend.entity.DailyMealPlan(); existing.setOpenid("u"); existing.setPlanDate(date);
        existing.setPlanJson("[{\"name\":\"西兰花鸡胸肉\",\"ingredients\":\"西兰花,鸡胸肉\",\"steps\":\"炒熟即可\"},{\"name\":\"番茄牛腩\",\"ingredients\":\"番茄,牛腩\",\"steps\":\"炖熟即可\"},{\"name\":\"西兰花豆腐汤\",\"ingredients\":\"西兰花,豆腐\",\"steps\":\"煮熟即可\"}]");
        when(plans.findByOpenidAndPlanDate("u", date)).thenReturn(java.util.Optional.of(existing));
        when(packs.findByEnabledTrueAndLicenseStatus("commercially-usable")).thenReturn(List.of(pack()));
        when(seasonal.findByEnabledTrueAndLicenseStatus("commercially-usable")).thenReturn(List.of(ingredient("西兰花"), ingredient("番茄")));
        when(recipes.findAll()).thenReturn(List.of(recipe("西兰花鸡胸肉", "西兰花,鸡胸肉"), recipe("番茄牛腩", "番茄,牛腩"), recipe("西兰花豆腐汤", "西兰花,豆腐"), recipe("番茄炒蛋", "番茄,鸡蛋")));
        when(plans.save(any())).thenAnswer(i -> i.getArgument(0));
        LlmClient llm = mock(LlmClient.class); when(llm.isConfigured()).thenReturn(false);
        DailyMealPlanService service = new DailyMealPlanService(plans, recipes, mock(UserFoodPreferenceRepository.class), packs, seasonal, new DailyMealPlanValidator(new AllergenNormalizationService(llm, new ObjectMapper())), new ObjectMapper());
        var result = service.replace("u", date, 2);
        assertTrue(result.isPresent()); assertEquals("番茄炒蛋", result.get().meals().get(2).getName());
    }
    @Test void usesUnifiedProfileToRejectDislikedDishAndPreferLovedDish() {
        DailyMealPlanRepository plans = mock(DailyMealPlanRepository.class);
        RecipeRepository recipes = mock(RecipeRepository.class);
        NutritionKnowledgePackRepository packs = mock(NutritionKnowledgePackRepository.class);
        SeasonalIngredientRepository seasonal = mock(SeasonalIngredientRepository.class);
        AgentMemoryStore memory = mock(AgentMemoryStore.class);
        var date = LocalDate.of(2026, 9, 29);
        when(plans.findByOpenidAndPlanDate("u", date)).thenReturn(java.util.Optional.empty());
        when(packs.findByEnabledTrueAndLicenseStatus("commercially-usable")).thenReturn(List.of(pack()));
        when(seasonal.findByEnabledTrueAndLicenseStatus("commercially-usable"))
                .thenReturn(List.of(ingredient("西兰花"), ingredient("番茄"), ingredient("玉米")));
        when(recipes.findAll()).thenReturn(List.of(
                recipe("西兰花炒虾仁", "西兰花,虾仁"),
                recipe("西兰花鸡胸肉", "西兰花,鸡胸肉"),
                recipe("番茄牛腩", "番茄,牛腩"),
                recipe("玉米豆腐汤", "玉米,豆腐")));
        when(memory.profile("u", AgentMemoryStore.Scene.WEEKLY_PLAN)).thenReturn(new UserProfile(
                "u", null, null, List.of(), null, null, null, null, null, List.of(),
                List.of(), List.of("对虾"), List.of(), List.of("玉米豆腐汤"),
                List.of("西兰花炒虾仁"), List.of(), List.of(), Map.of(), null, false, 0, List.of()));
        when(plans.save(any())).thenAnswer(i -> i.getArgument(0));
        LlmClient llm = mock(LlmClient.class);
        when(llm.isConfigured()).thenReturn(false);
        DailyMealPlanService service = new DailyMealPlanService(plans, recipes,
                mock(UserFoodPreferenceRepository.class), packs, seasonal,
                new DailyMealPlanValidator(new AllergenNormalizationService(llm, new ObjectMapper())),
                new ObjectMapper(), memory);

        var result = service.plan("u", date);

        assertTrue(result.isPresent());
        assertEquals("玉米豆腐汤", result.get().meals().get(0).getName());
        assertTrue(result.get().meals().stream().noneMatch(item -> "西兰花炒虾仁".equals(item.getName())));
    }
    private NutritionKnowledgePack pack() { var p=new NutritionKnowledgePack(); p.setEnabled(true); p.setLicenseStatus("commercially-usable"); p.setVersion("1"); return p; }
    private SeasonalIngredient ingredient(String n) { var i=new SeasonalIngredient(); i.setName(n); i.setEnabled(true); i.setLicenseStatus("commercially-usable"); return i; }
    private Recipe recipe(String n,String i) { var r=new Recipe(); r.setName(n); r.setIngredients(i); r.setSteps("洗净后炒熟即可"); return r; }
}
