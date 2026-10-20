package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.config.AppClock;
import com.moodrecipe.backend.entity.DailyMenu;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.entity.UserFoodPreference;
import com.moodrecipe.backend.model.UserProfile;
import com.moodrecipe.backend.repository.DailyMenuRepository;
import com.moodrecipe.backend.repository.RecipeInteractionRepository;
import com.moodrecipe.backend.repository.RecipeFavoriteRepository;
import com.moodrecipe.backend.repository.RecipeRepository;
import com.moodrecipe.backend.repository.UserFoodPreferenceRepository;
import com.moodrecipe.backend.repository.UserRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 今日卡「换一道」(refresh) 在预生成引擎接入后的接口测试：
 * 正常命中队列、队列缺失降级现场挑选、验收拒绝整列时降级、以及 SwapQueueBuilder.build 出口。
 */
class DailyMenuServiceSwapTest {

    /** 假构建器：按 scope 返回固定候选，模拟离线预生成队列。 */
    private static final class FakeBuilder implements SwapCandidateService.SwapQueueBuilder {
        final AtomicInteger builds = new AtomicInteger();
        volatile List<Recipe> toServe = List.of();

        @Override
        public String scope() {
            return SwapCandidateService.SCOPE_TODAY_BOARD;
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
        r.setDescription("家常菜");
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

    private DailyMenuService newService(DailyMenuRepository menus, RecipeRepository recipes,
                                        RecipeInteractionRepository interactions, UserRecordRepository records,
                                        UserFoodPreferenceRepository prefs, RecipePool pool, SwapCandidateService swap) {
        DailyMenuService svc = new DailyMenuService(menus, recipes, interactions, records, prefs,
                new AllergenNormalizationService(null, new ObjectMapper()), pool, mock(RecipeFavoriteRepository.class));
        if (swap != null) svc.setSwapCandidates(swap);
        return svc;
    }

    private SwapCandidateService swapService(FakeBuilder builder) {
        @SuppressWarnings("unchecked")
        ObjectProvider<SwapCandidateService.SwapQueueBuilder> providers = mock(ObjectProvider.class);
        when(providers.stream()).thenAnswer(inv -> Stream.of(builder));
        FeedbackLearningService learning = mock(FeedbackLearningService.class);
        when(learning.getProfile(any())).thenReturn(new UserProfile());
        UserFoodPreferenceRepository prefs = mock(UserFoodPreferenceRepository.class);
        when(prefs.findByOpenid(any())).thenReturn(Optional.empty());
        return new SwapCandidateService(providers, learning, prefs,
                new AllergenNormalizationService(null, new ObjectMapper()), mock(DailyMenuRepository.class));
    }

    /** 预热指定键的队列（消费方与 refresh 用同一 mealType 键位）。 */
    private void prewarm(SwapCandidateService swap, FakeBuilder builder, String openid) {
        String meal = DailyMenuService.mealTypeOf(java.time.LocalDateTime.now(AppClock.ZONE));
        for (int i = 0; i < 100; i++) {
            if (swap.next(SwapCandidateService.SCOPE_TODAY_BOARD, openid, SwapCandidateService.ANY_MOOD, meal, r -> true).isPresent())
                return;
            sleep20();
        }
    }

    /** 正常路径：队列命中时毫秒级返回，且返回的是队列里的候选（而非现场挑选的兜底菜）。 */
    @Test
    void refreshServesFromPrebuiltQueueWhenAvailable() {
        FakeBuilder builder = new FakeBuilder();
        builder.toServe = List.of(dish(1L, "队列菜A"), dish(2L, "队列菜B"));
        SwapCandidateService swap = swapService(builder);

        DailyMenuRepository menus = mock(DailyMenuRepository.class);
        when(menus.findByOpenidAndMenuDate(any(), any())).thenReturn(Optional.empty());
        when(menus.findByOpenidAndMenuDateGreaterThanEqual(any(), any())).thenReturn(List.of());
        RecipeRepository recipes = mock(RecipeRepository.class);
        when(recipes.findById(any())).thenAnswer(inv -> builder.toServe.stream()
                .filter(d -> d.getId().equals(inv.getArgument(0))).findFirst());
        RecipePool pool = new RecipePool(recipes);
        // 现场挑选兜底的候选与队列候选不同名，用于区分两条路径
        when(recipes.findAiWithImages()).thenReturn(List.of(dish(99L, "兜底菜")));
        when(recipes.findAll()).thenReturn(List.of(dish(99L, "兜底菜")));

        DailyMenuService svc = newService(menus, recipes, mock(RecipeInteractionRepository.class),
                mock(UserRecordRepository.class), mock(UserFoodPreferenceRepository.class), pool, swap);

        prewarm(swap, builder, "u1");
        Optional<Recipe> result = svc.refresh("u1").map(DailyMenuService.Board::recipe);
        assertTrue(result.isPresent());
        Set<String> queueNames = Set.of("队列菜A", "队列菜B");
        assertTrue(queueNames.contains(result.get().getName()));
    }

    /** 降级：未注入预生成引擎（swapCandidates 为空）时，退回现场挑选，仍能返回一道菜。 */
    @Test
    void refreshFallsBackToOnDemandPickWhenEngineAbsent() {
        DailyMenuRepository menus = mock(DailyMenuRepository.class);
        when(menus.findByOpenidAndMenuDate(any(), any())).thenReturn(Optional.empty());
        when(menus.findByOpenidAndMenuDateGreaterThanEqual(any(), any())).thenReturn(List.of());
        RecipeRepository recipes = mock(RecipeRepository.class);
        Recipe fallback = dish(99L, "兜底菜");
        when(recipes.findAiWithImages()).thenReturn(List.of(fallback));
        when(recipes.findAll()).thenReturn(List.of(fallback));
        when(recipes.findById(99L)).thenReturn(Optional.of(fallback));
        RecipePool pool = new RecipePool(recipes);

        DailyMenuService svc = newService(menus, recipes, mock(RecipeInteractionRepository.class),
                mock(UserRecordRepository.class), mock(UserFoodPreferenceRepository.class), pool, null);

        Optional<Recipe> result = svc.refresh("u1").map(DailyMenuService.Board::recipe);
        assertTrue(result.isPresent());
        assertEquals("兜底菜", result.get().getName());
    }

    /** 异常/边界：候选被验收全部拒绝时，队列返回 empty，refresh 退回现场挑选兜底。 */
    @Test
    void refreshFallsBackWhenQueueCandidateRejectedByAccept() {
        FakeBuilder builder = new FakeBuilder();
        // 唯一候选已被列进今日已推（excludeIds），验收必拒
        Recipe queued = dish(1L, "队列菜A");
        builder.toServe = List.of(queued);
        SwapCandidateService swap = swapService(builder);

        DailyMenuRepository menus = mock(DailyMenuRepository.class);
        when(menus.findByOpenidAndMenuDate(any(), any())).thenReturn(Optional.empty());
        DailyMenu alreadyShown = new DailyMenu();
        alreadyShown.setOpenid("u1");
        alreadyShown.setMenuDate(LocalDate.now());
        alreadyShown.setRecipeId(queued.getId());
        when(menus.findByOpenidAndMenuDateGreaterThanEqual(any(), any())).thenReturn(List.of(alreadyShown));
        RecipeRepository recipes = mock(RecipeRepository.class);
        Recipe fallback = dish(99L, "兜底菜");
        when(recipes.findAiWithImages()).thenReturn(List.of(fallback));
        when(recipes.findAll()).thenReturn(List.of(fallback));
        when(recipes.findById(99L)).thenReturn(Optional.of(fallback));
        RecipePool pool = new RecipePool(recipes);

        DailyMenuService svc = newService(menus, recipes, mock(RecipeInteractionRepository.class),
                mock(UserRecordRepository.class), mock(UserFoodPreferenceRepository.class), pool, swap);

        prewarm(swap, builder, "u1");
        Optional<Recipe> result = svc.refresh("u1").map(DailyMenuService.Board::recipe);
        assertTrue(result.isPresent());
        // 队列候选被拒 → 现场兜底，而非队列里的"队列菜A"
        assertEquals("兜底菜", result.get().getName());
    }

    /** SwapQueueBuilder.build 出口：返回排序候选，且今日已推过的菜（excludeIds）被硬过滤排除。 */
    @Test
    void buildExcludesAlreadyShownToday() {
        DailyMenuRepository menus = mock(DailyMenuRepository.class);
        Recipe excluded = dish(1L, "已推过");
        DailyMenu row = new DailyMenu();
        row.setOpenid("u1");
        row.setMenuDate(LocalDate.now());
        row.setRecipeId(excluded.getId());
        when(menus.findByOpenidAndMenuDateGreaterThanEqual(any(), any())).thenReturn(List.of(row));
        RecipeRepository recipes = mock(RecipeRepository.class);
        Recipe keep = dish(2L, "保留菜");
        when(recipes.findAiWithImages()).thenReturn(List.of(excluded, keep));
        when(recipes.findAll()).thenReturn(List.of(excluded, keep));
        RecipePool pool = new RecipePool(recipes);

        DailyMenuService svc = newService(menus, recipes, mock(RecipeInteractionRepository.class),
                mock(UserRecordRepository.class), mock(UserFoodPreferenceRepository.class), pool, null);

        List<Recipe> ranked = svc.build("u1", SwapCandidateService.ANY_MOOD, "LUNCH");
        assertTrue(ranked.stream().noneMatch(r -> "已推过".equals(r.getName())));
        assertTrue(ranked.stream().anyMatch(r -> "保留菜".equals(r.getName())));
    }

    /** SwapQueueBuilder.build 边界：候选池为空时返回空列表而非 null/NPE。 */
    @Test
    void buildReturnsEmptyWhenPoolEmpty() {
        DailyMenuRepository menus = mock(DailyMenuRepository.class);
        when(menus.findByOpenidAndMenuDateGreaterThanEqual(any(), any())).thenReturn(List.of());
        RecipeRepository recipes = mock(RecipeRepository.class);
        when(recipes.findAiWithImages()).thenReturn(List.of());
        when(recipes.findAll()).thenReturn(List.of());
        RecipePool pool = new RecipePool(recipes);

        DailyMenuService svc = newService(menus, recipes, mock(RecipeInteractionRepository.class),
                mock(UserRecordRepository.class), mock(UserFoodPreferenceRepository.class), pool, null);

        assertTrue(svc.build("u1", SwapCandidateService.ANY_MOOD, "LUNCH").isEmpty());
    }
}
