package com.moodrecipe.backend.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Agnes（OpenAI 兼容）Chat Completions 实现。
 *
 * 与旧实现的关键差别：
 * 1. 超时分级（FAST / STANDARD / LONG），不再所有请求都 40 秒；
 * 2. 失败分类 + 有限重试，而不是一次性吞掉异常；
 * 3. 支持 response_format=json_object，并在解析失败时做一次结构化修复重试；
 * 4. 支持 function calling；供应商不支持时自动降级为 JSON 工具协议；
 * 5. 记录 traceId / 耗时 / 用量 / 失败原因，供降级原因展示。
 *
 * 提示词与用户正文一律不写入日志。
 */
@Service
public class AgnesLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(AgnesLlmClient.class);
    private static final String REPAIR_HINT =
            "你上一次的输出不是合法的 JSON 对象。请只输出一个 JSON 对象，不要 Markdown 代码块，不要任何解释。";

    private final ObjectMapper json;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int maxAttempts;
    private final boolean toolCallingEnabled;
    private final AtomicBoolean toolsSupported;

    public AgnesLlmClient(ObjectMapper json,
                          @Value("${ai.recipe.api-key:}") String apiKey,
                          @Value("${ai.recipe.base-url:https://apihub.agnes-ai.com/v1/chat/completions}") String baseUrl,
                          @Value("${ai.recipe.model:agnes-2.5-flash}") String model,
                          @Value("${ai.llm.max-attempts:2}") int maxAttempts,
                          @Value("${ai.llm.tool-calling:true}") boolean toolCallingEnabled) {
        this.json = json;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.model = model == null ? "" : model.trim();
        this.maxAttempts = Math.max(1, Math.min(maxAttempts, 4));
        this.toolCallingEnabled = toolCallingEnabled;
        this.toolsSupported = new AtomicBoolean(toolCallingEnabled);
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isBlank() && !baseUrl.isBlank() && !model.isBlank();
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public boolean supportsTools() {
        return toolCallingEnabled && toolsSupported.get();
    }

    @Override
    public LlmResult complete(LlmRequest request) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        if (!isConfigured()) {
            log.warn("LLM 未配置，跳过调用 purpose={} traceId={}", request.purpose(), traceId);
            return LlmResult.failed(LlmResult.Failure.NOT_CONFIGURED, 0, 0L);
        }
        long start = System.currentTimeMillis();
        List<LlmMessage> messages = new ArrayList<>(request.messages());
        boolean useNativeTools = !request.tools().isEmpty() && supportsTools();
        boolean useJsonMode = request.jsonMode() && !useNativeTools;

        LlmResult.Failure failure = LlmResult.Failure.SERVER_ERROR;
        int attempts = 0;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            attempts = attempt;
            try {
                HttpResponse<String> response = send(request, messages, useNativeTools, useJsonMode);
                int status = response.statusCode();
                if (status < 200 || status >= 300) {
                    failure = classify(status);
                    if (status == 400 && useNativeTools) {
                        toolsSupported.set(false);
                        log.warn("供应商不支持 function calling，后续改用 JSON 工具协议 traceId={}", traceId);
                        // 同一轮内立刻用 JSON 协议重试一次，不浪费一次尝试
                        useNativeTools = false;
                        useJsonMode = request.jsonMode();
                        attempt--;
                        continue;
                    }
                    if (retryable(failure)) {
                        log.warn("LLM 返回可重试状态 purpose={} status={} attempt={} traceId={}",
                                request.purpose(), status, attempt, traceId);
                        backoff(attempt);
                        continue;
                    }
                    break;
                }
                LlmResponse parsed = parse(response.body());
                if (parsed.content().isBlank() && !parsed.hasToolCalls()) {
                    failure = LlmResult.Failure.EMPTY_OUTPUT;
                    backoff(attempt);
                    continue;
                }
                if (useJsonMode && !looksLikeJson(parsed.content())) {
                    if (attempt < maxAttempts) {
                        log.warn("LLM 未返回 JSON，发起结构化修复重试 purpose={} attempt={} traceId={}",
                                request.purpose(), attempt, traceId);
                        messages.add(LlmMessage.user(REPAIR_HINT));
                        continue;
                    }
                    failure = LlmResult.Failure.UNPARSEABLE_JSON;
                    break;
                }
                log.info("LLM 调用成功 purpose={} attempt={} latencyMs={} tokens={} traceId={}",
                        request.purpose(), attempt, System.currentTimeMillis() - start,
                        parsed.usage().totalTokens(), traceId);
                return LlmResult.ok(parsed, attempts, System.currentTimeMillis() - start);
            } catch (java.net.http.HttpTimeoutException ex) {
                failure = LlmResult.Failure.TIMEOUT;
                log.warn("LLM 调用超时 purpose={} tier={} attempt={} traceId={}",
                        request.purpose(), request.tier(), attempt, traceId);
                break;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                failure = LlmResult.Failure.TIMEOUT;
                break;
            } catch (Exception ex) {
                failure = LlmResult.Failure.SERVER_ERROR;
                log.warn("LLM 调用异常 purpose={} attempt={} traceId={} detail={}",
                        request.purpose(), attempt, traceId, ex.toString());
                backoff(attempt);
            }
        }
        log.warn("LLM 调用失败 purpose={} failure={} attempts={} traceId={}",
                request.purpose(), failure, attempts, traceId);
        return LlmResult.failed(failure, attempts, System.currentTimeMillis() - start);
    }

    private HttpResponse<String> send(LlmRequest request, List<LlmMessage> messages,
                                      boolean useNativeTools, boolean useJsonMode) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", request.temperature());
        body.put("max_tokens", request.maxTokens());
        body.put("messages", toPayload(messages));
        if (useJsonMode) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        if (useNativeTools) {
            body.put("tools", request.tools().stream().map(this::toToolPayload).toList());
            body.put("tool_choice", "auto");
        }
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(baseUrl))
                .timeout(request.tier().timeout())
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
    }

    private List<Map<String, Object>> toPayload(List<LlmMessage> messages) {
        List<Map<String, Object>> payload = new ArrayList<>();
        for (LlmMessage message : messages) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", message.role());
            item.put("content", message.content() == null ? "" : message.content());
            if (message.toolCallId() != null) {
                item.put("tool_call_id", message.toolCallId());
                item.put("name", message.name() == null ? "" : message.name());
            }
            payload.add(item);
        }
        return payload;
    }

    private Map<String, Object> toToolPayload(LlmToolSpec spec) {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", spec.name(),
                        "description", spec.description(),
                        "parameters", spec.parametersJson()));
    }

    private LlmResponse parse(String body) throws Exception {
        JsonNode root = json.readTree(body);
        JsonNode message = root.path("choices").path(0).path("message");
        String content = message.path("content").asText("").trim();
        List<LlmToolCall> toolCalls = new ArrayList<>();
        for (JsonNode call : message.path("tool_calls")) {
            JsonNode function = call.path("function");
            String name = function.path("name").asText("");
            if (name.isBlank()) continue;
            toolCalls.add(new LlmToolCall(call.path("id").asText(""), name, function.path("arguments").asText("{}")));
        }
        JsonNode usage = root.path("usage");
        LlmUsage tokens = new LlmUsage(
                usage.path("prompt_tokens").asInt(0),
                usage.path("completion_tokens").asInt(0),
                usage.path("total_tokens").asInt(0));
        return new LlmResponse(content, toolCalls,
                root.path("choices").path(0).path("finish_reason").asText(""),
                tokens, root.path("model").asText(model));
    }

    private boolean looksLikeJson(String content) {
        if (content == null || content.isBlank()) return false;
        String value = content.trim();
        return (value.startsWith("{") && value.endsWith("}")) || (value.startsWith("[") && value.endsWith("]"));
    }

    private LlmResult.Failure classify(int status) {
        if (status == 408 || status == 429) return LlmResult.Failure.RATE_LIMITED;
        if (status == 401 || status == 403 || status == 400 || status == 404 || status == 422) {
            return LlmResult.Failure.BAD_REQUEST;
        }
        if (status >= 500) return LlmResult.Failure.SERVER_ERROR;
        return LlmResult.Failure.SERVER_ERROR;
    }

    private boolean retryable(LlmResult.Failure failure) {
        return failure == LlmResult.Failure.RATE_LIMITED
                || failure == LlmResult.Failure.SERVER_ERROR
                || failure == LlmResult.Failure.TIMEOUT;
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep(Math.min(1_200L, 250L * attempt));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
