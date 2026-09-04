package com.moodrecipe.backend.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新增/更新每日记录的请求体
 */
public record RecordRequest(
        @NotBlank @Size(max = 2048) String imageUrl,
        @NotBlank @Size(max = 100) String dishName,
        @NotBlank @Size(max = 20) String moodTag,
        @Size(max = 200) String note,
        String recipeId,
        @Min(0) @Max(1440) Integer cookingTime,
        String recordDate
) {
}
