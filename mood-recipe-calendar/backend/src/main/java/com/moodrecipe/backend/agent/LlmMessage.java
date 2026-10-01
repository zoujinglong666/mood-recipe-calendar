package com.moodrecipe.backend.agent;

/**
 * 一条对话消息；role 取 system / user / assistant / tool。
 *
 * imageUrl 用于多模态（视觉）调用：非空时，该条消息的 content 会组装成
 * OpenAI 兼容的 content 数组 [ {type:text}, {type:image_url} ]，供视觉模型看图。
 */
public record LlmMessage(String role, String content, String name, String toolCallId, String imageUrl) {

    public static LlmMessage system(String content) {
        return new LlmMessage("system", content, null, null, null);
    }

    public static LlmMessage user(String content) {
        return new LlmMessage("user", content, null, null, null);
    }

    /** 带图消息：文本提示 + 图片（公网可访问 URL 或 data:image/...;base64,xxx）。 */
    public static LlmMessage userWithImage(String content, String imageUrl) {
        return new LlmMessage("user", content, null, null, imageUrl);
    }

    public static LlmMessage assistant(String content) {
        return new LlmMessage("assistant", content, null, null, null);
    }

    public static LlmMessage tool(String toolCallId, String name, String content) {
        return new LlmMessage("tool", content, name, toolCallId, null);
    }
}
