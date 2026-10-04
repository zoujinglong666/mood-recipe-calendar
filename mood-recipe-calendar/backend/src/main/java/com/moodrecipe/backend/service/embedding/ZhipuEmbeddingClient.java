package com.moodrecipe.backend.service.embedding;

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

/**
 * 智谱官方 Embedding 客户端（open.bigmodel.cn）。
 *
 * 用于《中国居民膳食指南》RAG：离线抽取时由 Python 脚本调用，
 * 运行时由 {@link GuidelineRagService} 把用户查询嵌入后做余弦检索。
 * 模型默认 embedding-2（1024 维），可配置为 bge-m3 等。
 *
 * 本地若需走代理（如 127.0.0.1:4780）访问外网，读取 HTTPS_PROXY / https_proxy 环境变量自动生效。
 */
@Service
public class ZhipuEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(ZhipuEmbeddingClient.class);
    private static final int BATCH = 32;

    private final ObjectMapper json;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final HttpClient httpClient;

    public ZhipuEmbeddingClient(ObjectMapper json,
                               @Value("${zhipu.embedding.api-key:}") String apiKey,
                               @Value("${zhipu.embedding.base-url:https://open.bigmodel.cn/api/paas/v4}") String baseUrl,
                               @Value("${zhipu.embedding.model:embedding-2}") String model) {
        this.json = json;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = (baseUrl == null ? "https://open.bigmodel.cn/api/paas/v4" : baseUrl.trim()).replaceAll("/+$", "");
        this.model = model == null ? "embedding-2" : model.trim();
        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10));
        String proxy = firstNonBlank(System.getenv("HTTPS_PROXY"), System.getenv("https_proxy"));
        if (proxy != null) {
            try {
                URI p = URI.create(proxy);
                builder.proxy(java.net.ProxySelector.of(new java.net.InetSocketAddress(p.getHost(), p.getPort())));
            } catch (Exception ex) {
                log.warn("解析 HTTPS_PROXY 失败，改为直连：{}", ex.toString());
            }
        }
        this.httpClient = builder.build();
    }

    private static String firstNonBlank(String... xs) {
        for (String x : xs) if (x != null && !x.isBlank()) return x;
        return null;
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isBlank() && !baseUrl.isBlank() && !model.isBlank();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> result = new ArrayList<>();
        if (!isConfigured() || texts.isEmpty()) return result;
        for (int i = 0; i < texts.size(); i += BATCH) {
            result.addAll(callBatch(texts.subList(i, Math.min(i + BATCH, texts.size()))));
        }
        return result;
    }

    private List<float[]> callBatch(List<String> texts) {
        List<float[]> out = new ArrayList<>();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("input", texts);
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/embeddings"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("智谱 embedding 失败 status={} body={}", response.statusCode(), response.body());
                for (int k = 0; k < texts.size(); k++) out.add(new float[0]);
                return out;
            }
            JsonNode root = json.readTree(response.body());
            Map<Integer, float[]> byIndex = new LinkedHashMap<>();
            for (JsonNode d : root.path("data")) {
                int idx = d.path("index").asInt(-1);
                List<Float> vec = new ArrayList<>();
                for (JsonNode v : d.path("embedding")) vec.add((float) v.asDouble());
                byIndex.put(idx, toArray(vec));
            }
            for (int k = 0; k < texts.size(); k++) out.add(byIndex.getOrDefault(k, new float[0]));
            return out;
        } catch (Exception ex) {
            log.warn("智谱 embedding 异常：{}", ex.toString());
            for (int k = 0; k < texts.size(); k++) out.add(new float[0]);
            return out;
        }
    }

    private static float[] toArray(List<Float> v) {
        float[] a = new float[v.size()];
        for (int i = 0; i < v.size(); i++) a[i] = v.get(i);
        return a;
    }
}
