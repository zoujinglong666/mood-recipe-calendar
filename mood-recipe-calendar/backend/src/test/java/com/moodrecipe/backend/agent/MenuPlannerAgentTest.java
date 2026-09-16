package com.moodrecipe.backend.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.entity.AgentMemoryFact;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.entity.UserFoodPreference;
import com.moodrecipe.backend.repository.AgentMemoryFactRepository;
import com.moodrecipe.backend.repository.PlanDishOutcomeRepository;
import com.moodrecipe.backend.repository.RecipeInteractionRepository;
import com.moodrecipe.backend.repository.RecipeRepository;
import com.moodrecipe.backend.repository.UserFoodPreferenceRepository;
import com.moodrecipe.backend.repository.UserRecordRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 规划智能体：模型不可用时能降级，模型给出违规菜单时能纠偏。 */
class MenuPlannerAgentTest {

    private static final String OPENID = "openid-planner-test";

    @Test
    void degradesToLocalRecipesWhenModelIsMissing() {
        MenuPlannerAgent planner = planner(mock(LlmClient.class), recipes(), List.of());

        MenuPlannerAgent.PlanResult result = planner.plan(new MenuPlannerAgent.PlanRequest(
                OPENID, List.of(0, 1, 2), 1, "BALANCED", "DAILY", ""));

        assertEquals(3, result.days().size());
        assertNotNull(result.quality());
        assertTrue(result.degradeReasons().stream().anyMatch(reason -> reason.contains("模型未配置")),
                result.degradeReasons().toString());
        assertFalse(result.trace().isEmpty(), "每一步都要留痕，便于事后复盘");
        assertEquals(List.of("番茄炒蛋", "清炒时蔬", "红烧肉"),
                result.days().stream().map(day -> day.dishes().get(0).name()).toList());
    }

    /** 模型第一版排出了过敏食材，智能体必须带着具体问题重排，而不是照单全收。 */
    @Test
    void repairsMenuThatViolatesConstraints() {
        SequencingLlm llm = new SequencingLlm(
                "{\"days\":[{\"dishes\":[{\"name\":\"花生焖猪蹄\",\"role\":\"MAIN\",\"ingredients\":[\"花生\",\"猪蹄\"],\"cookingTime\":40,\"difficulty\":\"中等\"}]},"
                        + "{\"dishes\":[{\"name\":\"清炒时蔬\",\"role\":\"MAIN\",\"ingredients\":[\"时蔬\"],\"cookingTime\":15,\"difficulty\":\"简单\"}]}]}",
                "{\"days\":[{\"dishes\":[{\"name\":\"番茄炒蛋\",\"role\":\"MAIN\",\"ingredients\":[\"番茄\",\"鸡蛋\"],\"cookingTime\":20,\"difficulty\":\"简单\"}]},"
                        + "{\"dishes\":[{\"name\":\"清炒时蔬\",\"role\":\"MAIN\",\"ingredients\":[\"时蔬\"],\"cookingTime\":15,\"difficulty\":\"简单\"}]}]}");
        MenuPlannerAgent planner = planner(llm, recipes(), List.of());

        MenuPlannerAgent.PlanResult result = planner.plan(new MenuPlannerAgent.PlanRequest(
                OPENID, List.of(0, 1), 1, "BALANCED", "DAILY", ""));

        List<String> names = result.days().stream().flatMap(day -> day.dishes().stream())
                .map(MenuPlannerAgent.PlannedDish::name).toList();
        assertFalse(names.contains("花生焖猪蹄"), "违规的菜必须被换掉：" + names);
        assertTrue(result.degradeReasons().stream().anyMatch(reason -> reason.contains("未通过校验")),
                result.degradeReasons().toString());
        assertFalse(result.quality().hasHard());
    }

    @Test
    void fillsMissingDishesToTheUsersExplicitCount() {
        SequencingLlm llm = new SequencingLlm(
                "{\"days\":[{\"dishes\":[{\"name\":\"番茄炒蛋\",\"ingredients\":[\"番茄\",\"鸡蛋\"]}]}]}");
        MenuPlannerAgent planner = planner(llm, recipes(), List.of());

        MenuPlannerAgent.PlanResult result = planner.plan(new MenuPlannerAgent.PlanRequest(
                OPENID, List.of(0), 4, "BALANCED", "TREAT", "四人聚餐"));

        assertEquals(4, result.days().get(0).dishes().size(), "模型少给菜时必须补到用户明确要求的数量");
        assertEquals(4, result.days().get(0).dishes().stream().map(MenuPlannerAgent.PlannedDish::name)
                .distinct().count(), "补菜不能制造重复菜名");
    }

    @Test
    void largeMenuUsesOneAiPassThenLocalValidation() {
        SequencingLlm llm = new SequencingLlm(
                "{\"days\":[{\"dishes\":[{\"name\":\"番茄炒蛋\",\"ingredients\":[\"番茄\",\"鸡蛋\"]}]}]}");
        MenuPlannerAgent planner = planner(llm, recipes(), List.of());

        planner.plan(new MenuPlannerAgent.PlanRequest(
                OPENID, List.of(5, 6), 9, "BALANCED", "DAILY", "八人宴请"));

        assertEquals(1, llm.planCalls(), "大菜单不能在一次 HTTP 请求里串行重试数分钟");
    }

    private MenuPlannerAgent planner(LlmClient llm, RecipeRepository recipes,
                                     List<AgentMemoryFact> memory) {
        AgentMemoryFactRepository facts = mock(AgentMemoryFactRepository.class);
        when(facts.findByOpenidAndStatusOrderByUpdatedAtDesc(anyString(), anyString()))
                .thenReturn(new ArrayList<>(memory));
        when(facts.save(any(AgentMemoryFact.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserFoodPreference preference = new UserFoodPreference();
        preference.setOpenid(OPENID);
        preference.setAllergens("花生");
        UserFoodPreferenceRepository preferences = mock(UserFoodPreferenceRepository.class);
        when(preferences.findByOpenid(OPENID)).thenReturn(Optional.of(preference));

        AgentMemoryStore store = new AgentMemoryStore(facts, preferences, mock(UserRecordRepository.class),
                mock(RecipeInteractionRepository.class), recipes, mock(PlanDishOutcomeRepository.class));

        return new MenuPlannerAgent(llm, store, recipes, new ObjectMapper());
    }

    private RecipeRepository recipes() {
        RecipeRepository recipes = mock(RecipeRepository.class);
        when(recipes.findAiWithImages()).thenReturn(List.of());
        when(recipes.findAll()).thenReturn(List.of(recipe("番茄炒蛋"), recipe("清炒时蔬"), recipe("红烧肉"),
                recipe("紫菜蛋花汤"), recipe("清蒸鲈鱼"), recipe("香菇青菜")));
        return recipes;
    }

    private Recipe recipe(String name) {
        Recipe recipe = new Recipe();
        recipe.setName(name);
        recipe.setIngredients("[\"" + name + "\"]");
        recipe.setSteps("[\"炒熟即可\"]");
        return recipe;
    }

    /** 依次返回预设回复，用来模拟"第一版不合格、第二版合格"的重排过程。 */
    private static final class SequencingLlm implements LlmClient {
        private final List<String> payloads;
        private int cursor = 0;

        SequencingLlm(String... payloads) {
            this.payloads = List.of(payloads);
        }

        @Override
        public LlmResult complete(LlmRequest request) {
            String payload = payloads.get(Math.min(cursor, payloads.size() - 1));
            if ("agent-plan-menu".equals(request.purpose())) cursor++;
            return LlmResult.ok(new LlmResponse(payload, List.of(), "stop", null, "fake-model"), 1, 1L);
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public String model() {
            return "fake-model";
        }

        @Override
        public boolean supportsTools() {
            return false;
        }

        int planCalls() {
            return cursor;
        }
    }
}
