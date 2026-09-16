package com.moodrecipe.backend.agent;

import java.util.List;
import java.util.Optional;

/** 一次模型调用请求。purpose 只用于日志与链路追踪，绝不写入提示词。 */
public record LlmRequest(String purpose,
                         String system,
                         List<LlmMessage> messages,
                         double temperature,
                         int maxTokens,
                         TimeoutTier tier,
                         boolean jsonMode,
                         List<LlmToolSpec> tools) {

    public LlmRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
        tier = tier == null ? TimeoutTier.STANDARD : tier;
        maxTokens = Math.max(64, maxTokens);
    }

    public static LlmRequest text(String purpose, String system, String user, double temperature, int maxTokens,
                                  TimeoutTier tier) {
        return new LlmRequest(purpose, system, List.of(LlmMessage.user(user)), temperature, maxTokens, tier, false, List.of());
    }

    /** 结构化输出：客户端会强制 JSON 模式，并在解析失败时自动做一次修复重试。 */
    public static LlmRequest json(String purpose, String system, String user, double temperature, int maxTokens,
                                  TimeoutTier tier) {
        return new LlmRequest(purpose, system, List.of(LlmMessage.user(user)), temperature, maxTokens, tier, true, List.of());
    }

    public static LlmRequest toolLoop(String purpose, String system, String user, double temperature, int maxTokens,
                                      TimeoutTier tier, List<LlmToolSpec> tools) {
        return new LlmRequest(purpose, system, List.of(LlmMessage.user(user)), temperature, maxTokens, tier, true, tools);
    }
}
