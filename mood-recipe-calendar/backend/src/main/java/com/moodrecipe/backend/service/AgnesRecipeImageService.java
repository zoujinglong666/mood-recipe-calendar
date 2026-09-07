package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.entity.Recipe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** 使用 Agnes Image 为 AI 菜谱生成封面；失败时不影响菜谱正文返回。 */
@Service
public class AgnesRecipeImageService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final String size;
    private final String ratio;

    public AgnesRecipeImageService(
            ObjectMapper objectMapper,
            @Value("${ai.recipe-image.api-key:}") String apiKey,
            @Value("${ai.recipe-image.base-url:https://apihub.agnes-ai.com/v1/images/generations}") String baseUrl,
            @Value("${ai.recipe-image.model:agnes-image-2.5-flash}") String model,
            @Value("${ai.recipe-image.size:1K}") String size,
            @Value("${ai.recipe-image.ratio:4:3}") String ratio
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.size = size;
        this.ratio = ratio;
    }

    public Optional<String> generateCover(Recipe recipe) {
        if (apiKey.isBlank()) return Optional.empty();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody(recipe))))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) return Optional.empty();
            return imageUrl(response.body());
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    Map<String, Object> requestBody(Recipe recipe) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("prompt", prompt(recipe));
        body.put("size", size);
        body.put("ratio", ratio);
        body.put("extra_body", Map.of("response_format", "url"));
        return body;
    }

    Optional<String> imageUrl(String responseBody) {
        try {
            JsonNode data = objectMapper.readTree(responseBody).path("data");
            if (!data.isArray() || data.isEmpty()) return Optional.empty();
            String url = data.path(0).path("url").asText().trim();
            URI uri = URI.create(url);
            return "https".equalsIgnoreCase(uri.getScheme()) ? Optional.of(url) : Optional.empty();
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private String prompt(Recipe recipe) {
        return "一道刚出锅的中国家常菜「" + clip(recipe.getName(), 40) + "」，"
                + clip(recipe.getDescription(), 80) + "。主要食材参考：" + clip(recipe.getIngredients(), 240)
                + "。真实美食摄影，三分之二俯拍，自然暖光，干净温暖的陶瓷餐具与木桌，食物细节清晰、令人有食欲，"
                + "主体居中并留有舒适呼吸感，不出现人物、文字、水印、品牌标志或多余餐具。";
    }

    private String clip(String value, int maxLength) {
        if (value == null) return "";
        String clean = value.replaceAll("[\\r\\n]+", " ").trim();
        return clean.substring(0, Math.min(clean.length(), maxLength));
    }
}
