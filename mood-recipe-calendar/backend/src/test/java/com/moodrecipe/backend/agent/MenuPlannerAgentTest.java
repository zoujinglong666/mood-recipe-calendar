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
import com.moodrecipe.backend.service.RecipePool;
import com.moodrecipe.backend.service.search.SearchClient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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

    @Test
    void localFallbackKeepsExplicitIngredientRequests() {
        MenuPlannerAgent planner = planner(mock(LlmClient.class), recipes(), List.of());

        MenuPlannerAgent.PlanResult result = planner.plan(new MenuPlannerAgent.PlanRequest(
                OPENID, List.of(0), 3, "BALANCED", "DAILY", "指定食材：鱼、鸡肉、牛肉"));

        List<String> names = result.days().get(0).dishes().stream()
                .map(MenuPlannerAgent.PlannedDish::name).toList();
        assertTrue(names.stream().anyMatch(name -> name.contains("鱼")), names.toString());
        assertTrue(names.stream().anyMatch(name -> name.contains("鸡")), names.toString());
        assertTrue(names.stream().anyMatch(name -> name.contains("牛")), names.toString());
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
    void largeMenuUsesOneAiPassPerDay() {
        SequencingLlm llm = new SequencingLlm(
                "{\"days\":[{\"dishes\":[{\"name\":\"番茄炒蛋\",\"ingredients\":[\"番茄\",\"鸡蛋\"]}]}]}");
        MenuPlannerAgent planner = planner(llm, recipes(), List.of());

        planner.plan(new MenuPlannerAgent.PlanRequest(
                OPENID, List.of(5, 6), 9, "BALANCED", "DAILY", "八人宴请"));

        assertTrue(llm.planCalls() >= 2, "大菜单应按天分批生成，避免单次超长输出");
    }

    @Test
    void neverReturnsMojibakeDishNamesFromStructuredModelOutput() {
        SequencingLlm llm = new SequencingLlm(
                "{\"days\":[{\"dishes\":[{\"name\":\"ç•ªéŒŒ‚ç’ë›×\",\"ingredients\":[\"ç•ªéŒŒ\"],\"steps\":[\"ç‚’ç†Ÿå‘³å³å¯\"]}]}]}");
        MenuPlannerAgent planner = planner(llm, recipes(), List.of());

        MenuPlannerAgent.PlanResult result = planner.plan(new MenuPlannerAgent.PlanRequest(
                OPENID, List.of(0), 1, "BALANCED", "DAILY", ""));

        assertEquals("番茄炒蛋", result.days().get(0).dishes().get(0).name());
        assertTrue(result.degradeReasons().stream().anyMatch(reason -> reason.contains("结构化结果")),
                result.degradeReasons().toString());
    }

    /** 模型限流回退本地排菜时，用户点名但库里没有的菜（如地方特色菜）必须现场生成补进菜单。 */
    @Test
    void generatesRequestedDishWhenPoolCannotCoverIt() {
        LlmClient llm = mock(LlmClient.class);
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(any())).thenAnswer(invocation -> {
            LlmRequest request = invocation.getArgument(0);
            if ("agent-requested-dish".equals(request.purpose())) {
                return LlmResult.ok(new LlmResponse(
                        "{\"name\":\"南昌拌粉\",\"ingredients\":[\"米粉 200g\",\"花生米 30g\",\"葱花 10g\"],"
                                + "\"steps\":[\"米粉煮熟捞出\",\"拌入调料与花生米\",\"撒葱花拌匀即可\"],"
                                + "\"cookingTime\":15,\"difficulty\":\"简单\"}",
                        List.of(), "stop", new LlmUsage(0, 0, 0), "test"), 1, 0);
            }
            return LlmResult.failed(LlmResult.Failure.RATE_LIMITED, 2, 500);
        });
        MenuPlannerAgent planner = planner(llm, recipes(), List.of());

        MenuPlannerAgent.PlanResult result = planner.plan(new MenuPlannerAgent.PlanRequest(
                OPENID, List.of(0), 2, "BALANCED", "DAILY", "指定食材：南昌拌粉"));

        List<String> names = result.days().stream().flatMap(day -> day.dishes().stream())
                .map(MenuPlannerAgent.PlannedDish::name).toList();
        assertTrue(names.stream().anyMatch(name -> name.contains("南昌拌粉")),
                "点名的菜必须出现在菜单里：" + names);
        assertTrue(result.degradeReasons().stream().anyMatch(reason -> reason.contains("现场生成")),
                result.degradeReasons().toString());
    }

    /** 现场生成也失败时，必须如实报告点名食材无法满足，不能谎报"已补齐"。 */
    @Test
    void reportsHonestlyWhenRequestedDishCannotBeHonored() {
        LlmClient llm = mock(LlmClient.class);
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(any())).thenReturn(LlmResult.failed(LlmResult.Failure.RATE_LIMITED, 2, 500));
        MenuPlannerAgent planner = planner(llm, recipes(), List.of());

        MenuPlannerAgent.PlanResult result = planner.plan(new MenuPlannerAgent.PlanRequest(
                OPENID, List.of(0), 2, "BALANCED", "DAILY", "指定食材：南昌拌粉"));

        assertFalse(result.degradeReasons().stream().anyMatch(reason -> reason.contains("已按指定食材重新补齐")));
        assertTrue(result.degradeReasons().stream().anyMatch(reason ->
                        reason.contains("南昌拌粉") && reason.contains("暂无法满足")),
                result.degradeReasons().toString());
    }

    /** 点名菜生成时联网搜索真实做法：搜索摘要必须进入生成提示词，作为生成依据。 */
    @Test
    void usesWebSearchEvidenceWhenGeneratingRequestedDish() {
        SearchClient search = mock(SearchClient.class);
        when(search.available()).thenReturn(true);
        when(search.search(anyString(), anyInt())).thenReturn(List.of(
                new SearchClient.SearchResult("南昌拌粉的做法",
                        "主料：米粉 200g、花生米 30g；步骤：米粉煮熟后拌入调料、撒葱花", "https://example.com")));
        AtomicReference<String> capturedPrompt = new AtomicReference<>();
        LlmClient llm = mock(LlmClient.class);
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(any())).thenAnswer(invocation -> {
            LlmRequest request = invocation.getArgument(0);
            if ("agent-requested-dish".equals(request.purpose())) {
                capturedPrompt.set(request.messages().get(0).content());
                return LlmResult.ok(new LlmResponse(
                        "{\"name\":\"南昌拌粉\",\"ingredients\":[\"米粉 200g\",\"花生米 30g\"],"
                                + "\"steps\":[\"米粉煮熟捞出\",\"拌入调料与花生米\"],\"cookingTime\":15,\"difficulty\":\"简单\"}",
                        List.of(), "stop", new LlmUsage(0, 0, 0), "test"), 1, 0);
            }
            return LlmResult.failed(LlmResult.Failure.RATE_LIMITED, 2, 500);
        });
        MenuPlannerAgent planner = planner(llm, recipes(), List.of(), search);

        planner.plan(new MenuPlannerAgent.PlanRequest(
                OPENID, List.of(0), 2, "BALANCED", "DAILY", "指定食材：南昌拌粉"));

        assertTrue(capturedPrompt.get() != null && capturedPrompt.get().contains("米粉 200g"),
                "生成提示词必须包含联网搜到的真实做法：" + capturedPrompt.get());
    }

    private MenuPlannerAgent planner(LlmClient llm, RecipeRepository recipes,
                                     List<AgentMemoryFact> memory) {
        return planner(llm, recipes, memory, noSearch());
    }

    private MenuPlannerAgent planner(LlmClient llm, RecipeRepository recipes,
                                     List<AgentMemoryFact> memory, SearchClient search) {
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

        return new MenuPlannerAgent(llm, store, recipes, new ObjectMapper(), search, new RecipePool(recipes));
    }

    /** 空搜索实现：未接入搜索的既有测试用，generateRequestedDish 直接走纯生成路径。 */
    private SearchClient noSearch() {
        return new SearchClient() {
            @Override public boolean available() { return false; }
            @Override public List<SearchClient.SearchResult> search(String query, int maxResults) { return List.of(); }
        };
    }

    private RecipeRepository recipes() {
        RecipeRepository recipes = mock(RecipeRepository.class);
        when(recipes.findAiWithImages()).thenReturn(List.of());
        when(recipes.findAll()).thenReturn(List.of(recipe("番茄炒蛋"), recipe("清炒时蔬"), recipe("红烧肉"),
                recipe("紫菜蛋花汤"), recipe("清蒸鲈鱼"), recipe("香菇青菜"), recipe("可乐鸡翅"), recipe("番茄牛腩")));
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
