package com.moodrecipe.backend.agent;

/**
 * 模型访问边界。所有智能体能力都只依赖这个接口，
 * 便于在测试里替换成假实现，也便于未来换供应商。
 */
public interface LlmClient {

    LlmResult complete(LlmRequest request);

    /** 是否已完成配置；未配置时所有调用直接返回 NOT_CONFIGURED。 */
    boolean isConfigured();

    String model();

    /** 供应商是否支持原生 function calling；不支持时智能体自动切换到 JSON 工具协议。 */
    boolean supportsTools();
}
