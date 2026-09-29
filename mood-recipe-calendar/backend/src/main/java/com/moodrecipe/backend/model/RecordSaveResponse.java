package com.moodrecipe.backend.model;

import com.moodrecipe.backend.entity.UserRecord;

public record RecordSaveResponse(UserRecord record, LearningReceipt learningReceipt) { }
