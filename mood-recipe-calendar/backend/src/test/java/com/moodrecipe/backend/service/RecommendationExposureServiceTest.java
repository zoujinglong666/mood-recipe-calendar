package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.RecommendationExposure;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.repository.RecommendationExposureRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendationExposureServiceTest {
    @Test
    void recordsOwnedFeedbackWithoutRecipeContent() {
        RecommendationExposureRepository repository = mock(RecommendationExposureRepository.class);
        RecommendationExposure exposure = new RecommendationExposure();
        exposure.setId("exposure-1");
        exposure.setOpenid("user-1");
        when(repository.findById("exposure-1")).thenReturn(Optional.of(exposure));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        boolean saved = new RecommendationExposureService(repository)
                .feedback("user-1", "exposure-1", "LIKE");

        assertTrue(saved);
        assertTrue(exposure.isLiked());
        verify(repository).save(exposure);
    }

    @Test
    void generatedExposureStoresOnlyDishHash() {
        RecommendationExposureRepository repository = mock(RecommendationExposureRepository.class);
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        Recipe recipe = new Recipe();
        recipe.setName("番茄炒蛋");

        new RecommendationExposureService(repository).recordShown("user-1", recipe, "AI");

        verify(repository).save(any(RecommendationExposure.class));
    }
}
