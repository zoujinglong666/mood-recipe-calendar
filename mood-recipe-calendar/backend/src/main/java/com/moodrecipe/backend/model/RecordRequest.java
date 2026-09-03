package com.moodrecipe.backend.model;

/**
 * 新增/更新每日记录的请求体
 */
public record RecordRequest(
        String openid,
        String imageUrl,
        String dishName,
        String moodTag,
        String note,
        String recipeId,
        Integer cookingTime,
        String recordDate
) {
}
