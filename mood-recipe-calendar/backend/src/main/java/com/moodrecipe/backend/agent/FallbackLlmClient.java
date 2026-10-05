package com.moodrecipe.backend.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/** 主模型失败时才调用备用模型，业务层仍只依赖 LlmClient。 */
@Service
@Primary
public class FallbackLlmClient implements LlmClient {
    private final LlmClient primary;
    private final LlmClient fallback;

    public FallbackLlmClient(ObjectMapper json,
                             @Value("${ai.recipe.api-key:}") String apiKey,
                             @Value("${ai.recipe.base-url:https://apihub.agnes-ai.com/v1/chat/completions}") String baseUrl,
                             @Value("${ai.llm.primary-model:agnes-3.0-flash}") String primaryModel,
                             @Value("${ai.llm.fallback-model:agnes-2.5-flash}") String fallbackModel,
                             @Value("${ai.llm.max-attempts:2}") int maxAttempts,
                             @Value("${ai.llm.tool-calling:true}") boolean toolCallingEnabled) {
        this.primary = new AgnesLlmClient(json, apiKey, baseUrl, primaryModel, maxAttempts, toolCallingEnabled);
        this.fallback = new AgnesLlmClient(json, apiKey, baseUrl, fallbackModel, maxAttempts, toolCallingEnabled);
    }

    @Override public LlmResult complete(LlmRequest request) {
        LlmResult result = primary.complete(request);
        // 限流时主/备共用同一个 agnes key，回退只会多烧额度且必再 429，直接返回；
        // 只有其它可重试错误（如某模型不支持）才尝试备用模型。
        if (result.ok() || result.failure() == LlmResult.Failure.RATE_LIMITED) {
            return result;
        }
        return result.retryable() ? fallback.complete(request) : result;
    }
    @Override public boolean isConfigured() { return primary.isConfigured() || fallback.isConfigured(); }
    @Override public String model() { return primary.model() + " -> " + fallback.model(); }
    @Override public boolean supportsTools() { return primary.supportsTools() || fallback.supportsTools(); }
}
