package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.agent.LlmClient;
import com.moodrecipe.backend.agent.LlmResponse;
import com.moodrecipe.backend.agent.LlmResult;
import com.moodrecipe.backend.agent.LlmUsage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AllergenNormalizationServiceTest {

    @Test
    void combinesLocalSafetyTermsWithModelSynonyms() {
        LlmClient llm = mock(LlmClient.class);
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(any())).thenReturn(LlmResult.ok(new LlmResponse(
                "{\"terms\":[\"虾仁\",\"海虾\"]}", List.of(), "stop", new LlmUsage(0, 0, 0), "test"), 1, 0));

        List<String> result = new AllergenNormalizationService(llm, new ObjectMapper())
                .normalize("", "对虾过敏");

        assertEquals(List.of("对虾", "虾", "虾仁", "海虾"), result);
    }

    @Test
    void keepsLocalTermsWhenModelIsUnavailable() {
        LlmClient llm = mock(LlmClient.class);
        when(llm.isConfigured()).thenReturn(false);

        List<String> result = new AllergenNormalizationService(llm, new ObjectMapper())
                .normalize("", "花生过敏");

        assertEquals(List.of("花生"), result);
    }

    @Test
    void keepsShrimpSynonymWhenModelIsUnavailable() {
        LlmClient llm = mock(LlmClient.class);
        when(llm.isConfigured()).thenReturn(false);

        List<String> result = new AllergenNormalizationService(llm, new ObjectMapper())
                .normalize("", "对虾过敏");

        assertEquals(List.of("对虾", "虾", "虾仁"), result);
    }

    @Test
    void keepsLocalTermsWhenModelReturnsInvalidJson() {
        LlmClient llm = mock(LlmClient.class);
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(any())).thenReturn(LlmResult.ok(new LlmResponse(
                "not-json", List.of(), "stop", new LlmUsage(0, 0, 0), "test"), 1, 0));

        List<String> result = new AllergenNormalizationService(llm, new ObjectMapper())
                .normalize("", "花生过敏");

        assertEquals(List.of("花生"), result);
    }
}
