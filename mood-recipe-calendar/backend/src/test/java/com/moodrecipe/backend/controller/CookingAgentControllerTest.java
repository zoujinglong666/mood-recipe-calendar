package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.service.CookingAgentService;
import com.moodrecipe.backend.service.CookingLearningService;
import com.moodrecipe.backend.service.WechatContentSafetyService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CookingAgentControllerTest {
    @Test
    void rejectsOversizedQuestionBeforeCallingAgent() {
        CookingAgentService agent = mock(CookingAgentService.class);
        var controller = new CookingAgentController(agent, mock(CookingLearningService.class), allowSafety());
        var request = new CookingAgentService.TurnRequest(1L, 0, "s", "问".repeat(501), "ASK", true, List.of());

        var response = controller.turn("user-1", request);

        assertEquals(400, response.getCode());
        verifyNoInteractions(agent);
    }

    @Test
    void rejectsInvalidFeedbackType() {
        CookingLearningService learning = mock(CookingLearningService.class);
        doThrow(new IllegalArgumentException("反馈类型无效")).when(learning)
                .record(anyString(), any(), any(), any(), anyString());
        var controller = new CookingAgentController(mock(CookingAgentService.class), learning, allowSafety());

        var response = controller.feedback("user-1",
                new CookingAgentController.FeedbackRequest(1L, 0, "煎", "UNKNOWN"));

        assertEquals(400, response.getCode());
    }

    private WechatContentSafetyService allowSafety() {
        WechatContentSafetyService safety = mock(WechatContentSafetyService.class);
        when(safety.allowsText(anyString(), any(String[].class))).thenReturn(true);
        return safety;
    }
}
