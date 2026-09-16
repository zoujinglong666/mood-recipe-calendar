package com.moodrecipe.backend.agent;

import com.moodrecipe.backend.entity.AgentMemoryFact;
import com.moodrecipe.backend.entity.PlanDishOutcome;
import com.moodrecipe.backend.repository.PlanDishOutcomeRepository;
import com.moodrecipe.backend.repository.RecipeInteractionRepository;
import com.moodrecipe.backend.repository.RecipeRepository;
import com.moodrecipe.backend.repository.UserFoodPreferenceRepository;
import com.moodrecipe.backend.repository.UserRecordRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 记忆不是"存字符串"，而是带置信度、来源、证据和时效的事实，这里锁住这些性质。 */
class AgentMemoryStoreTest {

    private static final String OPENID = "openid-memory-test";

    private final FakeMemoryFacts facts = new FakeMemoryFacts();
    private final PlanDishOutcomeRepository outcomes = mock(PlanDishOutcomeRepository.class);
    private final AgentMemoryStore store = new AgentMemoryStore(facts.repository(),
            mock(UserFoodPreferenceRepository.class), mock(UserRecordRepository.class),
            mock(RecipeInteractionRepository.class), mock(RecipeRepository.class), outcomes);

    @Test
    void remembersValueWithConfidenceSourceAndEvidence() {
        MemoryItem item = store.remember(AgentMemoryStore.RememberCommand.explicit(
                OPENID, AgentMemoryStore.KEY_CUISINE, "赣菜", "原话：我老家在抚州"));

        assertNotNull(item);
        assertEquals("赣菜", item.value());
        assertEquals(AgentMemoryStore.SRC_CHAT, item.source());
        assertEquals(0.9d, item.confidence(), 1e-6);
        assertEquals("赣菜", facts.get(OPENID, AgentMemoryStore.KEY_CUISINE).getMemoryValue());
        assertEquals("原话：我老家在抚州", facts.get(OPENID, AgentMemoryStore.KEY_CUISINE).getEvidence());
    }

    /** 随口的推断不能盖掉用户明确说过的事实。 */
    @Test
    void weakInferenceNeverOverridesConfidentFact() {
        store.remember(new AgentMemoryStore.RememberCommand(OPENID, AgentMemoryStore.KEY_SPICE,
                "中辣", 0.9, AgentMemoryStore.SRC_CHAT, "用户明确说过", null));
        store.remember(AgentMemoryStore.RememberCommand.inferred(OPENID, AgentMemoryStore.KEY_SPICE,
                "不吃辣", "模型从一句抱怨里推断"));

        assertEquals("中辣", facts.get(OPENID, AgentMemoryStore.KEY_SPICE).getMemoryValue());
    }

    @Test
    void repeatedConfirmationsRaiseConfidenceAndRefreshEvidence() {
        AgentMemoryStore.RememberCommand first = new AgentMemoryStore.RememberCommand(OPENID,
                AgentMemoryStore.KEY_PEOPLE, "3", 0.6, AgentMemoryStore.SRC_CHAT, "第一次提到三口人", null);
        double before = store.remember(first).confidence();
        double after = store.remember(new AgentMemoryStore.RememberCommand(OPENID,
                AgentMemoryStore.KEY_PEOPLE, "3", 0.6, AgentMemoryStore.SRC_CHAT, "又确认了一次", null)).confidence();

        assertTrue(after > before, "同一事实被再次确认后应该更可信：" + before + " -> " + after);
        assertEquals("又确认了一次", facts.get(OPENID, AgentMemoryStore.KEY_PEOPLE).getEvidence());
    }

    /** 有时效的事实（比如"这周家里有客人"）过期后必须自动遗忘。 */
    @Test
    void expiresFactsThatAreNoLongerTrue() {
        AgentMemoryFact seeded = facts.seed(FakeMemoryFacts.fact(OPENID, AgentMemoryStore.KEY_HOUSEHOLD,
                "这周有客人", 0.9, AgentMemoryStore.SRC_CHAT));
        seeded.setExpiresAt(LocalDateTime.now().minusDays(1));

        assertTrue(store.recall(OPENID, AgentMemoryStore.Scene.DIALOGUE, 10).isEmpty());
        assertEquals(AgentMemoryFact.STATUS_ARCHIVED,
                facts.get(OPENID, AgentMemoryStore.KEY_HOUSEHOLD).getStatus());
    }

    /** 衰减到阈值以下的事实同样不再进入提示词。 */
    @Test
    void forgetsFactsThatFadedBelowThreshold() {
        facts.seed(FakeMemoryFacts.fact(OPENID, AgentMemoryStore.KEY_BUDGET, "随便", 0.1,
                AgentMemoryStore.SRC_INFERRED));

        assertTrue(store.recall(OPENID, AgentMemoryStore.Scene.WEEKLY_PLAN, 10).isEmpty());
    }

    /** 学到的对话策略要回对话里，菜系亲和只服务点菜：场景隔离错了，学习闭环就是断的。 */
    @Test
    void recallsOnlyFactsRelevantToTheScene() {
        facts.seed(FakeMemoryFacts.fact(OPENID, AgentMemoryStore.KEY_SKIP_QUESTIONS, "ASK_PEOPLE", 0.8,
                AgentMemoryStore.SRC_LEARNED));
        facts.seed(FakeMemoryFacts.fact(OPENID, AgentMemoryStore.AFFINITY_PREFIX + "川菜", "1.0", 0.7,
                AgentMemoryStore.SRC_LEARNED));
        facts.seed(FakeMemoryFacts.fact(OPENID, AgentMemoryStore.KEY_PEOPLE, "3", 0.9,
                AgentMemoryStore.SRC_CHAT));

        assertTrue(keys(store, AgentMemoryStore.Scene.DIALOGUE).contains(AgentMemoryStore.KEY_SKIP_QUESTIONS));
        assertFalse(keys(store, AgentMemoryStore.Scene.DIALOGUE).contains(AgentMemoryStore.AFFINITY_PREFIX + "川菜"));
        assertTrue(keys(store, AgentMemoryStore.Scene.WEEKLY_PLAN).contains(AgentMemoryStore.AFFINITY_PREFIX + "川菜"));
        assertTrue(keys(store, AgentMemoryStore.Scene.COMPANION).contains(AgentMemoryStore.KEY_PEOPLE));
        assertFalse(keys(store, AgentMemoryStore.Scene.COMPANION).contains(AgentMemoryStore.KEY_SKIP_QUESTIONS));
    }

    /** 记忆要能被翻译成可执行的档案字段，否则"记住了"没有意义。 */
    @Test
    void turnsMemoryIntoUsableProfile() {
        facts.seed(FakeMemoryFacts.fact(OPENID, AgentMemoryStore.KEY_PEOPLE, "3", 0.9, AgentMemoryStore.SRC_CHAT));
        facts.seed(FakeMemoryFacts.fact(OPENID, AgentMemoryStore.KEY_SKIP_QUESTIONS, "ASK_PEOPLE,ASK_SPICE",
                0.8, AgentMemoryStore.SRC_LEARNED));
        facts.seed(FakeMemoryFacts.fact(OPENID, AgentLearningService.KEY_AVOID_DISHES, "红烧肉", 0.9,
                AgentMemoryStore.SRC_LEARNED));
        facts.seed(FakeMemoryFacts.fact(OPENID, AgentMemoryStore.KEY_MAX_MINUTES, "35", 0.8,
                AgentMemoryStore.SRC_LEARNED));
        facts.seed(FakeMemoryFacts.fact(OPENID, AgentMemoryStore.KEY_CUISINE, "赣菜", 0.9, AgentMemoryStore.SRC_CHAT));
        when(outcomes.findTop50ByOpenidOrderByUpdatedAtDesc(OPENID))
                .thenReturn(List.of(outcome(0, true), outcome(1, true)));

        UserProfile profile = store.profile(OPENID, AgentMemoryStore.Scene.WEEKLY_PLAN);

        assertEquals(Integer.valueOf(3), profile.people());
        assertEquals(Integer.valueOf(35), profile.maxCookingMinutes());
        assertTrue(profile.skipQuestions().contains("ASK_PEOPLE"));
        assertTrue(profile.skipQuestions().contains("ASK_SPICE"));
        assertTrue(profile.avoidDishes().contains("红烧肉"));
        assertTrue(profile.favoriteCuisines().contains("赣菜"));
        assertTrue(profile.preferSimple(), "两道菜都反馈太难，应该自动偏好省事做法");
        assertTrue(profile.summary().contains("用餐人数=3"));
    }

    /** 写回的"最近做过的菜"要能被档案读到，用来避免重复排菜。 */
    @Test
    void mergesRecentlyCookedDishesIntoProfile() {
        facts.seed(FakeMemoryFacts.fact(OPENID, AgentMemoryStore.KEY_RECENT_DISHES, "腊味合蒸,红烧肉", 0.8,
                AgentMemoryStore.SRC_BEHAVIOR));

        UserProfile profile = store.profile(OPENID, AgentMemoryStore.Scene.WEEKLY_PLAN);

        assertTrue(profile.recentDishes().contains("腊味合蒸"));
        assertTrue(profile.recentDishes().contains("红烧肉"));
    }

    private List<String> keys(AgentMemoryStore store, AgentMemoryStore.Scene scene) {
        return store.recall(OPENID, scene, 20).stream().map(MemoryItem::key).toList();
    }

    private PlanDishOutcome outcome(int dayIndex, boolean tooHard) {
        PlanDishOutcome outcome = new PlanDishOutcome();
        outcome.setOpenid(OPENID);
        outcome.setPlanId(1L);
        outcome.setDayIndex(dayIndex);
        outcome.setDishIndex(0);
        outcome.setDishName("第" + dayIndex + "天的菜");
        outcome.setTooHard(tooHard);
        return outcome;
    }
}
