package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.agent.LlmClient;
import com.moodrecipe.backend.entity.DailyMealPlan;
import com.moodrecipe.backend.entity.NutritionKnowledgePack;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.entity.SeasonalIngredient;
import com.moodrecipe.backend.repository.DailyMealPlanRepository;
import com.moodrecipe.backend.repository.DailyMenuRepository;
import com.moodrecipe.backend.repository.NutritionKnowledgePackRepository;
import com.moodrecipe.backend.repository.RecipeRepository;
import com.moodrecipe.backend.repository.SeasonalIngredientRepository;
import com.moodrecipe.backend.repository.UserFoodPreferenceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 三餐计划「换菜」(replace) 在预生成引擎接入后的接口测试：
 * 正常命中队列、验收逐位跳过不合格候选、索引边界、计划缺失/数据损坏等异常场景，以及 SwapQueueBuilder.build 出口。
 */
class DailyMealPlanServiceSwapTest {

    private static final String OPENID = "u";
    private static final LocalDate DATE = LocalDate.of(2026, 9, 28);

    private static final class FakeBuilder implements SwapCandidateService.SwapQueueBuilder {
        final AtomicInteger builds = new AtomicInteger();
        volatile List<Recipe> toServe = List.of();

        @Override
        public String scope() {
            return SwapCandidateService.SCOPE_MEAL_PLAN;
        }

        @Override
        public List<Recipe> build(String openid, String mood, String mealType) {
            builds.incrementAndGet();
            return toServe;
        }
    }

    private static Recipe dish(long id, String name) {
        Recipe r = new Recipe();
        r.setId(id);
        r.setName(name);
        r.setIngredients("食材若干");
        r.setSteps("洗净炒熟即可");
        return r;
    }

    private static void sleep20() {
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private DailyMealPlanValidator validator() {
        LlmClient llm = mock(LlmClient.class);
        when(llm.isConfigured()).thenReturn(false);
        return new DailyMealPlanValidator(new AllergenNormalizationService(llm, new ObjectMapper()));
    }

    private DailyMealPlanService service(DailyMealPlanRepository plans, RecipeRepository recipes,
                                         UserFoodPreferenceRepository prefs,
                                         NutritionKnowledgePackRepository packs,
                                         SeasonalIngredientRepository seasonal,
                                         SwapCandidateService swap) {
        DailyMealPlanService svc = new DailyMealPlanService(plans, recipes, prefs, packs, seasonal,
                validator(), new ObjectMapper(), null, new RecipePool(recipes), null);
        if (swap != null) svc.setSwapCandidates(swap);
        return svc;
    }

    private SwapCandidateService swapService(FakeBuilder builder) {
        @SuppressWarnings("unchecked")
        ObjectProvider<SwapCandidateService.SwapQueueBuilder> providers = mock(ObjectProvider.class);
        when(providers.stream()).thenAnswer(inv -> Stream.of(builder));
        FeedbackLearningService learning = mock(FeedbackLearningService.class);
        when(learning.getProfile(any())).thenReturn(new com.moodrecipe.backend.model.UserProfile());
        UserFoodPreferenceRepository prefs = mock(UserFoodPreferenceRepository.class);
        when(prefs.findByOpenid(any())).thenReturn(Optional.empty());
        return new SwapCandidateService(providers, learning, prefs,
                new AllergenNormalizationService(null, new ObjectMapper()), mock(DailyMenuRepository.class));
    }

    private DailyMealPlan existingPlan(String... names) {
        DailyMealPlan plan = new DailyMealPlan();
        plan.setOpenid(OPENID);
        plan.setPlanDate(DATE);
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < names.length; i++) {
            if (i > 0) json.append(",");
            json.append("{\"name\":\"").append(names[i]).append("\",\"ingredients\":\"x\",\"steps\":\"y\"}");
        }
        json.append("]");
        plan.setPlanJson(json.toString());
        return plan;
    }

    private void prewarm(SwapCandidateService swap, String openid, String mealType) {
        for (int i = 0; i < 100; i++) {
            if (swap.next(SwapCandidateService.SCOPE_MEAL_PLAN, openid, SwapCandidateService.ANY_MOOD, mealType, r -> true).isPresent())
                return;
            sleep20();
        }
    }

    /** 正常路径：队列命中时换掉指定餐位，其余两餐保持不变，返回的是队列候选。 */
    @Test
    void replaceSwapsRequestedMealFromQueue() {
        FakeBuilder builder = new FakeBuilder();
        builder.toServe = List.of(dish(10L, "候选X"), dish(11L, "候选Y"), dish(12L, "候选Z"));
        SwapCandidateService swap = swapService(builder);

        DailyMealPlanRepository plans = mock(DailyMealPlanRepository.class);
        DailyMealPlan plan = existingPlan("早餐粥", "午餐饭", "晚餐汤");
        when(plans.findByOpenidAndPlanDate(OPENID, DATE)).thenReturn(Optional.of(plan));
        when(plans.save(any())).thenAnswer(i -> i.getArgument(0));
        NutritionKnowledgePackRepository packs = mock(NutritionKnowledgePackRepository.class);
        when(packs.findByEnabledTrueAndLicenseStatus(any())).thenReturn(List.of());
        SeasonalIngredientRepository seasonal = mock(SeasonalIngredientRepository.class);
        when(seasonal.findByEnabledTrueAndLicenseStatus(any())).thenReturn(List.of());

        DailyMealPlanService svc = service(plans, mock(RecipeRepository.class),
                mock(UserFoodPreferenceRepository.class), packs, seasonal, swap);

        prewarm(swap, OPENID, "MAIN");
        var result = svc.replace(OPENID, DATE, 1);
        assertTrue(result.isPresent());
        assertEquals("早餐粥", result.get().meals().get(0).getName());
        assertEquals("晚餐汤", result.get().meals().get(2).getName());
        assertTrue(List.of("候选X", "候选Y", "候选Z").contains(result.get().meals().get(1).getName()));
    }

    /** 验收逐位跳过：候选与现有某餐重名被拒，自动滑到下一个合格候选。 */
    @Test
    void replaceSkipsCandidateRejectedByValidation() {
        FakeBuilder builder = new FakeBuilder();
        // 队首"早餐粥"与现有早餐重名，必须被验收拒绝并滑到"候选X"
        builder.toServe = List.of(dish(10L, "早餐粥"), dish(11L, "候选X"));
        SwapCandidateService swap = swapService(builder);

        DailyMealPlanRepository plans = mock(DailyMealPlanRepository.class);
        DailyMealPlan plan = existingPlan("早餐粥", "午餐饭", "晚餐汤");
        when(plans.findByOpenidAndPlanDate(OPENID, DATE)).thenReturn(Optional.of(plan));
        when(plans.save(any())).thenAnswer(i -> i.getArgument(0));
        NutritionKnowledgePackRepository packs = mock(NutritionKnowledgePackRepository.class);
        when(packs.findByEnabledTrueAndLicenseStatus(any())).thenReturn(List.of());
        SeasonalIngredientRepository seasonal = mock(SeasonalIngredientRepository.class);
        when(seasonal.findByEnabledTrueAndLicenseStatus(any())).thenReturn(List.of());

        DailyMealPlanService svc = service(plans, mock(RecipeRepository.class),
                mock(UserFoodPreferenceRepository.class), packs, seasonal, swap);

        prewarm(swap, OPENID, "MAIN");
        var result = svc.replace(OPENID, DATE, 1);
        assertTrue(result.isPresent());
        assertEquals("候选X", result.get().meals().get(1).getName());
    }

    /** 降级：未注入预生成引擎时，退回现场排序，仍能换出一道合格菜。 */
    @Test
    void replaceFallsBackToOnDemandRankingWhenEngineAbsent() {
        DailyMealPlanRepository plans = mock(DailyMealPlanRepository.class);
        DailyMealPlan plan = existingPlan("早餐粥", "午餐饭", "晚餐汤");
        when(plans.findByOpenidAndPlanDate(OPENID, DATE)).thenReturn(Optional.of(plan));
        when(plans.save(any())).thenAnswer(i -> i.getArgument(0));

        RecipeRepository recipes = mock(RecipeRepository.class);
        Recipe fallback = dish(99L, "现场候选");
        when(recipes.findAll()).thenReturn(List.of(fallback));
        NutritionKnowledgePackRepository packs = mock(NutritionKnowledgePackRepository.class);
        when(packs.findByEnabledTrueAndLicenseStatus(any())).thenReturn(List.of());
        SeasonalIngredientRepository seasonal = mock(SeasonalIngredientRepository.class);
        when(seasonal.findByEnabledTrueAndLicenseStatus(any())).thenReturn(List.of());

        DailyMealPlanService svc = service(plans, recipes, mock(UserFoodPreferenceRepository.class), packs, seasonal, null);

        var result = svc.replace(OPENID, DATE, 1);
        assertTrue(result.isPresent());
        assertEquals("现场候选", result.get().meals().get(1).getName());
        assertEquals("早餐粥", result.get().meals().get(0).getName());
    }

    /** 边界：餐序号越界（-1 / 3）直接返回 empty，不触碰存储。 */
    @Test
    void replaceRejectsOutOfRangeMealIndex() {
        DailyMealPlanService svc = service(mock(DailyMealPlanRepository.class), mock(RecipeRepository.class),
                mock(UserFoodPreferenceRepository.class), mock(NutritionKnowledgePackRepository.class),
                mock(SeasonalIngredientRepository.class), null);
        assertTrue(svc.replace(OPENID, DATE, -1).isEmpty());
        assertTrue(svc.replace(OPENID, DATE, 3).isEmpty());
    }

    /** 异常：计划不存在 → empty（不抛 NPE）。 */
    @Test
    void replaceReturnsEmptyWhenPlanMissing() {
        DailyMealPlanRepository plans = mock(DailyMealPlanRepository.class);
        when(plans.findByOpenidAndPlanDate(OPENID, DATE)).thenReturn(Optional.empty());
        DailyMealPlanService svc = service(plans, mock(RecipeRepository.class),
                mock(UserFoodPreferenceRepository.class), mock(NutritionKnowledgePackRepository.class),
                mock(SeasonalIngredientRepository.class), null);
        assertTrue(svc.replace(OPENID, DATE, 1).isEmpty());
    }

    /** 异常：planJson 不是三道菜（两道）→ empty。 */
    @Test
    void replaceReturnsEmptyWhenPlanNotThreeMeals() {
        DailyMealPlanRepository plans = mock(DailyMealPlanRepository.class);
        when(plans.findByOpenidAndPlanDate(OPENID, DATE)).thenReturn(Optional.of(existingPlan("早餐粥", "午餐饭")));
        DailyMealPlanService svc = service(plans, mock(RecipeRepository.class),
                mock(UserFoodPreferenceRepository.class), mock(NutritionKnowledgePackRepository.class),
                mock(SeasonalIngredientRepository.class), null);
        assertTrue(svc.replace(OPENID, DATE, 1).isEmpty());
    }

    /** 异常：planJson 损坏（非法 JSON）→ 捕获异常返回 empty，不向上抛。 */
    @Test
    void replaceReturnsEmptyWhenPlanJsonCorrupted() {
        DailyMealPlan plan = new DailyMealPlan();
        plan.setOpenid(OPENID);
        plan.setPlanDate(DATE);
        plan.setPlanJson("这不是合法的json??");
        DailyMealPlanRepository plans = mock(DailyMealPlanRepository.class);
        when(plans.findByOpenidAndPlanDate(OPENID, DATE)).thenReturn(Optional.of(plan));
        DailyMealPlanService svc = service(plans, mock(RecipeRepository.class),
                mock(UserFoodPreferenceRepository.class), mock(NutritionKnowledgePackRepository.class),
                mock(SeasonalIngredientRepository.class), null);
        assertTrue(svc.replace(OPENID, DATE, 1).isEmpty());
    }

    /** SwapQueueBuilder.build 出口：非早餐约束返回全池；早餐约束只保留打了"早餐"标签的菜。 */
    @Test
    void buildRespectsBreakfastOnlyFilter() {
        RecipeRepository recipes = mock(RecipeRepository.class);
        Recipe normal = dish(1L, "红烧肉");
        Recipe breakfast = dish(2L, "皮蛋瘦肉粥");
        breakfast.setMoodTags("早餐");
        when(recipes.findAll()).thenReturn(List.of(normal, breakfast));
        RecipePool pool = new RecipePool(recipes);
        NutritionKnowledgePackRepository packs = mock(NutritionKnowledgePackRepository.class);
        when(packs.findByEnabledTrueAndLicenseStatus(any())).thenReturn(List.of());
        SeasonalIngredientRepository seasonal = mock(SeasonalIngredientRepository.class);
        when(seasonal.findByEnabledTrueAndLicenseStatus(any())).thenReturn(List.of());

        DailyMealPlanService svc = service(mock(DailyMealPlanRepository.class), recipes,
                mock(UserFoodPreferenceRepository.class), packs, seasonal, null);

        List<Recipe> all = svc.build(OPENID, SwapCandidateService.ANY_MOOD, "MAIN");
        assertTrue(all.stream().anyMatch(r -> "红烧肉".equals(r.getName())));
        assertTrue(all.stream().anyMatch(r -> "皮蛋瘦肉粥".equals(r.getName())));

        List<Recipe> breakfastOnly = svc.build(OPENID, SwapCandidateService.ANY_MOOD, "BREAKFAST");
        assertTrue(breakfastOnly.stream().allMatch(r -> "皮蛋瘦肉粥".equals(r.getName())));
        assertTrue(breakfastOnly.stream().noneMatch(r -> "红烧肉".equals(r.getName())));
    }

    /** SwapQueueBuilder.build 边界：候选池为空时返回空列表而非 null/NPE。 */
    @Test
    void buildReturnsEmptyWhenPoolEmpty() {
        RecipeRepository recipes = mock(RecipeRepository.class);
        when(recipes.findAll()).thenReturn(List.of());
        RecipePool pool = new RecipePool(recipes);
        NutritionKnowledgePackRepository packs = mock(NutritionKnowledgePackRepository.class);
        when(packs.findByEnabledTrueAndLicenseStatus(any())).thenReturn(List.of());
        SeasonalIngredientRepository seasonal = mock(SeasonalIngredientRepository.class);
        when(seasonal.findByEnabledTrueAndLicenseStatus(any())).thenReturn(List.of());
        DailyMealPlanService svc = service(mock(DailyMealPlanRepository.class), recipes,
                mock(UserFoodPreferenceRepository.class), packs, seasonal, null);
        assertTrue(svc.build(OPENID, SwapCandidateService.ANY_MOOD, "MAIN").isEmpty());
    }
}
