package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.model.RecordRequest;
import com.moodrecipe.backend.repository.RecipeInteractionRepository;
import com.moodrecipe.backend.repository.UserRecordRepository;
import com.moodrecipe.backend.service.RecommendationExposureService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class RecordControllerTest {
    @Test
    void rejectsImpossibleRecordDateBeforeSaving() {
        UserRecordRepository records = mock(UserRecordRepository.class);
        RecordController controller = new RecordController(records,
                mock(RecipeInteractionRepository.class), mock(RecommendationExposureService.class));
        RecordRequest request = new RecordRequest("https://example.com/a.jpg", "番茄炒蛋", "平静",
                "", null, null, 20, "2026-02-31");

        var response = controller.save("user-1", request);

        assertEquals(400, response.getCode());
        verifyNoInteractions(records);
    }
}
