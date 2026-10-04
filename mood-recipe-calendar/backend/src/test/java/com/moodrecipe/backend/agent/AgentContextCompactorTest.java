package com.moodrecipe.backend.agent;

import com.moodrecipe.backend.service.AgentConversationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentContextCompactorTest {

    @Mock
    private LlmClient llm;

    private AgentContextCompactor compactor() {
        return new AgentContextCompactor(llm);
    }

    private List<AgentConversationService.TranscriptMessage> history(int n) {
        var list = new java.util.ArrayList<AgentConversationService.TranscriptMessage>();
        for (int i = 0; i < n; i++) {
            list.add(new AgentConversationService.TranscriptMessage("user", "消息" + i, List.of(), false));
        }
        return list;
    }

    private void stubConfigured(String text) {
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(any())).thenReturn(
                LlmResult.ok(new LlmResponse(text, null, "stop", null, "fake"), 1, 1L));
    }

    @Test
    void returnsEmptyForBlankOpenid() {
        assertEquals("", compactor().compact("", history(8), 1));
        verifyNoInteractions(llm);
    }

    @Test
    void returnsEmptyForNullHistory() {
        assertEquals("", compactor().compact("u-null", null, 1));
        verifyNoInteractions(llm);
    }

    @Test
    void returnsEmptyWhenOldMessagesBelowThreshold() {
        // 8 条消息, recentCount=3 -> oldCount = 8 - 3 = 5 < MIN_OLD_MESSAGES(6)
        assertEquals("", compactor().compact("u-thresh", history(8), 3));
        verifyNoInteractions(llm);
    }

    @Test
    void summarizesWhenConfiguredAndEnoughOldMessages() {
        stubConfigured("较早对话的摘要");
        String result = compactor().compact("u-config", history(8), 1);
        assertEquals("较早对话的摘要", result);
        verify(llm).complete(any());
    }

    @Test
    void returnsEmptyWhenLlmNotConfigured() {
        when(llm.isConfigured()).thenReturn(false);
        assertEquals("", compactor().compact("u-unconfig", history(8), 1));
        verify(llm, never()).complete(any());
    }

    @Test
    void returnsEmptyWhenLlmFails() {
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(any())).thenReturn(LlmResult.failed(LlmResult.Failure.SERVER_ERROR, 1, 1L));
        assertEquals("", compactor().compact("u-fail", history(8), 1));
    }

    @Test
    void cachesSummaryAndReusesForSameOpenid() {
        stubConfigured("较早对话的摘要");
        AgentContextCompactor c = compactor();
        String first = c.compact("u-cache", history(8), 1);
        String second = c.compact("u-cache", history(8), 1);
        assertEquals("较早对话的摘要", first);
        assertEquals(first, second);
        verify(llm, times(1)).complete(any());
    }
}
