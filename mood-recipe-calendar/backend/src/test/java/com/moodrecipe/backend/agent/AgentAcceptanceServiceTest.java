package com.moodrecipe.backend.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 验收服务给出的是"能不能算真智能体"的判定，不允许靠"看起来能跑"自欺欺人。 */
class AgentAcceptanceServiceTest {

    private static final String OPENID = "openid-acceptance-test";

    @Test
    void reportsDegradedModeWhenModelIsMissing() {
        LlmClient llm = mock(LlmClient.class);
        when(llm.isConfigured()).thenReturn(false);

        AgentAcceptanceService.Report report = service(llm, mock(ToolRegistry.class), mock(MenuPlannerAgent.class))
                .verify(OPENID);

        assertFalse(report.agentReady());
        assertEquals(1, report.checks().size());
        assertFalse(report.checks().get(0).passed());
        assertTrue(report.summary().contains("降级"), report.summary());
    }

    @Test
    void passesEveryGateWhenModelToolsAndPlanningWork() {
        LlmClient llm = new FakeLlm("我是锅仔，你的做菜搭子。", "{\"ok\":true,\"name\":\"锅仔\"}");
        AgentAcceptanceService.Report report = service(llm, registry(), planner(88)).verify(OPENID);

        assertTrue(report.agentReady(), report.summary());
        assertEquals(5, report.checks().size());
        assertTrue(report.checks().stream().allMatch(AgentAcceptanceService.Check::passed),
                report.checks().toString());
        assertTrue(report.summary().contains("真正的智能体"), report.summary());
    }

    /** 模型不能稳定输出结构化结果时，必须判为未通过并说清是哪一级。 */
    @Test
    void failsWhenModelCannotReturnStructuredJson() {
        LlmClient llm = new FakeLlm("我是锅仔", "这不是 JSON，只是一句中文");

        AgentAcceptanceService.Report report = service(llm, registry(), planner(88)).verify(OPENID);

        assertFalse(report.agentReady());
        assertTrue(report.checks().stream()
                .anyMatch(check -> "结构化输出".equals(check.name()) && !check.passed()));
        assertTrue(report.summary().contains("结构化输出"), report.summary());
    }

    /** 工具一个都没注册时，同样不算智能体。 */
    @Test
    void failsWhenNoToolIsRegistered() {
        LlmClient llm = new FakeLlm("我是锅仔", "{\"ok\":true}");
        ToolRegistry registry = mock(ToolRegistry.class);
        when(registry.specs()).thenReturn(List.of());

        AgentAcceptanceService.Report report = service(llm, registry, planner(88)).verify(OPENID);

        assertFalse(report.agentReady());
        assertTrue(report.summary().contains("工具注册"), report.summary());
    }

    private AgentAcceptanceService service(LlmClient llm, ToolRegistry registry, MenuPlannerAgent planner) {
        return new AgentAcceptanceService(llm, registry, planner, mock(AgentMemoryStore.class), new ObjectMapper());
    }

    private ToolRegistry registry() {
        ToolRegistry registry = mock(ToolRegistry.class);
        when(registry.specs()).thenReturn(List.of(
                new LlmToolSpec("recall_user_profile", "回忆用户档案", "{}"),
                new LlmToolSpec("search_recipes", "检索候选菜谱", "{}")));
        return registry;
    }

    private MenuPlannerAgent planner(int score) {
        MenuPlannerAgent planner = mock(MenuPlannerAgent.class);
        when(planner.plan(any(MenuPlannerAgent.PlanRequest.class))).thenReturn(
                new MenuPlannerAgent.PlanResult(
                        List.of(new MenuPlannerAgent.PlannedDay(0,
                                List.of(new MenuPlannerAgent.PlannedDish("番茄炒蛋", List.of("番茄 2个", "鸡蛋 2个"),
                                        List.of("炒熟即可"), 20, "简单", "MAIN", "")),
                                "番茄可与第二天复用", "少油少盐")),
                        new MenuQualityScorer.MenuQuality(score, List.of(),
                                new MenuQualityScorer.Stats(1, 1, 1, 1d, 0.5d, 20d, 0)),
                        List.of(), List.of(new AgentTrace.Step("plan", "menu", 12L, "一次通过", true)),
                        List.of("people"), "trace-acceptance"));
        return planner;
    }

    /** 按用途返回预设回复，模拟"文本可以、结构化不行"这类真实故障。 */
    private record FakeLlm(String text, String json) implements LlmClient {
        @Override
        public LlmResult complete(LlmRequest request) {
            String payload = "acceptance-json".equals(request.purpose()) ? json : text;
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
            return true;
        }
    }
}
