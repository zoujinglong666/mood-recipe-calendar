package com.moodrecipe.backend.agent;

/** 模型主动发起的一次工具调用。 */
public record LlmToolCall(String id, String name, String argumentsJson) {
}
