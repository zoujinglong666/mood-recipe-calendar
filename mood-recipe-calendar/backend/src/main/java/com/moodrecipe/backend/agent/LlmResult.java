package com.moodrecipe.backend.agent;

/**
 * 模型调用结果：成功与失败都在这里表达，调用方据此决定是重试、换策略还是展示降级原因。
 */
public record LlmResult(LlmResponse response, Failure failure, int attempts, long latencyMs) {

    public enum Failure {
        NONE,
        NOT_CONFIGURED,
        TIMEOUT,
        RATE_LIMITED,
        SERVER_ERROR,
        BAD_REQUEST,
        EMPTY_OUTPUT,
        UNPARSEABLE_JSON
    }

    public boolean ok() {
        return failure == Failure.NONE && response != null;
    }

    public java.util.Optional<LlmResponse> value() {
        return java.util.Optional.ofNullable(response);
    }

    public String text() {
        return response == null ? "" : response.content();
    }

    public boolean retryable() {
        return switch (failure) {
            case TIMEOUT, RATE_LIMITED, SERVER_ERROR, EMPTY_OUTPUT, UNPARSEABLE_JSON -> true;
            default -> false;
        };
    }

    /** 面向用户/运维的中文降级原因，可直接展示。 */
    public String reason() {
        return switch (failure) {
            case NONE -> "";
            case NOT_CONFIGURED -> "模型服务未配置（缺少 AGNES_API_KEY 或模型名）";
            case TIMEOUT -> "模型响应超时";
            case RATE_LIMITED -> "模型服务限流";
            case SERVER_ERROR -> "模型服务暂时不可用";
            case BAD_REQUEST -> "模型拒绝了本次请求（提示词或参数不被支持）";
            case EMPTY_OUTPUT -> "模型返回了空内容";
            case UNPARSEABLE_JSON -> "模型没有按约定返回结构化结果";
        };
    }

    public static LlmResult ok(LlmResponse response, int attempts, long latencyMs) {
        return new LlmResult(response, Failure.NONE, attempts, latencyMs);
    }

    public static LlmResult failed(Failure failure, int attempts, long latencyMs) {
        return new LlmResult(null, failure, attempts, latencyMs);
    }
}
