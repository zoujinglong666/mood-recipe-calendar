package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.repository.RecipeInteractionRepository;
import com.moodrecipe.backend.repository.RecipeRepository;
import com.moodrecipe.backend.entity.UserEntitlement;
import com.moodrecipe.backend.service.GuozaiAgent;
import com.moodrecipe.backend.service.OperationalEventService;
import com.moodrecipe.backend.service.RecommendationJobService;
import com.moodrecipe.backend.service.VirtualCommerceService;
import com.moodrecipe.backend.service.RecommendationExposureService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

/**
 * RecipeController 现在是薄委托层，推荐逻辑由 GuozaiAgent 承担。
 * 本地回退的详细测试见 GuozaiAgentTest。
 */
class RecipeControllerTest {

    private RecipeController buildController(GuozaiAgent agent) {
        return buildController(agent, mock(VirtualCommerceService.class));
    }

    private RecipeController buildController(GuozaiAgent agent, VirtualCommerceService commerce) {
        return new RecipeController(
                mock(RecipeRepository.class),
                mock(RecipeInteractionRepository.class),
                agent,
                commerce,
                mock(OperationalEventService.class),
                mock(RecommendationJobService.class),
                mock(RecommendationExposureService.class));
    }

    @Test
    void delegatesRecommendationToGuozaiAgent() {
        GuozaiAgent agent = mock(GuozaiAgent.class);
        Recipe expected = new Recipe();
        expected.setId(1L);
        expected.setName("番茄炒蛋");
        when(agent.recommend(anyString(), anyString(), any())).thenReturn(expected);

        RecipeController controller = buildController(agent);
        Recipe result = controller.recommend("user-1", "平静").getData();
        assertEquals("番茄炒蛋", result.getName());
    }

    @Test
    void restoresEntitlementOnceWhenDeepRecommendationThrows() {
        GuozaiAgent agent = mock(GuozaiAgent.class);
        VirtualCommerceService commerce = mock(VirtualCommerceService.class);
        UserEntitlement entitlement = new UserEntitlement();
        entitlement.setId(7L);
        when(commerce.consumeEntitlement("user-1", "AI_DEEP_RECOMMEND"))
                .thenReturn(Optional.of(entitlement));
        when(agent.deepRecommend(anyString(), anyString(), any(), any(), any()))
                .thenThrow(new IllegalStateException("boom"));

        RecipeController controller = buildController(agent, commerce);
        var response = controller.deepRecommend("user-1",
                new RecipeController.DeepRecommendRequest("平静", "番茄", "30", "清淡"));

        assertEquals(503, response.getCode());
        verify(commerce, times(1)).restoreEntitlement(7L);
    }

    @Test
    void rejectsInvalidDeepRequestBeforeConsumingEntitlement() {
        VirtualCommerceService commerce = mock(VirtualCommerceService.class);
        RecipeController controller = buildController(mock(GuozaiAgent.class), commerce);

        var response = controller.deepRecommend("user-1",
                new RecipeController.DeepRecommendRequest("", "番茄", "30", "清淡"));

        assertEquals(400, response.getCode());
        verifyNoInteractions(commerce);
    }

    @Test
    void keepsDatabaseRecipeFeedbackCompatible() {
        RecipeRepository recipes = mock(RecipeRepository.class);
        RecipeInteractionRepository interactions = mock(RecipeInteractionRepository.class);
        when(recipes.existsById(1L)).thenReturn(true);
        RecipeController controller = new RecipeController(
                recipes,
                interactions,
                mock(GuozaiAgent.class),
                mock(VirtualCommerceService.class),
                mock(OperationalEventService.class),
                mock(RecommendationJobService.class),
                mock(RecommendationExposureService.class));

        var response = controller.feedback(1L, "user-1",
                new RecipeController.RecipeFeedbackRequest("LIKE"));

        assertEquals(0, response.getCode());
        verify(interactions).save(any());
    }
}
