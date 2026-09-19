package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.model.RecordRequest;
import com.moodrecipe.backend.repository.RecipeInteractionRepository;
import com.moodrecipe.backend.repository.UserRecordRepository;
import com.moodrecipe.backend.service.RecommendationExposureService;
import com.moodrecipe.backend.service.WechatContentSafetyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Optional;
import com.moodrecipe.backend.entity.UserRecord;

class RecordControllerTest {
    @Test
    void rejectsImpossibleRecordDateBeforeSaving() {
        UserRecordRepository records = mock(UserRecordRepository.class);
        RecordController controller = new RecordController(records,
                mock(RecipeInteractionRepository.class), mock(RecommendationExposureService.class), allowSafety());
        RecordRequest request = new RecordRequest("https://example.com/a.jpg", "番茄炒蛋", "平静",
                "", null, null, "request-1", 20, "2026-02-31");

        var response = controller.save("user-1", request);

        assertEquals(400, response.getCode());
        verifyNoInteractions(records);
    }

    @Test
    void repeatedClientRequestReturnsExistingRecord() {
        UserRecordRepository records = mock(UserRecordRepository.class);
        UserRecord existing = new UserRecord();
        existing.setId(9L);
        when(records.findByOpenidAndClientRequestId("user-1", "request-1")).thenReturn(Optional.of(existing));
        RecordController controller = new RecordController(records,
                mock(RecipeInteractionRepository.class), mock(RecommendationExposureService.class), allowSafety());
        RecordRequest request = new RecordRequest("", "番茄炒蛋", "平静", "", "1", null,
                "request-1", 20, null);

        var response = controller.save("user-1", request);

        assertEquals(9L, response.getData().getId());
        verify(records, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private WechatContentSafetyService allowSafety() {
        return new WechatContentSafetyService(new ObjectMapper(), "", "", false, false);
    }
}
