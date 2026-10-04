package com.moodrecipe.backend.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentLoopTest {

    @Mock
    private LlmClient llm;

    private AgentTool fakeTool() {
        return new AgentTool() {
            public String name() { return "web_search"; }
            public String description() { return "搜索"; }
            public String parametersJson() { return "{}"; }
            public ToolResult run(String argsJson, ToolContext ctx) {
                return ToolResult.ok("搜索结果：番茄");
            }
        };
    }

    private AgentLoop loop() {
        return new AgentLoop(llm, new ToolRegistry(List.of(fakeTool())), new ObjectMapper());
    }

    private AgentLoop.RunSpec spec() {
        return new AgentLoop.RunSpec("目的", "目标", Set.of("web_search"),
                5, 0.5, 500, TimeoutTier.STANDARD, new ToolContext("u1", "trace", new ObjectMapper()));
    }

    @Test
    void failsWhenLlmNotConfigured() {
        when(llm.isConfigured()).thenReturn(false);
        AgentLoop.Outcome outcome = loop().run(spec());
        assertFalse(outcome.completed());
        assertTrue(outcome.failure().contains("未配置"));
    }

    @Test
    void runsToolThenReturnsFinalAnswer() {
        when(llm.isConfigured()).thenReturn(true);
        LlmResponse toolStep = new LlmResponse(
                "{\"toolCalls\":[{\"tool\":\"web_search\",\"args\":{\"q\":\"番茄\"}}]}",
                List.of(new LlmToolCall("c1", "web_search", "{\"q\":\"番茄\"}")),
                "tool_calls", null, "fake");
        LlmResponse finalStep = new LlmResponse(
                "{\"finalAnswer\":\"买番茄两个\"}", null, "stop", null, "fake");
        when(llm.complete(any())).thenReturn(
                LlmResult.ok(toolStep, 1, 1L),
                LlmResult.ok(finalStep, 1, 1L));

        AgentLoop.Outcome outcome = loop().run(spec());
        assertTrue(outcome.completed());
        assertEquals("买番茄两个", outcome.finalAnswer());
        assertTrue(outcome.toolsUsed().contains("web_search"));
        assertEquals(2, outcome.steps());
    }

    @Test
    void failsWhenModelCallFails() {
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(any())).thenReturn(LlmResult.failed(LlmResult.Failure.SERVER_ERROR, 1, 1L));
        AgentLoop.Outcome outcome = loop().run(spec());
        assertFalse(outcome.completed());
        assertTrue(outcome.failure().contains("模型调用失败"));
    }

    @Test
    void executeToolDelegatesToRegistry() {
        ToolResult result = loop().executeTool("web_search", "{}", new ToolContext("u1", "t", new ObjectMapper()));
        assertTrue(result.outputJson().contains("番茄"));
    }
}
