package com.moodrecipe.backend.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 新增/更新每日记录的请求体
 */
public record RecordRequest(
        @Size(max = 2048) String imageUrl,
        @NotBlank @Size(max = 100) String dishName,
        @NotBlank @Size(max = 20) String moodTag,
        @Size(max = 200) String note,
        Long recipeId,
        @Size(max = 36) String exposureId,
        @Size(max = 64) String clientRequestId,
        @Min(0) @Max(1440) Integer cookingTime,
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$") String recordDate,
        List<@Size(max = 2048) String> imageUrls,
        Boolean liked,
        Boolean tooHard,
        Boolean leftover
) {
    public RecordRequest(String imageUrl, String dishName, String moodTag, String note, Long recipeId,
                         String exposureId, String clientRequestId, Integer cookingTime, String recordDate) {
        this(imageUrl, dishName, moodTag, note, recipeId, exposureId, clientRequestId, cookingTime,
                recordDate, List.of(), null, null, null);
    }

    public RecordRequest(String imageUrl, String dishName, String moodTag, String note, Long recipeId,
                         String exposureId, String clientRequestId, Integer cookingTime, String recordDate,
                         List<String> imageUrls) {
        this(imageUrl, dishName, moodTag, note, recipeId, exposureId, clientRequestId, cookingTime,
                recordDate, imageUrls, null, null, null);
    }
}
