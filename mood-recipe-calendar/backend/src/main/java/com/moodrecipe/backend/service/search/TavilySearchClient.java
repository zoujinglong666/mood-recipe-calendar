package com.moodrecipe.backend.service.search;

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
 * Tavily 搜索实现（专为 LLM 设计，直接返回已清洗的摘要，最适合 Agent 消费）。
 *
 * 未配置 SEARCH_API_KEY 时 available() 返回 false，上层工具直接降级，不会发请求也不会报错。
 * 任何网络/解析异常都被吞掉并返回空列表——搜索失败绝不能影响锅仔回答。
 */
@Service
public class TavilySearchClient implements SearchClient {

    private static final Logger log = LoggerFactory.getLogger(TavilySearchClient.class);

    private final ObjectMapper json;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String endpoint;

    public TavilySearchClient(ObjectMapper json,
                              @Value("${search.api-key:}") String apiKey,
                              @Value("${search.tavily-endpoint:https://api.tavily.com/search}") String endpoint) {
        this.json = json;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.endpoint = endpoint == null || endpoint.isBlank() ? "https://api.tavily.com/search" : endpoint.trim();
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    }

    @Override
    public boolean available() {
        return !apiKey.isBlank();
    }

    @Override
    public List<SearchResult> search(String query, int maxResults) {
        if (!available() || query == null || query.isBlank()) return List.of();
        int limit = Math.max(1, Math.min(maxResults <= 0 ? 5 : maxResults, 8));
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("api_key", apiKey);
            body.put("query", query.trim());
            body.put("max_results", limit);
            body.put("search_depth", "basic");
            body.put("include_answer", false);
            body.put("include_raw_content", false);

            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(12))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.info("web_search 返回非 200：{}", response.statusCode());
                return List.of();
            }
            JsonNode root = json.readTree(response.body());
            List<SearchResult> results = new ArrayList<>();
            for (JsonNode item : root.path("results")) {
                String title = item.path("title").asText("").trim();
                String snippet = item.path("content").asText("").trim();
                String url = item.path("url").asText("").trim();
                if (title.isBlank() && snippet.isBlank()) continue;
                results.add(new SearchResult(truncate(title, 120), truncate(snippet, 500), url));
                if (results.size() >= limit) break;
            }
            return results;
        } catch (Exception error) {
            log.info("web_search 失败：{}", error.getClass().getSimpleName());
            return List.of();
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
