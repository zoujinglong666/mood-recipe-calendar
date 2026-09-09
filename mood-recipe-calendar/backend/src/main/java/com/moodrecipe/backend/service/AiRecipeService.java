package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.entity.Recipe;
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
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 通过 OpenAI 兼容的 Chat Completions API 生成菜谱。
 * API Key 只从环境变量读取；服务不可用时由调用方回退到本地菜谱库。
 */
@Service
public class AiRecipeService {

    private static final Logger log = LoggerFactory.getLogger(AiRecipeService.class);

    public enum GenerationEvent {
        TEXT_STARTED,
        TEXT_COMPLETED,
        TEXT_FAILED,
        IMAGE_STARTED,
        IMAGE_COMPLETED,
        IMAGE_FAILED
    }

    private record ChatResult(String content) {}

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(40);

    private final ObjectMapper objectMapper;
    private final AgnesRecipeImageService imageService;
    private final GuozaiPersona persona;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public AiRecipeService(
            ObjectMapper objectMapper,
            AgnesRecipeImageService imageService,
            GuozaiPersona persona,
            @Value("${ai.recipe.api-key:}") String apiKey,
            @Value("${ai.recipe.base-url:https://apihub.agnes-ai.com/v1/chat/completions}") String baseUrl,
            @Value("${ai.recipe.model:agnes-2.5-flash}") String model
    ) {
        this.objectMapper = objectMapper;
        this.imageService = imageService;
        this.persona = persona;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
    }

    public Optional<Recipe> recommend(String mood) {
        return recommend(mood, "");
    }

    /** 为已购买的深度推荐权益生成菜谱，preference 仅包含用户明确填写的烹饪偏好。 */
    public Optional<Recipe> recommend(String mood, String preference) {
        return recommend(mood, preference, ignored -> { });
    }

    /** 只在真实的模型请求边界报告阶段，不包含提示词或供应商错误。 */
    public Optional<Recipe> recommend(String mood, String preference, Consumer<GenerationEvent> progress) {
        if (apiKey.isBlank() || model.isBlank()) {
            progress.accept(GenerationEvent.TEXT_FAILED);
            return Optional.empty();
        }
        log.info("AI 菜谱生成开始，model={}", model);
        progress.accept(GenerationEvent.TEXT_STARTED);

        String systemPrompt = "你是锅仔，一位温暖、务实的中文家常菜助手。你只提供普通家庭可完成的菜谱，不提供医疗建议。";
        String userPrompt = """
                用户现在的心情是「%s」。请推荐一道适合此刻的中国家常菜。
                用户补充的烹饪偏好是：「%s」。只在合理且安全的范围内遵循它；如果为空则忽略。
                只返回一个合法 JSON 对象，不要 Markdown、不要解释。格式严格为：
                {"name":"菜名","description":"30字以内的治愈理由","ingredients":["食材及用量"],"steps":["步骤"],"cookingTime":30,"difficulty":"简单","moodTags":"%s","season":"四季"}
                规则：3-7 种常见食材；3-5 个步骤；20-45 分钟；食材用量明确；不虚构功效。
                """.formatted(mood, sanitizePreference(preference), mood);

        Optional<ChatResult> result = chatRaw(systemPrompt, userPrompt, 0.8, 1024, progress);
        if (result.isEmpty()) {
            progress.accept(GenerationEvent.TEXT_FAILED);
            return Optional.empty();
        }
        Optional<Recipe> recipe = toRecipe(result.get().content(), mood);
        if (recipe.isEmpty()) {
            progress.accept(GenerationEvent.TEXT_FAILED);
            return Optional.empty();
        }
        progress.accept(GenerationEvent.TEXT_COMPLETED);
        progress.accept(GenerationEvent.IMAGE_STARTED);
        Optional<String> cover = imageService.generateCover(recipe.get());
        cover.ifPresent(recipe.get()::setImage);
        progress.accept(cover.isPresent() ? GenerationEvent.IMAGE_COMPLETED : GenerationEvent.IMAGE_FAILED);
        return recipe;
    }

    /**
     * 智能体菜谱推荐——使用 GuozaiPersona 统一人格 prompt，
     * userPrompt 由 GuozaiAgent 基于用户记忆、情绪趋势、历史偏好主动分析后构建。
     * 与简单 recommend(mood, preference) 的区别：prompt 包含锅仔对用户的真实观察分析，
     * 而不是仅传心情+口味字符串。
     */
    public Optional<Recipe> recommendWithPersona(String mood, String userPrompt, Consumer<GenerationEvent> progress) {
        if (apiKey.isBlank() || model.isBlank()) {
            progress.accept(GenerationEvent.TEXT_FAILED);
            return Optional.empty();
        }
        log.info("锅仔智能体菜谱推荐开始，model={}", model);
        progress.accept(GenerationEvent.TEXT_STARTED);

        Optional<ChatResult> result = chatRaw(persona.systemPrompt(), userPrompt, 0.8, 1024, progress);
        if (result.isEmpty()) {
            progress.accept(GenerationEvent.TEXT_FAILED);
            return Optional.empty();
        }
        Optional<Recipe> recipe = toRecipe(result.get().content(), mood);
        if (recipe.isEmpty()) {
            progress.accept(GenerationEvent.TEXT_FAILED);
            return Optional.empty();
        }
        progress.accept(GenerationEvent.TEXT_COMPLETED);
        progress.accept(GenerationEvent.IMAGE_STARTED);
        Optional<String> cover = imageService.generateCover(recipe.get());
        cover.ifPresent(recipe.get()::setImage);
        progress.accept(cover.isPresent() ? GenerationEvent.IMAGE_COMPLETED : GenerationEvent.IMAGE_FAILED);
        return recipe;
    }

    /** 只接收聚合后的习惯摘要，不上传日记、菜谱正文或身份标识。 */
    public Optional<String> companionMessage(String context) {
        return companionMessageWithPersona("根据这份不含身份信息的习惯摘要写一句20到36字的个性化寄语："
                + sanitizePreference(context) + "。自然提到其中一个真实细节，不要引号、标题、表情符号或自称AI。");
    }

    /**
     * 深度寄语——使用 GuozaiPersona 统一的人格 prompt 和传入的 user prompt。
     * 用于锅仔主动分析后的个性化寄语。
     */
    public Optional<String> companionMessageWithPersona(String userPrompt) {
        return chat(persona.systemPrompt(), userPrompt, 0.75, 40);
    }

    /**
     * 情绪洞察——使用 GuozaiPersona 统一的人格 prompt，生成一句话情绪总结。
     */
    public Optional<String> insightMessageWithPersona(String userPrompt) {
        return chat(persona.systemPrompt(), userPrompt, 0.7, 25);
    }

    private Optional<String> chat(String systemPrompt, String userPrompt, double temperature, int maxLength) {
        return chatRaw(systemPrompt, userPrompt, temperature, 256, e -> {})
                .map(r -> r.content())
                .filter(text -> !text.isBlank())
                .map(text -> text.substring(0, Math.min(text.length(), maxLength)));
    }

    /**
     * 通用 Chat Completions 调用。模型内部推理和用户提示词不得写入日志或返回前端。
     */
    private Optional<ChatResult> chatRaw(String systemPrompt, String userPrompt,
                                         double temperature, int maxTokens,
                                         Consumer<GenerationEvent> progress) {
        if (apiKey.isBlank() || model.isBlank()) return Optional.empty();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("temperature", temperature);
            body.put("max_tokens", maxTokens);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
            ));
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Chat API 返回非 2xx: status={}", response.statusCode());
                return Optional.empty();
            }
            JsonNode msg = objectMapper.readTree(response.body())
                    .path("choices").path(0).path("message");
            String content = msg.path("content").asText().replaceAll("[\\r\\n]+", " ").trim();
            if (content.isBlank()) return Optional.empty();
            return Optional.of(new ChatResult(content));
        } catch (Exception ex) {
            log.warn("Chat API 调用失败: {}", ex.toString());
            return Optional.empty();
        }
    }

    private Optional<Recipe> toRecipe(String content, String mood) {
        try {
            String json = content.trim();
            if (json.startsWith("```")) {
                json = json.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
            }
            JsonNode node = objectMapper.readTree(json);
            String name = node.path("name").asText().trim();
            List<String> ingredients = readTextArray(node.path("ingredients"));
            List<String> steps = readTextArray(node.path("steps"));
            int cookingTime = node.path("cookingTime").asInt(30);
            if (name.isEmpty() || ingredients.isEmpty() || steps.isEmpty()) return Optional.empty();

            Recipe recipe = new Recipe();
            recipe.setName(name);
            recipe.setDescription(node.path("description").asText("锅仔为你挑了一道温柔的家常菜。"));
            recipe.setIngredients(objectMapper.writeValueAsString(ingredients));
            recipe.setSteps(objectMapper.writeValueAsString(steps));
            recipe.setCookingTime(Math.max(10, Math.min(cookingTime, 90)));
            recipe.setDifficulty(node.path("difficulty").asText("简单"));
            recipe.setMoodTags(node.path("moodTags").asText(mood));
            recipe.setSeason(node.path("season").asText("四季"));
            return Optional.of(recipe);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private List<String> readTextArray(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (!node.isArray()) return result;
        for (JsonNode item : node) {
            String value = item.asText().trim();
            if (!value.isEmpty()) result.add(value);
        }
        return result;
    }

    private String sanitizePreference(String preference) {
        if (preference == null) return "";
        String sanitized = preference.replaceAll("[\\r\\n]+", " ").trim();
        return sanitized.substring(0, Math.min(sanitized.length(), 240));
    }
}
