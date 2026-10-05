package com.moodrecipe.backend.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.service.UsageQuotaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * 会员分级模型路由（@Primary，替换直接注入 FallbackLlmClient 的位置）。
 *
 * 路由规则：
 * 1. ai.llm.paid.enabled=false（默认）或 DeepSeek 未配置时，行为与直接调用免费 FallbackLlmClient 完全一致，
 *    绝不改变任何现有服务；
 * 2. 仅当付费层启用、已配置 DeepSeek key、且本次请求来自会员时，才分流到 DeepSeek：
 *    - 重质量任务（菜单规划 / 智能体对话）走 planning-model（默认 deepseek-v4-pro）；
 *    - 高频短任务（如过敏归一化）走 flash-model（默认 deepseek-flash）；
 * 3. 非会员、或会员但 DeepSeek 未就绪时，一律回落免费 Agnes。
 *
 * DeepSeek 完全 OpenAI 兼容，直接复用 AgnesLlmClient 的传输实现（含超时分级 / 失败重试 / 工具协议降级）。
 */
@Service
@Primary
public class TieredLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(TieredLlmClient.class);

    /** 过敏归一化这类高频短任务走 Flash 模型，其余走 Pro。 */
    private static final String FLASH_PURPOSE = "allergen-normalization";

    private final LlmClient freeClient;
    private final LlmClient paidClient;
    private final UsageQuotaService quota;
    private final boolean paidEnabled;
    private final String planningModel;
    private final String flashModel;

    public TieredLlmClient(FallbackLlmClient freeClient,
                           UsageQuotaService quota,
                           ObjectMapper json,
                           @Value("${ai.llm.paid.enabled:false}") boolean paidEnabled,
                           @Value("${ai.llm.paid.api-key:}") String paidApiKey,
                           @Value("${ai.llm.paid.base-url:https://api.deepseek.com/chat/completions}") String paidBaseUrl,
                           @Value("${ai.llm.paid.planning-model:deepseek-v4-pro}") String planningModel,
                           @Value("${ai.llm.paid.flash-model:deepseek-flash}") String flashModel,
                           @Value("${ai.llm.max-attempts:2}") int maxAttempts,
                           @Value("${ai.llm.tool-calling:true}") boolean toolCallingEnabled,
                           @Value("${ai.llm.paid.max-concurrency:16}") int paidMaxConcurrency) {
        this.freeClient = freeClient;
        this.quota = quota;
        this.paidEnabled = paidEnabled;
        this.planningModel = planningModel == null || planningModel.isBlank() ? "deepseek-v4-pro" : planningModel.trim();
        this.flashModel = flashModel == null || flashModel.isBlank() ? "deepseek-flash" : flashModel.trim();
        String key = paidApiKey == null ? "" : paidApiKey.trim();
        String url = paidBaseUrl == null ? "" : paidBaseUrl.trim();
        // 付费层并发闸门独立于免费层，避免被 agnes 免费额度挤占（默认 16 permits）。
        this.paidClient = new AgnesLlmClient(json, key, url, this.planningModel, maxAttempts, toolCallingEnabled, paidMaxConcurrency);
    }

    @Override
    public LlmResult complete(LlmRequest request) {
        if (!paidEnabled) {
            return freeClient.complete(request);
        }
        boolean paidConfigured = paidClient.isConfigured();
        boolean isMember = request.openid() != null && !request.openid().isBlank() && quota.member(request.openid());
        if (!paidConfigured || !isMember) {
            if (isMember && !paidConfigured) {
                log.warn("会员请求本应走 DeepSeek，但 DeepSeek 未配置，回落免费模型 purpose={}", request.purpose());
            }
            return freeClient.complete(request);
        }
        String model = FLASH_PURPOSE.equals(request.purpose()) ? flashModel : planningModel;
        log.info("会员请求走 DeepSeek purpose={} model={} openid={}", request.purpose(), model, mask(request.openid()));
        return paidClient.complete(request.withModelOverride(model));
    }

    @Override
    public boolean isConfigured() {
        return freeClient.isConfigured();
    }

    @Override
    public String model() {
        return paidEnabled
                ? freeClient.model() + " | 会员=" + paidClient.model()
                : freeClient.model();
    }

    @Override
    public boolean supportsTools() {
        return freeClient.supportsTools() || paidClient.supportsTools();
    }

    private static String mask(String openid) {
        if (openid == null || openid.length() <= 4) return openid;
        return openid.substring(0, 2) + "***" + openid.substring(openid.length() - 2);
    }
}
