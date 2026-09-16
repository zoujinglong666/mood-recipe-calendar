package com.moodrecipe.backend.agent;

/** 一条对话消息；role 取 system / user / assistant / tool。 */
public record LlmMessage(String role, String content, String name, String toolCallId) {

    public static LlmMessage system(String content) {
        return new LlmMessage("system", content, null, null);
    }

    public static LlmMessage user(String content) {
        return new LlmMessage("user", content, null, null);
    }

    public static LlmMessage assistant(String content) {
        return new LlmMessage("assistant", content, null, null);
    }

    public static LlmMessage tool(String toolCallId, String name, String content) {
        return new LlmMessage("tool", content, name, toolCallId);
    }
}
