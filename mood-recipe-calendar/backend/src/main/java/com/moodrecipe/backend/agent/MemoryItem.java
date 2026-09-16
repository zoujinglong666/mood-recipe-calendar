package com.moodrecipe.backend.agent;

import java.time.LocalDateTime;

/** 一条被检索出来的记忆，带置信度、来源、证据和"为什么这次用上它"。 */
public record MemoryItem(String key,
                         String value,
                         double confidence,
                         String source,
                         String evidence,
                         LocalDateTime updatedAt,
                         String reason) {

    public boolean confident() {
        return confidence >= 0.6;
    }
}
