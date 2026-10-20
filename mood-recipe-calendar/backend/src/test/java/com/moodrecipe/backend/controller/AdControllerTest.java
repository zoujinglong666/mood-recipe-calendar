package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.service.AdRewardService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AdControllerTest {
    private final AdRewardService adRewards = mock(AdRewardService.class);
    private final AdController controller = new AdController(adRewards);

    @Test
    void rewardRecordsAndReturnsGranted() {
        Map<String, String> body = Map.of("type", AdRewardService.RESHUFFLE_UNLIMITED, "adUnitId", "adunit-x");

        ApiResponse<?> resp = controller.reward("user-1", body);

        assertEquals(0, resp.getCode());
        verify(adRewards).record(eq("user-1"), eq(AdRewardService.RESHUFFLE_UNLIMITED), eq("adunit-x"));
    }

    @Test
    void rewardRejectsUnknownType() {
        Map<String, String> body = Map.of("type", "BOGUS", "adUnitId", "x");

        ApiResponse<?> resp = controller.reward("user-1", body);

        assertEquals(400, resp.getCode());
        verify(adRewards, never()).record(anyString(), anyString(), anyString());
    }
}
