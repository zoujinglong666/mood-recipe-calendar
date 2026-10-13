package com.moodrecipe.backend.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.agent.tools.CheckDietaryConflictsTool;
import com.moodrecipe.backend.agent.tools.CheckRecentHistoryTool;
import com.moodrecipe.backend.agent.tools.ConsolidateIngredientsTool;
import com.moodrecipe.backend.agent.tools.RecallProfileTool;
import com.moodrecipe.backend.agent.tools.RememberFactTool;
import com.moodrecipe.backend.agent.tools.ScoreMenuTool;
import com.moodrecipe.backend.agent.tools.SearchRecipeTool;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.repository.PlanDishOutcomeRepository;
import com.moodrecipe.backend.repository.RecipeInteractionRepository;
import com.moodrecipe.backend.repository.RecipeRepository;
import com.moodrecipe.backend.repository.UserFoodPreferenceRepository;
import com.moodrecipe.backend.repository.UserRecordRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 真实模型的端到端验收：不打桩，真的打一次模型，判断"现在到底是不是智能体"。
 *
 * 默认不执行（仅当显式设置 AGENT_LIVE_TEST=true 时才跑），避免日常 mvn test 烧 token、被限流误导：
 * PowerShell 运行方式：
 * $env:AGENT_LIVE_TEST="true"; mvn -o test -Dtest=AgentLiveAcceptanceTest
 *
 * 跑起来后还会先做一次探针调用：模型限流/超时/未配置时整类跳过（记为 skipped），
 * 只有模型健康时才做严格断言，避免把"供应商抽风"误报成"智能体不合格"。
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "AGENT_LIVE_TEST", matches = "true")
class AgentLiveAcceptanceTest {

    private static final String OPENID = "live-acceptance-openid";

    @BeforeAll
    static void preflight() {
        LlmClient llm = llm();
        assumeTrue(llm.isConfigured(), "缺少 AGNES_API_KEY，跳过真实模型验收");
        LlmResult probe = llm.complete(LlmRequest.text("live-preflight", AgentPrompts.system(),
                "只回复两个字：可用", 0.2, 32, TimeoutTier.FAST));
        assumeTrue(probe.ok(), "真实模型当前不可用（限流/超时/服务异常），跳过端到端验收：" + probe.reason());
    }

    /** 模型必须自己发起工具调用并据此收敛，否则只是"会说话的文本补全"。 */
    @Test
    void realModelDrivesTheToolLoop() {
        Wiring wiring = wiring();

        AgentLoop.Outcome outcome = wiring.loop.run(new AgentLoop.RunSpec("live-acceptance-loop",
                "先用工具查一下这位用户的忌口和最近做过的菜，然后用一句话告诉我今晚适合做什么。",
                Set.of("recall_user_profile", "check_recent_history"), 4, 0.3, 700, TimeoutTier.STANDARD,
                new ToolContext(OPENID, "live-trace", wiring.json)));

        assertTrue(outcome.completed(), "模型可用时工具循环必须收敛：" + outcome.failure());
        assertFalse(outcome.toolsUsed().isEmpty(), "模型没有主动调用任何工具，说明它不是在干活：" + outcome);
        assertFalse(outcome.observations().isEmpty());
    }

    /** 五级验收：配置 → 文本 → 结构化 → 工具 → 规划闭环。 */
    @Test
    void realModelPassesEveryAcceptanceGate() {
        AgentAcceptanceService.Report report = wiring().acceptance.verify(OPENID);

        assertTrue(report.agentReady(), report.summary() + "；明细：" + report.checks());
        assertEquals(5, report.checks().size());
    }

    /** 规划闭环：模型排的菜单必须通过质量校验，否则要带着问题重排。 */
    @Test
    void realModelPlansMenuThatPassesQualityGate() {
        MenuPlannerAgent.PlanResult plan = wiring().planner.plan(new MenuPlannerAgent.PlanRequest(
                OPENID, List.of(0, 1, 2), 2, "BALANCED", "DAILY", "验收用的一次性规划"));

        assertFalse(plan.days().isEmpty(), "没能排出任何一天；降级原因：" + plan.degradeReasons());
        assertFalse(plan.quality().hasHard(), "菜单存在必须修正的问题：" + plan.quality().violationBrief());
        assertTrue(plan.quality().score() > 0);
        assertFalse(plan.trace().isEmpty(), "每一步都要留痕");
    }

    /** 理解由模型负责：不含关键词的 paraphrase 也要落到白名单动作上。 */
    @Test
    void realModelUnderstandsParaphrasedBudget() {
        DialogueState.Turn turn = wiring().dialogue.turn(OPENID, "这周手头有点紧，做菜别太铺张", null);

        assumeTrue(turn.degraded().isEmpty(), "模型本轮降级（限流/超时），NLU 结论不可信，跳过：" + turn.degraded());
        assertEquals("SAVE", turn.state().budget(), "模型没有听懂委婉表达：" + turn);
        assertFalse(turn.reply().isBlank());
    }

    /** 用户最早反馈的那句话："我老家在抚州" 必须落到赣菜。 */
    @Test
    void realModelInfersCuisineFromHometown() {
        DialogueState.Turn turn = wiring().dialogue.turn(OPENID, "我老家在抚州，来上海十年了", null);

        assumeTrue(turn.degraded().isEmpty(), "模型本轮降级（限流/超时），跳过：" + turn.degraded());
        assertEquals("赣菜", turn.state().favoriteCuisine(), turn.toString());
        assertTrue(turn.memoryUsed().stream().anyMatch(text -> text.contains("赣菜"))
                        || turn.askReason().contains("赣菜"),
                "这一轮要能说清用了什么记忆：" + turn.askReason() + " / " + turn.memoryUsed());
    }

    /** 真实模型客户端：密钥与模型名都来自环境变量，绝不写死在仓库里。 */
    private static LlmClient llm() {
        return new AgnesLlmClient(new ObjectMapper(), System.getenv("AGNES_API_KEY"),
                env("AI_RECIPE_BASE_URL", "https://apihub.agnes-ai.com/v1/chat/completions"),
                env("AI_RECIPE_MODEL", "agnes-3.0-flash"), 2, true);
    }

    private static Wiring wiring() {
        ObjectMapper json = new ObjectMapper();
        LlmClient llm = llm();

        FakeMemoryFacts facts = new FakeMemoryFacts();
        RecipeRepository recipes = mock(RecipeRepository.class);
        when(recipes.findAiWithImages()).thenReturn(List.of());
        when(recipes.findAll()).thenReturn(List.of(recipe("番茄炒蛋"), recipe("清炒时蔬"), recipe("红烧肉")));
        when(recipes.findAllById(any())).thenReturn(List.of());

        UserRecordRepository records = mock(UserRecordRepository.class);
        AgentMemoryStore store = new AgentMemoryStore(facts.repository(),
                mock(UserFoodPreferenceRepository.class), records, mock(RecipeInteractionRepository.class),
                recipes, mock(PlanDishOutcomeRepository.class));
        AgentLearningService learning = new AgentLearningService(mock(PlanDishOutcomeRepository.class),
                store, records, mock(RecipeInteractionRepository.class), recipes);

        ToolRegistry registry = new ToolRegistry(List.of(
                new RecallProfileTool(store), new RememberFactTool(store), new CheckDietaryConflictsTool(store),
                new ScoreMenuTool(store), new SearchRecipeTool(recipes),
                new CheckRecentHistoryTool(records, mock(RecipeInteractionRepository.class), recipes),
                new ConsolidateIngredientsTool()));
        AgentLoop loop = new AgentLoop(llm, registry, json);
        MenuPlannerAgent planner = new MenuPlannerAgent(llm, store, recipes, json);

        return new Wiring(json, loop, registry, store, planner,
                new AgentAcceptanceService(llm, registry, planner, store, json),
                new DialogueAgent(llm, store, mock(UserFoodPreferenceRepository.class), learning, json));
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Recipe recipe(String name) {
        Recipe recipe = new Recipe();
        recipe.setName(name);
        recipe.setIngredients("[\"" + name + "\"]");
        recipe.setSteps("[\"炒熟即可\"]");
        return recipe;
    }

    private record Wiring(ObjectMapper json, AgentLoop loop, ToolRegistry registry, AgentMemoryStore store,
                          MenuPlannerAgent planner, AgentAcceptanceService acceptance, DialogueAgent dialogue) {}
}
