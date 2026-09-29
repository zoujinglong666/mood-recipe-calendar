package com.moodrecipe.backend.service;

import com.moodrecipe.backend.agent.AgentMemoryStore;
import com.moodrecipe.backend.agent.MemoryItem;
import com.moodrecipe.backend.entity.RecipeInteraction;
import com.moodrecipe.backend.repository.RecipeInteractionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RecordLearningServiceTest {

    @Test
    void writesMadeLikedDifficultyAndLeftoverSignals() {
        RecipeInteractionRepository interactions = mock(RecipeInteractionRepository.class);
        RecommendationExposureService exposures = mock(RecommendationExposureService.class);
        AgentMemoryStore memories = mock(AgentMemoryStore.class);
        when(memories.personalizationEnabled("user-1")).thenReturn(true);
        when(memories.remember(any())).thenAnswer(invocation -> {
            AgentMemoryStore.RememberCommand command = invocation.getArgument(0);
            return new MemoryItem(command.key(), command.value(), "BEHAVIOR_SIGNAL", command.confidence(),
                    command.source(), command.evidence(), null, "刚写入");
        });
        RecordLearningService service = new RecordLearningService(interactions, exposures, memories);

        var receipt = service.learn("user-1", "12", "exp-1", true, true, true);

        ArgumentCaptor<RecipeInteraction> interaction = ArgumentCaptor.forClass(RecipeInteraction.class);
        verify(interactions, org.mockito.Mockito.times(2)).save(interaction.capture());
        assertEquals(java.util.List.of("MADE", "LIKE"),
                interaction.getAllValues().stream().map(RecipeInteraction::getAction).toList());
        assertTrue(interaction.getAllValues().stream().allMatch(item -> item.getRecipeId().equals(12L)));
        verify(exposures).feedback("user-1", "exp-1", "MADE");
        verify(exposures).feedback("user-1", "exp-1", "LIKE");

        ArgumentCaptor<AgentMemoryStore.RememberCommand> memory =
                ArgumentCaptor.forClass(AgentMemoryStore.RememberCommand.class);
        verify(memories, org.mockito.Mockito.times(3)).remember(memory.capture());
        assertEquals(
                java.util.List.of(
                        AgentMemoryStore.KEY_SIMPLE,
                        AgentMemoryStore.KEY_MAX_MINUTES,
                        RecordLearningService.KEY_AVOID_LEFTOVER),
                memory.getAllValues().stream().map(AgentMemoryStore.RememberCommand::key).toList());
        assertEquals(4, receipt.items().size());
        assertEquals("LEARNED", receipt.status());
    }

    @Test
    void doesNotDuplicateMadeInteraction() {
        RecipeInteractionRepository interactions = mock(RecipeInteractionRepository.class);
        RecommendationExposureService exposures = mock(RecommendationExposureService.class);
        AgentMemoryStore memories = mock(AgentMemoryStore.class);
        when(memories.personalizationEnabled("user-1")).thenReturn(true);
        when(interactions.existsByOpenidAndRecipeIdAndAction("user-1", 12L, "MADE")).thenReturn(true);
        RecordLearningService service = new RecordLearningService(interactions, exposures, memories);

        var receipt = service.learn("user-1", "12", null, false, false, false);

        verify(interactions, never()).save(any());
        assertTrue(receipt.items().isEmpty());
        assertEquals("SAVED_ONLY", receipt.status());
    }

    @Test
    void skipsOrdinaryLearningWhenPersonalizationIsDisabled() {
        RecipeInteractionRepository interactions = mock(RecipeInteractionRepository.class);
        RecommendationExposureService exposures = mock(RecommendationExposureService.class);
        AgentMemoryStore memories = mock(AgentMemoryStore.class);
        when(memories.personalizationEnabled("user-1")).thenReturn(false);
        RecordLearningService service = new RecordLearningService(interactions, exposures, memories);

        var receipt = service.learn("user-1", "12", "exp-1", true, true, true);

        assertEquals("SAVED_ONLY", receipt.status());
        verifyNoInteractions(interactions, exposures);
        verify(memories, never()).remember(any());
    }

    @Test
    void reportsOnlyTheMemoryWritesThatActuallySucceeded() {
        RecipeInteractionRepository interactions = mock(RecipeInteractionRepository.class);
        RecommendationExposureService exposures = mock(RecommendationExposureService.class);
        AgentMemoryStore memories = mock(AgentMemoryStore.class);
        when(memories.personalizationEnabled("user-1")).thenReturn(true);
        when(memories.remember(any())).thenAnswer(invocation -> {
            AgentMemoryStore.RememberCommand command = invocation.getArgument(0);
            if (AgentMemoryStore.KEY_SIMPLE.equals(command.key())) throw new IllegalStateException("temporary failure");
            return new MemoryItem(command.key(), command.value(), "BEHAVIOR_SIGNAL", command.confidence(),
                    command.source(), command.evidence(), null, "刚写入");
        });
        RecordLearningService service = new RecordLearningService(interactions, exposures, memories);

        var receipt = service.learn("user-1", null, null, false, true, true);

        assertEquals("LEARNED", receipt.status());
        assertEquals(java.util.List.of(AgentMemoryStore.KEY_MAX_MINUTES, RecordLearningService.KEY_AVOID_LEFTOVER),
                receipt.items().stream().map(item -> item.memoryKey()).toList());
    }
}
