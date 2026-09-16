package com.moodrecipe.backend.agent;

/** 单次调用的 token 消耗，用于成本与降级观测。 */
public record LlmUsage(int promptTokens, int completionTokens, int totalTokens) {

    public static final LlmUsage UNKNOWN = new LlmUsage(0, 0, 0);
}
