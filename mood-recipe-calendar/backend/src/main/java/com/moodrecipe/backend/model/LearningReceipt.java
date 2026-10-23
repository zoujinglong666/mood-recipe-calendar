package com.moodrecipe.backend.model;

import java.util.List;

public record LearningReceipt(String status, String title, List<LearningReceiptItem> items) {
    public static LearningReceipt learned(List<LearningReceiptItem> items) {
        return new LearningReceipt("LEARNED", "锅仔记住了", List.copyOf(items));
    }

    /** 无新增记忆也要给温度：把「什么都没发生」翻成「下次可以多说几句」的邀请。 */
    public static LearningReceipt savedOnly() {
        return new LearningReceipt("SAVED_ONLY", "这一顿收好了，下次多跟锅仔说几句", List.of());
    }

    public static LearningReceipt learningUnavailable() {
        return new LearningReceipt("SAVED_ONLY", "这一顿收好了，味道锅仔稍后再理", List.of());
    }
}
