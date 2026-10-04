package com.moodrecipe.backend.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.service.AgentConversationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentReflectionServiceTest {

    @Mock
    private LlmClient llm;
    @Mock
    private AgentMemoryStore store;
    @Mock
    private AgentConversationService conversations;
    private final ObjectMapper json = new ObjectMapper();

    private AgentReflectionService service() {
        return new AgentReflectionService(llm, store, conversations, json);
    }

    private AgentConversationService.Snapshot snapshot() {
        return new AgentConversationService.Snapshot("cid", null, null, null,
                List.of(new AgentConversationService.TranscriptMessage("user", "我想吃清淡点", List.of(), false)));
    }

    private void stubLearning(String payload) {
        when(llm.isConfigured()).thenReturn(true);
        when(conversations.latest("u1")).thenReturn(Optional.of(snapshot()));
        when(llm.complete(any())).thenReturn(
                LlmResult.ok(new LlmResponse(payload, null, "stop", null, "fake"), 1, 1L));
        when(store.recall(any(), any(), anyInt())).thenReturn(List.of());
        when(store.remember(any())).thenReturn(
                new MemoryItem("lesson.1", "x", "c", 0.7, AgentMemoryStore.SRC_LEARNED, "e", null, "r"));
    }

    @Test
    void reflectWritesLessonToMemory() {
        stubLearning("[\"少放盐更清淡\"]");
        service().reflectOnRating("u1", "不满意", "太油腻");
        ArgumentCaptor<AgentMemoryStore.RememberCommand> captor =
                ArgumentCaptor.forClass(AgentMemoryStore.RememberCommand.class);
        verify(store).remember(captor.capture());
        AgentMemoryStore.RememberCommand cmd = captor.getValue();
        assertTrue(cmd.key().startsWith("lesson."));
        assertEquals("少放盐更清淡", cmd.value());
        assertTrue(cmd.evidence().contains("复盘"));
        assertEquals(0.7, cmd.confidence(), 0.0001);
        assertEquals(AgentMemoryStore.SRC_LEARNED, cmd.source());
    }

    @Test
    void reflectSkipsWhenLlmNotConfigured() {
        when(llm.isConfigured()).thenReturn(false);
        service().reflectOnRating("u1", "满意", "");
        verifyNoInteractions(store);
    }

    @Test
    void reflectSkipsWhenNoTranscript() {
        when(llm.isConfigured()).thenReturn(true);
        when(conversations.latest("u1")).thenReturn(Optional.empty());
        service().reflectOnRating("u1", "满意", "");
        verify(llm, never()).complete(any());
        verify(store, never()).remember(any());
    }

    @Test
    void reflectSkipsWhenModelReturnsNoArray() {
        when(llm.isConfigured()).thenReturn(true);
        when(conversations.latest("u1")).thenReturn(Optional.of(snapshot()));
        when(llm.complete(any())).thenReturn(
                LlmResult.ok(new LlmResponse("{\"foo\":\"bar\"}", null, "stop", null, "fake"), 1, 1L));
        service().reflectOnRating("u1", "满意", "");
        verify(store, never()).remember(any());
        verify(store, never()).recall(any(), any(), anyInt());
    }

    @Test
    void lessonsReturnsLearnedText() {
        when(store.recall("u1", AgentMemoryStore.Scene.DIALOGUE, 40)).thenReturn(
                List.of(new MemoryItem("lesson.1", "少放盐更清淡", "c", 0.7,
                        AgentMemoryStore.SRC_LEARNED, "e", null, "r")));
        String lessons = service().lessons("u1");
        assertTrue(lessons.contains("少放盐更清淡"));
    }

    @Test
    void lessonsEmptyForBlankOpenid() {
        assertEquals("", service().lessons(""));
        verify(store, never()).recall(any(), any(), anyInt());
    }
}
