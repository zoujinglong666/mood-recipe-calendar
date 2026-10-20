package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.model.InteractionRecordedEvent;
import com.moodrecipe.backend.model.UserProfile;
import com.moodrecipe.backend.repository.DailyMenuRepository;
import com.moodrecipe.backend.repository.UserFoodPreferenceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
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

    private static final String SCOPE = SwapCandidateService.SCOPE_TODAY_BOARD;

    /** 可控的假构建器：记录构建次数，按序返回候选。 */
    private static final class FakeBuilder implements SwapCandidateService.SwapQueueBuilder {
        final AtomicInteger builds = new AtomicInteger();
        volatile List<Recipe> toServe = List.of();

        @Override
        public String scope() {
            return SCOPE;
        }

        @Override
        public List<Recipe> build(String openid, String mood, String mealType) {
            builds.incrementAndGet();
            return toServe;
        }
    }

    private SwapCandidateService service(FakeBuilder builder) {
        @SuppressWarnings("unchecked")
        ObjectProvider<SwapCandidateService.SwapQueueBuilder> providers = mock(ObjectProvider.class);
        // stream() 每次重建都会调用，必须返回新流（thenReturn 复用一次性 Stream 会让第二次重建静默失败）
        when(providers.stream()).thenAnswer(inv -> Stream.of(builder));
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
    private static Optional<Recipe> awaitPick(SwapCandidateService service, Predicate<Recipe> accept) {
        Optional<Recipe> picked = Optional.empty();
        for (int i = 0; i < 100 && picked.isEmpty(); i++) {
            picked = service.next(SCOPE, "u1", SwapCandidateService.ANY_MOOD, "LUNCH", accept);
            if (picked.isEmpty()) sleep20();
        }
        return picked;
    }

    /** 契约：首调 miss（走慢路径兜底），异步补货后同键换菜毫秒级出队，按序消费、耗尽返回 empty。 */
    @Test
    void missesFirstThenServesQueueInOrderUntilExhausted() {
        FakeBuilder builder = new FakeBuilder();
        builder.toServe = List.of(dish(1L, "番茄炒蛋"), dish(2L, "清蒸鲈鱼"));
        SwapCandidateService service = service(builder);

        assertTrue(service.next(SCOPE, "u1", SwapCandidateService.ANY_MOOD, "LUNCH", r -> true).isEmpty());
        assertEquals("番茄炒蛋", awaitPick(service, r -> true).orElseThrow().getName());
        assertEquals("清蒸鲈鱼", awaitPick(service, r -> true).orElseThrow().getName());
        // 耗尽且已消费候选不回灌：稳定 empty，消费方走慢路径
        assertTrue(service.next(SCOPE, "u1", SwapCandidateService.ANY_MOOD, "LUNCH", r -> true).isEmpty());
        assertTrue(service.next(SCOPE, "u1", SwapCandidateService.ANY_MOOD, "LUNCH", r -> true).isEmpty());
    }

    /** 契约：验收不通过的候选被丢弃并滑到下一个，不做无效返工。 */
    @Test
    void skipsCandidatesRejectedByAccept() {
        FakeBuilder builder = new FakeBuilder();
        builder.toServe = List.of(dish(1L, "昨日推过"), dish(2L, "新鲜候选"));
        SwapCandidateService service = service(builder);

        assertTrue(service.next(SCOPE, "u1", SwapCandidateService.ANY_MOOD, "LUNCH", r -> true).isEmpty());
        assertEquals("新鲜候选", awaitPick(service, r -> !"昨日推过".equals(r.getName())).orElseThrow().getName());
    }

    /** 契约：补货重建不回灌已消费候选——"刚换掉的菜"不会在队列里复活到队头。 */
    @Test
    void refillDoesNotReserveConsumedCandidates() {
        FakeBuilder builder = new FakeBuilder();
        builder.toServe = List.of(dish(1L, "番茄炒蛋"), dish(2L, "清蒸鲈鱼"));
        SwapCandidateService service = service(builder);

        assertTrue(service.next(SCOPE, "u1", SwapCandidateService.ANY_MOOD, "LUNCH", r -> true).isEmpty());
        assertEquals("番茄炒蛋", awaitPick(service, r -> true).orElseThrow().getName());
        // 消费触发补货，但番茄炒蛋已消费不得复活
        assertTrue(await(() -> builder.builds.get() > 1));
        assertEquals("清蒸鲈鱼", awaitPick(service, r -> true).orElseThrow().getName());
    }

    /** 关键契约：SHOWN（曝光）不失效队列（否则每次换菜都被打穿，永远回不到毫秒级）；
     *  LIKE 等有意义交互立刻换签名重建——个性化更新在下一次换菜生效。
     *  候选给足 30 道（> QUEUE_SIZE 且不触发补货阈值），把补货因素从断言中隔离出去。 */
    @Test
    void shownKeepsQueueButMeaningfulInteractionRebuildsIt() throws InterruptedException {
        FakeBuilder builder = new FakeBuilder();
        List<Recipe> dishes = new java.util.ArrayList<>();
        for (long i = 1; i <= 30; i++) dishes.add(dish(i, "候选" + i));
        builder.toServe = dishes;
        SwapCandidateService service = service(builder);

        assertTrue(service.next(SCOPE, "u1", SwapCandidateService.ANY_MOOD, "LUNCH", r -> true).isEmpty());
        // SHOWN：队列原样保留，照常出队
        service.onInteraction(new InteractionRecordedEvent("u1", "SHOWN", 9L));
        assertEquals("候选1", awaitPick(service, r -> true).orElseThrow().getName());

        int buildsAfterWarmup = builder.builds.get();
        Thread.sleep(300);
        assertEquals(buildsAfterWarmup, builder.builds.get());

        // LIKE：画像变 → 签名变 → 旧队列立即作废并自动重建，随后恢复毫秒级出队
        service.onInteraction(new InteractionRecordedEvent("u1", "LIKE", 9L));
        assertTrue(await(() -> builder.builds.get() > buildsAfterWarmup));
        assertEquals("候选1", awaitPick(service, r -> true).orElseThrow().getName());
    }

    private static boolean await(Supplier<Boolean> condition) {
        for (int i = 0; i < 100; i++) {
            if (condition.get()) return true;
            sleep20();
        }
        return false;
    }
}
