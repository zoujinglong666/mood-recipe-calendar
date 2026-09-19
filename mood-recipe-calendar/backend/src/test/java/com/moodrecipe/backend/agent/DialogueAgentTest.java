package com.moodrecipe.backend.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.entity.AgentMemoryFact;
import com.moodrecipe.backend.repository.AgentMemoryFactRepository;
import com.moodrecipe.backend.repository.PlanDishOutcomeRepository;
import com.moodrecipe.backend.repository.RecipeInteractionRepository;
import com.moodrecipe.backend.repository.RecipeRepository;
import com.moodrecipe.backend.repository.UserFoodPreferenceRepository;
import com.moodrecipe.backend.repository.UserRecordRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 对话智能体的行为验收：理解靠模型、追问按影响排序、模型不能越权、学过的东西要生效、降级要说清楚。
 */
class DialogueAgentTest {

    private static final String OPENID = "openid-agent-test";

    @Test
    void understandsHometownThatNoRegexKnows() {
        FakeLlm llm = new FakeLlm();
        llm.understand = "{\"facts\":[{\"key\":\"favoriteCuisine\",\"value\":\"赣菜\","
                + "\"confidence\":0.85,\"explicit\":false,\"evidence\":\"用户说老家在乐平，属江西\"}]}";

        DialogueState.Turn turn = agent(llm, List.of()).turn(OPENID, "我老家在乐平，小时候口味重", null);

        assertEquals("赣菜", turn.state().favoriteCuisine(), "理解应由模型完成，而不是只认写死的省份名");
    }

    @Test
    void refusesToFinishWhenCriticalFactsAreMissing() {
        FakeLlm llm = new FakeLlm();
        llm.decide = "{\"action\":\"READY\",\"reply\":\"信息够了，直接开始\","
                + "\"card\":{\"type\":\"READY\",\"title\":\"开始\",\"description\":\"\",\"options\":[]}}";

        DialogueState.Turn turn = agent(llm, List.of()).turn(OPENID, "帮我安排一下", null);

        assertEquals("ASK_PEOPLE", turn.action(), "模型想跳过关键信息时，服务端必须拦住");
        assertNotNull(turn.card());
    }

    /** 自主学习：问了多次都没人答的问题，不再追问，直接用默认值。 */
    @Test
    void stopsAskingQuestionsUserAlwaysIgnores() {
        FakeLlm llm = new FakeLlm();
        llm.decide = "{\"action\":\"READY\",\"reply\":\"直接开始\"}";
        List<AgentMemoryFact> memory = List.of(fact(AgentMemoryStore.KEY_SKIP_QUESTIONS, "ASK_PEOPLE",
                AgentMemoryStore.SRC_LEARNED));

        DialogueState.Turn turn = agent(llm, memory).turn(OPENID, "", null);

        assertNotEquals("ASK_PEOPLE", turn.action());
        assertEquals(2, turn.state().people(), "被跳过的问题要用默认值兜底，不能让状态一直悬空");
    }

    @Test
    void explainsDegradationWhenModelIsUnavailable() {
        FakeLlm llm = new FakeLlm();
        llm.configured = false;

        DialogueState.Turn turn = agent(llm, List.of()).turn(OPENID, "家里 3 口人", null);

        assertFalse(turn.degraded().isEmpty(), "降级必须说出来，不能假装正常");
        assertEquals(3, turn.state().people(), "模型不可用时本地兜底仍要听得懂基本信息");
    }

    @Test
    void surfacesConflictBetweenChildAndSpicy() {
        FakeLlm llm = new FakeLlm();
        llm.understand = "{\"facts\":[{\"key\":\"household\",\"value\":\"有小孩\",\"confidence\":0.9,\"explicit\":true,"
                + "\"evidence\":\"用户说家里有小孩\"},{\"key\":\"spice\",\"value\":\"能吃辣\",\"confidence\":0.9,"
                + "\"explicit\":true,\"evidence\":\"用户说能吃辣\"}]}";
        llm.decide = "{\"action\":\"READY\",\"reply\":\"开始吧\"}";

        DialogueState.Turn turn = agent(llm, List.of()).turn(OPENID, "家里有小孩，但是我们都能吃辣", null);

        assertFalse(turn.conflicts().isEmpty(), "信息互相矛盾时应该先解决矛盾");
        assertTrue(turn.conflicts().stream().anyMatch(text -> text.contains("小孩")), turn.conflicts().toString());
    }

    @Test
    void keepsBanquetDishCountAndGuestContextWithoutAskingAgain() {
        FakeLlm llm = new FakeLlm();
        llm.understand = "{\"facts\":[{\"key\":\"favoriteCuisine\",\"value\":\"湘菜\"," 
                + "\"confidence\":0.9,\"explicit\":false,\"evidence\":\"有湖南客人\"}]}";

        DialogueState.Turn turn = agent(llm, List.of()).turn(OPENID,
                "家里来客人，有湖南人、西安人、广东人，总共8人，帮我安排9道菜宴请客人", null);

        assertEquals(8, turn.state().people());
        assertEquals(9, turn.state().dishesPerDay());
        assertTrue(turn.state().mealContext().contains("湖南人"));
        assertNull(turn.state().favoriteCuisine(), "客人的籍贯不能误记成用户长期偏好");
        assertNotEquals("ASK_DISHES", turn.action(), "用户已经说了九道菜，不得重复追问");
    }

    @Test
    void todayBanquetStartsAnIndependentSingleMeal() {
        FakeLlm llm = new FakeLlm();
        List<AgentMemoryFact> memory = List.of(
                fact(AgentMemoryStore.KEY_PEOPLE, "3", AgentMemoryStore.SRC_CHAT),
                fact(AgentMemoryStore.KEY_DAYS, "5,6", AgentMemoryStore.SRC_CHAT),
                fact(AgentMemoryStore.KEY_SPICE, "不吃辣", AgentMemoryStore.SRC_CHAT),
                fact(AgentMemoryStore.KEY_CUISINE, "赣菜", AgentMemoryStore.SRC_CHAT));
        DialogueState.AgentState oldState = DialogueState.AgentState.empty()
                .withPeople(3).withCookingDays(List.of(5, 6)).withSpiceLevel("不吃辣");

        DialogueState.Turn turn = agent(llm, memory).turn(OPENID,
                "我今天宴请8位客人，安排9道菜", oldState);

        assertEquals(8, turn.state().people());
        assertEquals(9, turn.state().dishesPerDay());
        assertEquals(List.of(LocalDate.now().getDayOfWeek().getValue() - 1), turn.state().cookingDays());
        assertNull(turn.state().spiceLevel(), "独立宴请不能继承上次会话的辣度");
        assertTrue(turn.memoryUsed().isEmpty(), "独立宴请不能把长期口味展示为本轮依据");
    }

    @Test
    void understandsChineseNumbersWithoutModel() {
        List<AgentFact> facts = HeuristicExtractor.facts("十个人，准备九道菜聚餐");

        assertTrue(facts.stream().anyMatch(fact -> "people".equals(fact.key()) && "10".equals(fact.value())));
        assertTrue(facts.stream().anyMatch(fact -> "dishesPerDay".equals(fact.key()) && "9".equals(fact.value())));
    }

    @Test
    void adaptsDishChoicesToPartySizeAndKeepsBothHouseholdNeeds() {
        DialogueState.Card card = AgentCards.defaultCard("ASK_DISHES",
                DialogueState.AgentState.empty().withPeople(8));
        List<AgentFact> facts = HeuristicExtractor.facts("家里有老人和小孩");
        DialogueState.AgentState selected = HeuristicExtractor.applySelection(
                DialogueState.AgentState.empty(), "household=elder,child");

        assertTrue(card.options().stream().anyMatch(option -> "dishes=9".equals(option.value())));
        assertTrue(facts.stream().anyMatch(fact -> "有老人和小孩".equals(fact.value())));
        assertTrue(Boolean.TRUE.equals(selected.hasElder()) && Boolean.TRUE.equals(selected.hasChild()));
    }

    @Test
    void medicalRequestsUseFixedBoundaryWithoutCallingModel() {
        FakeLlm llm = new FakeLlm();
        DialogueState.Turn turn = agent(llm, List.of()).turn(OPENID, "糖尿病怎么停药，吃什么能治疗？", null);

        assertTrue(turn.reply().contains("不能根据疾病给出诊断、治疗或停药建议"));
        assertNotNull(turn.card());
    }

    private DialogueAgent agent(LlmClient llm, List<AgentMemoryFact> memory) {
        AgentMemoryFactRepository facts = mock(AgentMemoryFactRepository.class);
        when(facts.findByOpenidAndStatusOrderByUpdatedAtDesc(anyString(), anyString()))
                .thenReturn(new ArrayList<>(memory));
        when(facts.save(any(AgentMemoryFact.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AgentMemoryStore store = new AgentMemoryStore(facts, mock(UserFoodPreferenceRepository.class),
                mock(UserRecordRepository.class), mock(RecipeInteractionRepository.class),
                mock(RecipeRepository.class), mock(PlanDishOutcomeRepository.class));
        AgentLearningService learning = new AgentLearningService(mock(PlanDishOutcomeRepository.class), store,
                mock(UserRecordRepository.class), mock(RecipeInteractionRepository.class),
                mock(RecipeRepository.class));
        return new DialogueAgent(llm, store, mock(UserFoodPreferenceRepository.class), learning, new ObjectMapper());
    }

    private AgentMemoryFact fact(String key, String value, String source) {
        AgentMemoryFact fact = new AgentMemoryFact();
        fact.setOpenid(OPENID);
        fact.setMemoryKey(key);
        fact.setMemoryValue(value);
        fact.setSource(source);
        fact.setConfidence(0.9);
        fact.setEvidence("测试构造");
        fact.setStatus(AgentMemoryFact.STATUS_ACTIVE);
        fact.setHitCount(0);
        fact.setUpdatedAt(LocalDateTime.now());
        return fact;
    }

    private static final class FakeLlm implements LlmClient {
        private String understand = "{}";
        private String decide = "{}";
        private boolean configured = true;

        @Override
        public LlmResult complete(LlmRequest request) {
            String payload = "agent-understand".equals(request.purpose()) ? understand : decide;
            return LlmResult.ok(new LlmResponse(payload, List.of(), "stop", null, "fake-model"), 1, 1L);
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public String model() {
            return "fake-model";
        }

        @Override
        public boolean supportsTools() {
            return false;
        }
    }
}
