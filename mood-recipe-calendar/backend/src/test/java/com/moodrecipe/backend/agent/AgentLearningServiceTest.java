package com.moodrecipe.backend.agent;

import com.moodrecipe.backend.entity.PlanDishOutcome;
import com.moodrecipe.backend.entity.UserRecord;
import com.moodrecipe.backend.repository.PlanDishOutcomeRepository;
import com.moodrecipe.backend.repository.RecipeInteractionRepository;
import com.moodrecipe.backend.repository.RecipeRepository;
import com.moodrecipe.backend.repository.UserFoodPreferenceRepository;
import com.moodrecipe.backend.repository.UserRecordRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 学习闭环：真实结果必须改变下一次的行为，而不是只把评分存起来。 */
class AgentLearningServiceTest {

    private static final String OPENID = "openid-learning-test";

    private final FakeMemoryFacts facts = new FakeMemoryFacts();
    private final List<PlanDishOutcome> outcomeRows = new ArrayList<>();
    private final PlanDishOutcomeRepository outcomes = mock(PlanDishOutcomeRepository.class);
    private final UserRecordRepository records = mock(UserRecordRepository.class);
    private final AgentLearningService learning;

    AgentLearningServiceTest() {
        when(outcomes.findTop50ByOpenidOrderByUpdatedAtDesc(OPENID)).thenReturn(outcomeRows);
        when(outcomes.findByOpenidAndPlanIdAndDayIndexAndDishIndex(anyString(), any(), anyInt(), anyInt()))
                .thenReturn(Optional.empty());
        when(outcomes.save(any(PlanDishOutcome.class))).thenAnswer(invocation -> {
            PlanDishOutcome saved = invocation.getArgument(0);
            outcomeRows.add(saved);
            return saved;
        });

        AgentMemoryStore store = new AgentMemoryStore(facts.repository(), mock(UserFoodPreferenceRepository.class),
                records, mock(RecipeInteractionRepository.class), mock(RecipeRepository.class), outcomes);
        learning = new AgentLearningService(outcomes, store, records,
                mock(RecipeInteractionRepository.class), mock(RecipeRepository.class));
    }

    /** 问了三次都没人理的问题，下次不许再问。 */
    @Test
    void learnsToStopAskingQuestionsNobodyAnswers() {
        for (int i = 0; i < 3; i++) {
            learning.recordCardShown(OPENID, "ASK_SPICE");
        }
        learning.refresh(OPENID);

        assertTrue(learning.hints(OPENID).skipQuestions().contains("ASK_SPICE"),
                "问了 3 次没回答的问题应被加入跳过列表");
        assertEquals("ASK_SPICE", facts.get(OPENID, AgentMemoryStore.KEY_SKIP_QUESTIONS).getMemoryValue());
        assertTrue(facts.get(OPENID, AgentMemoryStore.KEY_SKIP_QUESTIONS).getEvidence().contains("3"));
    }

    /** 点得多的卡片要变成快捷选项，减少重复追问。 */
    @Test
    void promotesCardsUsersActuallyAnswer() {
        learning.recordCardShown(OPENID, "ASK_PEOPLE");
        learning.recordCardAnswered(OPENID, "ASK_PEOPLE", true);
        learning.recordCardShown(OPENID, "ASK_PEOPLE");
        learning.recordCardAnswered(OPENID, "ASK_PEOPLE", true);

        AgentLearningService.StrategyHints hints = learning.hints(OPENID);

        assertTrue(hints.preferQuickOptions().contains("ASK_PEOPLE"), hints.toString());
        assertTrue(hints.skipQuestions().isEmpty(), "被回答过的问题不该被跳过");
    }

    /** 连续两道菜被反馈"太难做"，下次自动收紧难度与时长上限。 */
    @Test
    void tightensDifficultyWhenDishesAreTooHard() {
        learning.recordOutcome(OPENID, new AgentLearningService.OutcomeInput(1L, 0, 0, "佛跳墙", true, false, true));
        learning.recordOutcome(OPENID, new AgentLearningService.OutcomeInput(1L, 1, 0, "松鼠鳜鱼", true, false, true));

        AgentLearningService.StrategyHints hints = learning.hints(OPENID);

        assertTrue(hints.preferSimple());
        assertEquals(Integer.valueOf(35), hints.maxCookingMinutes());
        assertEquals("true", facts.get(OPENID, AgentMemoryStore.KEY_SIMPLE).getMemoryValue());
    }

    /** 反复没做的菜要被拉黑，而不是继续排进下周。 */
    @Test
    void avoidsDishesUserNeverCooks() {
        learning.recordOutcome(OPENID, new AgentLearningService.OutcomeInput(1L, 0, 0, "红烧肉", false, null, null));
        learning.recordOutcome(OPENID, new AgentLearningService.OutcomeInput(1L, 1, 0, "红烧肉", false, null, null));

        assertTrue(learning.hints(OPENID).avoidDishes().contains("红烧肉"));
        assertEquals("红烧肉", facts.get(OPENID, AgentLearningService.KEY_AVOID_DISHES).getMemoryValue());
    }

    /** 做过的菜会沉淀成菜系亲和，下一次规划优先用得上。 */
    @Test
    void buildsCuisineAffinityFromRealSignals() {
        when(records.findTop30ByOpenidOrderByCreatedAtDesc(OPENID)).thenReturn(List.of(record("川菜-回锅肉")));

        learning.refresh(OPENID);

        assertEquals(Double.valueOf(1.0d), learning.hints(OPENID).cuisineAffinity().get("川菜"));
        assertEquals("1.0", facts.get(OPENID, AgentMemoryStore.AFFINITY_PREFIX + "川菜").getMemoryValue());
    }

    private UserRecord record(String dishName) {
        UserRecord record = new UserRecord();
        record.setOpenid(OPENID);
        record.setDishName(dishName);
        record.setRecordDate(LocalDate.now().toString());
        return record;
    }
}
