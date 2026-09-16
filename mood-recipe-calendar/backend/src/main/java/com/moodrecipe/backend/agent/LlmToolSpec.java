package com.moodrecipe.backend.agent;

/** 工具的 JSON Schema 声明，供模型决定要不要调用。 */
public record LlmToolSpec(String name, String description, String parametersJson) {
}
