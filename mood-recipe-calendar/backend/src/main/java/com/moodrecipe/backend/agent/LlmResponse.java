package com.moodrecipe.backend.agent;

import java.util.List;

/** 模型回复：正文 + 可能的工具调用 + 用量。 */
public record LlmResponse(String content, List<LlmToolCall> toolCalls, String finishReason, LlmUsage usage, String model) {

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
