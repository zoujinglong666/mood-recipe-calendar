package com.moodrecipe.backend.model;

import java.util.List;

public record LearningReceipt(String status, String title, List<LearningReceiptItem> items) {
    public static LearningReceipt learned(List<LearningReceiptItem> items) {
        return new LearningReceipt("LEARNED", "锅仔记住了", List.copyOf(items));
    }

    public static LearningReceipt savedOnly() {
        return new LearningReceipt("SAVED_ONLY", "记录已保存，本次没有新增记忆", List.of());
    }

    public static LearningReceipt learningUnavailable() {
        return new LearningReceipt("SAVED_ONLY", "记录已保存，锅仔稍后再整理", List.of());
    }
}
