package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.model.InteractionRecordedEvent;
import com.moodrecipe.backend.model.UserProfile;
import com.moodrecipe.backend.repository.DailyMenuRepository;
import com.moodrecipe.backend.repository.UserFoodPreferenceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SwapCandidateServiceTest {

    private static final String TODAY = SwapCandidateService.SCOPE_TODAY_BOARD;
    private static final String MEAL = SwapCandidateService.SCOPE_MEAL_PLAN;

    /** 可控的假构建器：记录构建次数，按序返回候选；可配置抛异常以验证降级。 */
    private static final class FakeBuilder implements SwapCandidateService.SwapQueueBuilder {
        final AtomicInteger builds = new AtomicInteger();
        volatile List<Recipe> toServe = List.of();
        volatile boolean throwsOnBuild = false;
        private final String scope;

        FakeBuilder(String scope) {
            this.scope = scope;
        }

        @Override
        public String scope() {
            return scope;
        }

        @Override
        public List<Recipe> build(String openid, String mood, String mealType) {
            builds.incrementAndGet();
            if (throwsOnBuild) throw new RuntimeException("build failed");
            return toServe;
        }
    }

    private SwapCandidateService service(SwapCandidateService.SwapQueueBuilder... builders) {
        @SuppressWarnings("unchecked")
        ObjectProvider<SwapCandidateService.SwapQueueBuilder> providers = mock(ObjectProvider.class);
        when(providers.stream()).thenAnswer(inv -> Stream.of(builders));
        FeedbackLearningService learning = mock(FeedbackLearningService.class);
        when(learning.getProfile(any())).thenReturn(new UserProfile());
        UserFoodPreferenceRepository preferences = mock(UserFoodPreferenceRepository.class);
        when(preferences.findByOpenid(any())).thenReturn(Optional.empty());
        return new SwapCandidateService(providers, learning, preferences,
                new AllergenNormalizationService(null, new ObjectMapper()), mock(DailyMenuRepository.class));
    }

    private static Recipe dish(long id, String name) {
        Recipe recipe = new Recipe();
        recipe.setId(id);
        recipe.setName(name);
        return recipe;
    }

    private static void sleep20() {
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 轮询直到出队成功（异步补货完成的可靠信号），超时返回 empty。 */
    private static Optional<Recipe> awaitPick(SwapCandidateService service, String scope, Predicate<Recipe> accept) {
        Optional<Recipe> picked = Optional.empty();
        for (int i = 0; i < 100 && picked.isEmpty(); i++) {
            picked = service.next(scope, "u1", SwapCandidateService.ANY_MOOD, "LUNCH", accept);
            if (picked.isEmpty()) sleep20();
        }
        return picked;
    }

    private static boolean await(Supplier<Boolean> condition) {
        for (int i = 0; i < 100; i++) {
            if (condition.get()) return true;
            sleep20();
        }
        return false;
    }

    /** 正常路径：首调 miss（慢路径兜底），异步补货后同键换菜毫秒级出队，按序消费、耗尽返回 empty。 */
    @Test
    void missesFirstThenServesQueueInOrderUntilExhausted() {
        FakeBuilder builder = new FakeBuilder(TODAY);
        builder.toServe = List.of(dish(1L, "番茄炒蛋"), dish(2L, "清蒸鲈鱼"));
        SwapCandidateService service = service(builder);

        assertTrue(service.next(TODAY, "u1", SwapCandidateService.ANY_MOOD, "LUNCH", r -> true).isEmpty());
        assertEquals("番茄炒蛋", awaitPick(service, TODAY, r -> true).orElseThrow().getName());
        assertEquals("清蒸鲈鱼", awaitPick(service, TODAY, r -> true).orElseThrow().getName());
        // 耗尽且已消费候选不回灌：稳定 empty，消费方走慢路径
        assertTrue(service.next(TODAY, "u1", SwapCandidateService.ANY_MOOD, "LUNCH", r -> true).isEmpty());
        assertTrue(service.next(TODAY, "u1", SwapCandidateService.ANY_MOOD, "LUNCH", r -> true).isEmpty());
    }

    /** 正常路径：验收不通过的候选被丢弃并滑到下一个，不做无效返工。 */
    @Test
    void skipsCandidatesRejectedByAccept() {
        FakeBuilder builder = new FakeBuilder(TODAY);
        builder.toServe = List.of(dish(1L, "昨日推过"), dish(2L, "新鲜候选"));
        SwapCandidateService service = service(builder);

        assertTrue(service.next(TODAY, "u1", SwapCandidateService.ANY_MOOD, "LUNCH", r -> true).isEmpty());
        assertEquals("新鲜候选", awaitPick(service, TODAY, r -> !"昨日推过".equals(r.getName())).orElseThrow().getName());
    }

    /** 关键契约：补货重建不回灌已消费候选——"刚换掉的菜"不会在队列里复活到队头。 */
    @Test
    void refillDoesNotReserveConsumedCandidates() {
        FakeBuilder builder = new FakeBuilder(TODAY);
        builder.toServe = List.of(dish(1L, "番茄炒蛋"), dish(2L, "清蒸鲈鱼"));
        SwapCandidateService service = service(builder);

        assertTrue(service.next(TODAY, "u1", SwapCandidateService.ANY_MOOD, "LUNCH", r -> true).isEmpty());
        assertEquals("番茄炒蛋", awaitPick(service, TODAY, r -> true).orElseThrow().getName());
        // 消费触发补货，但番茄炒蛋已消费不得复活
        assertTrue(await(() -> builder.builds.get() > 1));
        assertEquals("清蒸鲈鱼", awaitPick(service, TODAY, r -> true).orElseThrow().getName());
    }

    /** 作用域路由：同键只命中同 scope 的构建器，不串味到另一消费方。 */
    @Test
    void routesBuildToMatchingScopeOnly() {
        FakeBuilder todayBuilder = new FakeBuilder(TODAY);
        todayBuilder.toServe = List.of(dish(1L, "今日卡菜"));
        FakeBuilder mealBuilder = new FakeBuilder(MEAL);
        mealBuilder.toServe = List.of(dish(2L, "三餐菜"));
        SwapCandidateService service = service(todayBuilder, mealBuilder);

        assertTrue(service.next(TODAY, "u1", SwapCandidateService.ANY_MOOD, "LUNCH", r -> true).isEmpty());
        assertTrue(await(() -> todayBuilder.builds.get() > 0));
        // 今日卡队列只调用了 today 构建器，三餐构建器一次都没被触发
        assertEquals(0, mealBuilder.builds.get());
        assertEquals("今日卡菜", awaitPick(service, TODAY, r -> true).orElseThrow().getName());
    }

    /** 关键契约：SHOWN（曝光）不失效队列（否则每次换菜都被打穿，永远回不到毫秒级）；
     *  LIKE 等有意义交互立刻换签名重建——个性化更新在下一次换菜生效。
     *  候选给足 30 道（> QUEUE_SIZE 且不触发补货阈值），把补货因素从断言中隔离出去。 */
    @Test
    void shownKeepsQueueButMeaningfulInteractionRebuildsIt() throws InterruptedException {
        FakeBuilder builder = new FakeBuilder(TODAY);
        List<Recipe> dishes = new java.util.ArrayList<>();
        for (long i = 1; i <= 30; i++) dishes.add(dish(i, "候选" + i));
        builder.toServe = dishes;
        SwapCandidateService service = service(builder);

        assertTrue(service.next(TODAY, "u1", SwapCandidateService.ANY_MOOD, "LUNCH", r -> true).isEmpty());
        // SHOWN：队列原样保留，照常出队
        service.onInteraction(new InteractionRecordedEvent("u1", "SHOWN", 9L));
        assertEquals("候选1", awaitPick(service, TODAY, r -> true).orElseThrow().getName());

        int buildsAfterWarmup = builder.builds.get();
        Thread.sleep(300);
        assertEquals(buildsAfterWarmup, builder.builds.get());

        // LIKE：画像变 → 签名变 → 旧队列立即作废并自动重建，随后恢复毫秒级出队
        service.onInteraction(new InteractionRecordedEvent("u1", "LIKE", 9L));
        assertTrue(await(() -> builder.builds.get() > buildsAfterWarmup));
        assertEquals("候选1", awaitPick(service, TODAY, r -> true).orElseThrow().getName());
    }

    /** 边界：候选池为空（如冷启动尚未生成）→ 每次 next 都返回 empty，永不抛异常、永不死循环。 */
    @Test
    void emptyPoolAlwaysMisses() {
        FakeBuilder builder = new FakeBuilder(TODAY);
        builder.toServe = List.of();
        SwapCandidateService service = service(builder);

        assertTrue(service.next(TODAY, "u1", SwapCandidateService.ANY_MOOD, "LUNCH", r -> true).isEmpty());
        assertTrue(await(() -> builder.builds.get() > 0));
        assertTrue(service.next(TODAY, "u1", SwapCandidateService.ANY_MOOD, "LUNCH", r -> true).isEmpty());
    }

    /** 边界：accept 全盘拒绝 → 队列有候选也始终返回 empty，消费方走慢路径兜底。 */
    @Test
    void acceptRejectsAllAlwaysMisses() {
        FakeBuilder builder = new FakeBuilder(TODAY);
        builder.toServe = List.of(dish(1L, "A"), dish(2L, "B"));
        SwapCandidateService service = service(builder);

        assertTrue(service.next(TODAY, "u1", SwapCandidateService.ANY_MOOD, "LUNCH", r -> true).isEmpty());
        assertTrue(await(() -> builder.builds.get() > 0));
        assertTrue(service.next(TODAY, "u1", SwapCandidateService.ANY_MOOD, "LUNCH", r -> false).isEmpty());
    }

    /** 异常：构建器抛异常 → 降级为 empty（保留旧队列），异常不外泄打穿换菜主流程。 */
    @Test
    void builderThrowsDegradesToEmptyWithoutPropagating() {
        FakeBuilder builder = new FakeBuilder(TODAY);
        builder.toServe = List.of(dish(1L, "A"));
        builder.throwsOnBuild = true;
        SwapCandidateService service = service(builder);

        assertTrue(service.next(TODAY, "u1", SwapCandidateService.ANY_MOOD, "LUNCH", r -> true).isEmpty());
        assertTrue(service.next(TODAY, "u1", SwapCandidateService.ANY_MOOD, "LUNCH", r -> true).isEmpty());
    }

    /** 异常：构建器流获取抛异常 → 同样降级为空，不直接抛。 */
    @Test
    void providerStreamThrowsDegradesToEmpty() {
        @SuppressWarnings("unchecked")
        ObjectProvider<SwapCandidateService.SwapQueueBuilder> providers = mock(ObjectProvider.class);
        when(providers.stream()).thenThrow(new RuntimeException("stream boom"));
        FeedbackLearningService learning = mock(FeedbackLearningService.class);
        when(learning.getProfile(any())).thenReturn(new UserProfile());
        UserFoodPreferenceRepository preferences = mock(UserFoodPreferenceRepository.class);
        when(preferences.findByOpenid(any())).thenReturn(Optional.empty());
        SwapCandidateService service = new SwapCandidateService(providers, learning, preferences,
                new AllergenNormalizationService(null, new ObjectMapper()), mock(DailyMenuRepository.class));

        assertTrue(service.next(TODAY, "u1", SwapCandidateService.ANY_MOOD, "LUNCH", r -> true).isEmpty());
        assertTrue(service.next(TODAY, "u1", SwapCandidateService.ANY_MOOD, "LUNCH", r -> true).isEmpty());
    }

    /** 异常：画像计算抛异常 → 签名退化为 openid+日期，队列仍正常服务，不阻断换菜。 */
    @Test
    void learningThrowsFallsBackToSignatureAndStillServes() {
        FakeBuilder builder = new FakeBuilder(TODAY);
        builder.toServe = List.of(dish(1L, "A"));
        @SuppressWarnings("unchecked")
        ObjectProvider<SwapCandidateService.SwapQueueBuilder> providers = mock(ObjectProvider.class);
        when(providers.stream()).thenAnswer(inv -> Stream.of(builder));
        FeedbackLearningService learning = mock(FeedbackLearningService.class);
        when(learning.getProfile(any())).thenThrow(new RuntimeException("profile boom"));
        UserFoodPreferenceRepository preferences = mock(UserFoodPreferenceRepository.class);
        when(preferences.findByOpenid(any())).thenReturn(Optional.empty());
        SwapCandidateService service = new SwapCandidateService(providers, learning, preferences,
                new AllergenNormalizationService(null, new ObjectMapper()), mock(DailyMenuRepository.class));

        assertTrue(service.next(TODAY, "u1", SwapCandidateService.ANY_MOOD, "LUNCH", r -> true).isEmpty());
        assertEquals("A", awaitPick(service, TODAY, r -> true).orElseThrow().getName());
    }

    /** 边界：队列超龄（ENTRY_TTL_MS）后下次消费触发重建，且仍能正常出队（陈旧队列被废弃而非复用）。 */
    @Test
    void staleEntryIsRebuiltOnNext() throws Exception {
        FakeBuilder builder = new FakeBuilder(TODAY);
        builder.toServe = List.of(dish(1L, "A"), dish(2L, "B"));
        SwapCandidateService service = service(builder);

        assertTrue(service.next(TODAY, "u1", SwapCandidateService.ANY_MOOD, "LUNCH", r -> true).isEmpty());
        assertTrue(await(() -> builder.builds.get() > 0));
        // 等首轮异步重建彻底结束（building 复位）再强制超龄，避免 CAS 与 finally 复位之间的竞态
        assertTrue(await(() -> isIdle(service)));
        forceExpire(service);

        int before = builder.builds.get();
        // 用 ensure() 触发超龄重建（不消费队列），随后才出队
        service.ensure(TODAY, "u1", SwapCandidateService.ANY_MOOD, "LUNCH");
        assertTrue(await(() -> builder.builds.get() > before), "超龄应触发重建");
        assertEquals("A", awaitPick(service, TODAY, r -> true).orElseThrow().getName());
    }

    /** 异常：未知 action 的交互不抛异常（不阻断事件总线上的其它监听器）。 */
    @Test
    void unknownInteractionActionDoesNotThrow() {
        FakeBuilder builder = new FakeBuilder(TODAY);
        builder.toServe = List.of(dish(1L, "A"));
        SwapCandidateService service = service(builder);
        // onInteraction 对任何 action 都吞掉内部异常，不外泄
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> service.onInteraction(new InteractionRecordedEvent("u1", "UNKNOWN_ACTION", 9L)));
    }

    /** 把缓存里的所有队列标记成超龄，逼出下次消费的重建分支。 */
    private static void forceExpire(SwapCandidateService service) throws Exception {
        Field f = SwapCandidateService.class.getDeclaredField("queues");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) f.get(service);
        for (Object entry : map.values()) {
            Field builtAt = entry.getClass().getDeclaredField("builtAt");
            builtAt.setAccessible(true);
            builtAt.set(entry, 1L);
        }
    }

    /** 所有队列是否都已完成异步重建（building 复位），用于消除 CAS 与 finally 复位之间的测试竞态。 */
    private static boolean isIdle(SwapCandidateService service) {
        try {
            Field f = SwapCandidateService.class.getDeclaredField("queues");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) f.get(service);
            for (Object entry : map.values()) {
                Field building = entry.getClass().getDeclaredField("building");
                building.setAccessible(true);
                if (((java.util.concurrent.atomic.AtomicBoolean) building.get(entry)).get()) return false;
            }
            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
