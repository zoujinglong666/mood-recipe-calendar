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
import java.util.List;
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
    void rejectsRecipeRecordWithoutAnUploadedPhoto() {
        UserRecordRepository records = mock(UserRecordRepository.class);
        RecordController controller = new RecordController(records,
                mock(RecipeInteractionRepository.class), mock(RecommendationExposureService.class), allowSafety());
        RecordRequest request = new RecordRequest("", "番茄炒蛋", "平静", "", "1", null,
                "request-1", 20, "2026-09-22");

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
        RecordRequest request = new RecordRequest("https://example.com/a.jpg", "番茄炒蛋", "平静", "", "1", null,
                "request-1", 20, null);

        var response = controller.save("user-1", request);

        assertEquals(9L, response.getData().getId());
        verify(records, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void fetchesOnlyTheOwnersRecordForEditing() {
        UserRecordRepository records = mock(UserRecordRepository.class);
        UserRecord record = new UserRecord();
        record.setId(12L);
        record.setOpenid("user-1");
        when(records.findById(12L)).thenReturn(Optional.of(record));
        RecordController controller = new RecordController(records,
                mock(RecipeInteractionRepository.class), mock(RecommendationExposureService.class), allowSafety());

        var response = controller.getById(12L, "user-1");

        assertEquals(12L, response.getData().getId());
    }

    @Test
    void rejectsUpdateForAnotherUsersRecord() {
        UserRecordRepository records = mock(UserRecordRepository.class);
        UserRecord record = new UserRecord();
        record.setId(12L);
        record.setOpenid("user-2");
        when(records.findById(12L)).thenReturn(Optional.of(record));
        RecordController controller = new RecordController(records,
                mock(RecipeInteractionRepository.class), mock(RecommendationExposureService.class), allowSafety());
        RecordRequest request = new RecordRequest("https://example.com/a.jpg", "番茄炒蛋", "平静", "", null, null,
                null, 20, "2026-09-22", List.of("https://example.com/a.jpg", "https://example.com/b.jpg"));

        var response = controller.update(12L, "user-1", request);

        assertEquals(403, response.getCode());
        verify(records, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private WechatContentSafetyService allowSafety() {
        return new WechatContentSafetyService(new ObjectMapper(), "", "", false, false);
    }
}
