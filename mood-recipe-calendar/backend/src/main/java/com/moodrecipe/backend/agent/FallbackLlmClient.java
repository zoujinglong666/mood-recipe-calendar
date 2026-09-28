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
        return result.ok() || !result.retryable() ? result : fallback.complete(request);
    }
    @Override public boolean isConfigured() { return primary.isConfigured() || fallback.isConfigured(); }
    @Override public String model() { return primary.model() + " -> " + fallback.model(); }
    @Override public boolean supportsTools() { return primary.supportsTools() || fallback.supportsTools(); }
}
