package com.moodrecipe.backend.model;

import com.moodrecipe.backend.entity.UserRecord;

import java.util.List;

/** 记录保存响应：newlyUnlocked 是本次新解锁的成就名（前端用于庆祝提示），无新解锁时为空列表。 */
public record RecordSaveResponse(UserRecord record, LearningReceipt learningReceipt, List<String> newlyUnlocked) { }
