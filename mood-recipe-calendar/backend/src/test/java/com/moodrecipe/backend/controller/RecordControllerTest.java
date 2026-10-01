package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.model.RecordRequest;
import com.moodrecipe.backend.repository.RecipeInteractionRepository;
import com.moodrecipe.backend.repository.UserRecordRepository;
import com.moodrecipe.backend.model.LearningReceipt;
import com.moodrecipe.backend.model.LearningReceiptItem;
import com.moodrecipe.backend.service.RecommendationExposureService;
import com.moodrecipe.backend.service.RecordLearningService;
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
import java.util.Map;
import com.moodrecipe.backend.entity.UserRecord;

class RecordControllerTest {
    @Test
    void yearStatsExposeTheSameStreakFieldsAsOverallStats() {
        UserRecordRepository records = mock(UserRecordRepository.class);
        UserRecord first = recordOn("2026-09-20");
        UserRecord second = recordOn("2026-09-21");
        UserRecord third = recordOn("2026-09-23");
        when(records.findByOpenidOrderByCreatedAtDesc("user-1"))
                .thenReturn(List.of(third, second, first));
        RecordController controller = new RecordController(records,
                mock(RecordLearningService.class), allowSafety());

        var response = controller.yearStats("user-1", 2026);
        Map<String, Object> stats = response.getData();

        assertEquals(0, stats.get("currentStreak"));
        assertEquals(2, stats.get("longestStreak"));
    }

    @Test
    void rejectsImpossibleRecordDateBeforeSaving() {
        UserRecordRepository records = mock(UserRecordRepository.class);
        RecordController controller = new RecordController(records,
                mock(RecordLearningService.class), allowSafety());
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
                mock(RecordLearningService.class), allowSafety());
        RecordRequest request = new RecordRequest("", "番茄炒蛋", "平静", "", 1L, null,
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
        RecordLearningService learning = mock(RecordLearningService.class);
        RecordController controller = new RecordController(records,
                learning, allowSafety());
        RecordRequest request = new RecordRequest("https://example.com/a.jpg", "番茄炒蛋", "平静", "", 1L, null,
                "request-1", 20, null);

        var response = controller.save("user-1", request);

        assertEquals(9L, response.getData().record().getId());
        assertEquals("SAVED_ONLY", response.getData().learningReceipt().status());
        verify(records, never()).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(learning);
    }

    @Test
    void returnsRecordAndLearningReceiptAfterFirstSave() {
        UserRecordRepository records = mock(UserRecordRepository.class);
        when(records.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            UserRecord saved = invocation.getArgument(0);
            saved.setId(15L);
            return saved;
        });
        RecordLearningService learning = mock(RecordLearningService.class);
        LearningReceipt receipt = LearningReceipt.learned(List.of(
                new LearningReceiptItem("SIMPLE", "下次优先简单菜", "preference.simpleDishes")));
        when(learning.learn("user-1", 1L, "exp-1", true, true, false)).thenReturn(receipt);
        RecordController controller = new RecordController(records, learning, allowSafety());
        RecordRequest request = new RecordRequest("https://example.com/a.jpg", "番茄炒蛋", "平静", "",
                1L, "exp-1", "request-1", 20, "2026-09-22", List.of(), true, true, false);

        var response = controller.save("user-1", request);

        assertEquals(15L, response.getData().record().getId());
        assertEquals(receipt, response.getData().learningReceipt());
        verify(learning).learn("user-1", 1L, "exp-1", true, true, false);
    }

    @Test
    void keepsSavedRecordWhenLearningFails() {
        UserRecordRepository records = mock(UserRecordRepository.class);
        when(records.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));
        RecordLearningService learning = mock(RecordLearningService.class);
        when(learning.learn("user-1", 1L, null, false, false, false))
                .thenThrow(new IllegalStateException("learning unavailable"));
        RecordController controller = new RecordController(records, learning, allowSafety());
        RecordRequest request = new RecordRequest("https://example.com/a.jpg", "番茄炒蛋", "平静", "",
                1L, null, "request-1", 20, "2026-09-22");

        var response = controller.save("user-1", request);

        assertEquals(0, response.getCode());
        assertEquals("番茄炒蛋", response.getData().record().getDishName());
        assertEquals("SAVED_ONLY", response.getData().learningReceipt().status());
        assertEquals("记录已保存，锅仔稍后再整理", response.getData().learningReceipt().title());
        verify(records).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void fetchesOnlyTheOwnersRecordForEditing() {
        UserRecordRepository records = mock(UserRecordRepository.class);
        UserRecord record = new UserRecord();
        record.setId(12L);
        record.setOpenid("user-1");
        when(records.findById(12L)).thenReturn(Optional.of(record));
        RecordController controller = new RecordController(records,
                mock(RecordLearningService.class), allowSafety());

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
                mock(RecordLearningService.class), allowSafety());
        RecordRequest request = new RecordRequest("https://example.com/a.jpg", "番茄炒蛋", "平静", "", null, null,
                null, 20, "2026-09-22", List.of("https://example.com/a.jpg", "https://example.com/b.jpg"));

        var response = controller.update(12L, "user-1", request);

        assertEquals(403, response.getCode());
        verify(records, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private WechatContentSafetyService allowSafety() {
        return new WechatContentSafetyService(new ObjectMapper(), "", "", false, false);
    }

    private UserRecord recordOn(String date) {
        UserRecord record = new UserRecord();
        record.setRecordDate(date);
        record.setDishName("番茄炒蛋");
        record.setMoodTag("平静");
        return record;
    }
}
