package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.agent.*;
import com.moodrecipe.backend.entity.CookingKnowledgeChunk;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.repository.RecipeRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CookingAgentServiceTest {
    @Test
    void returnsOriginalStepsWhenModelIsUnavailable() {
        Fixture fixture = fixture(List.of());
        when(fixture.llm.isConfigured()).thenReturn(false);

        var response = fixture.service.turn("user-1", request("GUIDE", false));

        assertTrue(response.degraded());
        assertEquals("MODEL_UNAVAILABLE", response.degradeReason());
        assertEquals(2, response.guideSteps().size());
        verify(fixture.memory, never()).profile(anyString(), any());
    }

    @Test
    void exposesOnlySourcesReturnedByRetriever() {
        CookingKnowledgeChunk source = new CookingKnowledgeChunk();
        source.setId(7L); source.setTitle("肉类熟度"); source.setContent("中心温度达到70℃以上");
        source.setSourceName("权威来源"); source.setSourceUrl("https://example.com/source");
        source.setSourceVersion("2026-09"); source.setReviewedAt(LocalDateTime.of(2026, 9, 17, 0, 0));
        Fixture fixture = fixture(List.of(source));
        when(fixture.llm.isConfigured()).thenReturn(true);
        when(fixture.llm.complete(any())).thenReturn(okResponse());

        var response = fixture.service.turn("user-1", request("GUIDE", false));

        assertFalse(response.degraded());
        assertEquals(1, response.sources().size());
        assertEquals(7L, response.sources().getFirst().id());
        assertEquals("https://example.com/source", response.sources().getFirst().sourceUrl());
    }

    @Test
    void labelsAValidModelAnswerWithoutEvidence() {
        Fixture fixture = fixture(List.of());
        when(fixture.llm.isConfigured()).thenReturn(true);
        when(fixture.llm.complete(any())).thenReturn(okResponse());

        var response = fixture.service.turn("user-1", request("GUIDE", false));

        assertTrue(response.degraded());
        assertEquals("NO_EVIDENCE", response.degradeReason());
        assertTrue(response.sources().isEmpty());
    }

    @Test
    void degradesOnTimeoutAndBlockedOutput() {
        Fixture timeout = fixture(List.of());
        when(timeout.llm.isConfigured()).thenReturn(true);
        when(timeout.llm.complete(any())).thenReturn(LlmResult.failed(LlmResult.Failure.TIMEOUT, 1, 15_000));
        assertEquals("MODEL_ERROR", timeout.service.turn("user-1", request("ASK", false)).degradeReason());

        Fixture blocked = fixture(List.of());
        when(blocked.llm.isConfigured()).thenReturn(true);
        when(blocked.llm.complete(any())).thenReturn(okResponse());
        when(blocked.safety.allowsText(anyString(), any(String[].class))).thenReturn(false);
        assertEquals("CONTENT_BLOCKED", blocked.service.turn("user-1", request("GUIDE", false)).degradeReason());
    }

    private Fixture fixture(List<CookingKnowledgeChunk> evidence) {
        RecipeRepository recipes = mock(RecipeRepository.class);
        Recipe recipe = new Recipe();
        recipe.setId(1L); recipe.setName("番茄炒蛋"); recipe.setIngredients("[\"番茄\",\"鸡蛋\"]");
        recipe.setSteps("[\"鸡蛋炒熟盛出\",\"番茄炒软后混合\"]");
        when(recipes.findById(1L)).thenReturn(Optional.of(recipe));
        CookingKnowledgeService knowledge = mock(CookingKnowledgeService.class);
        when(knowledge.search(anyString(), anyInt())).thenReturn(evidence);
        CookingLearningService learning = mock(CookingLearningService.class);
        when(learning.teachingLevel(anyString(), anyBoolean())).thenReturn("GUIDED");
        AgentMemoryStore memory = mock(AgentMemoryStore.class);
        when(memory.personalizationEnabled(anyString())).thenReturn(false);
        LlmClient llm = mock(LlmClient.class);
        WechatContentSafetyService safety = mock(WechatContentSafetyService.class);
        when(safety.allowsText(anyString(), any(String[].class))).thenReturn(true);
        return new Fixture(new CookingAgentService(recipes, knowledge, learning, memory, llm,
                new ObjectMapper(), safety, true), llm, memory, safety);
    }

    private CookingAgentService.TurnRequest request(String action, boolean personalized) {
        return new CookingAgentService.TurnRequest(1L, 0, "session-1", "怎样算熟？", action,
                personalized, List.of());
    }

    private LlmResult okResponse() {
        String json = """
                {"reply":"鸡蛋凝固且没有流动蛋液即可", "guideSteps":[
                  {"index":1,"instruction":"鸡蛋炒熟盛出","heat":"中火","duration":"1分钟","successSigns":"蛋液完全凝固","rescue":"先离火翻散"},
                  {"index":2,"instruction":"番茄炒软后混合","heat":"中火","duration":"2分钟","successSigns":"番茄出汁变软","rescue":"过干时加一勺水"}
                ],"suggestions":["怎样算熟？"]}
                """;
        return LlmResult.ok(new LlmResponse(json, List.of(), "stop", LlmUsage.UNKNOWN, "test"), 1, 5);
    }

    private record Fixture(CookingAgentService service, LlmClient llm, AgentMemoryStore memory,
                           WechatContentSafetyService safety) {}
}
