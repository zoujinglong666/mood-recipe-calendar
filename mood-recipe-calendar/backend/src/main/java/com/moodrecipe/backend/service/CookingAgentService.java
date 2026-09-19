package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.agent.*;
import com.moodrecipe.backend.entity.CookingKnowledgeChunk;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.repository.RecipeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class CookingAgentService {
    private static final List<String> DEFAULT_SUGGESTIONS = List.of("怎样算熟？", "火太大怎么补救？", "没有这个食材怎么换？");
    private final RecipeRepository recipes;
    private final CookingKnowledgeService knowledge;
    private final CookingLearningService learning;
    private final AgentMemoryStore memory;
    private final LlmClient llm;
    private final ObjectMapper json;
    private final WechatContentSafetyService contentSafety;
    private final boolean enabled;

    public CookingAgentService(RecipeRepository recipes, CookingKnowledgeService knowledge,
                               CookingLearningService learning, AgentMemoryStore memory, LlmClient llm,
                               ObjectMapper json, WechatContentSafetyService contentSafety,
                               @Value("${ai.cooking-agent.enabled:true}") boolean enabled) {
        this.recipes = recipes; this.knowledge = knowledge; this.learning = learning; this.memory = memory;
        this.llm = llm; this.json = json; this.contentSafety = contentSafety; this.enabled = enabled;
    }

    public TurnResponse turn(String openid, TurnRequest request) {
        Recipe recipe = recipes.findById(request.recipeId()).orElse(null);
        if (recipe == null) throw new IllegalArgumentException("菜谱不存在");
        List<String> originalSteps = parseList(recipe.getSteps());
        if (originalSteps.isEmpty()) throw new IllegalArgumentException("这道菜还没有完整做法");
        int current = Math.max(0, Math.min(request.currentStep() == null ? 0 : request.currentStep(), originalSteps.size() - 1));
        String action = "ASK".equalsIgnoreCase(request.action()) ? "ASK" : "GUIDE";
        boolean personalized = !Boolean.FALSE.equals(request.personalized()) && memory.personalizationEnabled(openid);
        UserProfile profile = personalized ? memory.profile(openid, AgentMemoryStore.Scene.SINGLE_RECIPE) : UserProfile.empty(openid);
        String level = learning.teachingLevel(openid, personalized);
        String query = String.join(" ", recipe.getName(), value(recipe.getIngredients()), originalSteps.get(current), value(request.message()));
        List<CookingKnowledgeChunk> evidence = knowledge.search(query, 4);
        List<Source> sources = evidence.stream().map(item -> new Source(item.getId(), item.getTitle(),
                item.getSourceName(), item.getSourceUrl(), item.getSourceVersion(), item.getReviewedAt())).toList();
        List<String> memoryUsed = personalized ? profile.memory().stream().limit(4)
                .map(item -> item.key() + "：" + item.reason()).toList() : List.of();

        if (!enabled) return fallback(originalSteps, current, "功能正在小范围开放", "DISABLED", sources, memoryUsed, level);
        if (!llm.isConfigured()) return fallback(originalSteps, current, "模型服务暂时不可用，先按原步骤操作。", "MODEL_UNAVAILABLE", sources, memoryUsed, level);

        LlmResult result = llm.complete(LlmRequest.json("cooking-coach-" + action.toLowerCase(Locale.ROOT),
                systemPrompt(action, level), userPrompt(recipe, originalSteps, current, request, profile, evidence),
                0.25, "GUIDE".equals(action) ? 2200 : 900, TimeoutTier.STANDARD));
        if (!result.ok()) return fallback(originalSteps, current, result.reason(), "MODEL_ERROR", sources, memoryUsed, level);

        try {
            JsonNode root = json.readTree(result.text());
            String reply = root.path("reply").asText("").trim();
            List<GuideStep> guide = parseGuide(root.path("guideSteps"));
            if ("GUIDE".equals(action) && guide.size() != originalSteps.size()) throw new IllegalArgumentException("教学步骤不完整");
            if ("ASK".equals(action) && reply.isBlank()) throw new IllegalArgumentException("回答为空");
            List<String> suggestions = parseTextArray(root.path("suggestions"), 3);
            if (suggestions.isEmpty()) suggestions = DEFAULT_SUGGESTIONS;
            String safetyText = reply + " " + guide.stream().map(GuideStep::asText).reduce("", (a, b) -> a + " " + b);
            if (!contentSafety.allowsText(openid, safetyText)) {
                return fallback(originalSteps, current, "这次回答没有通过安全检查，请换一种问法。", "CONTENT_BLOCKED", sources, memoryUsed, level);
            }
            boolean degraded = evidence.isEmpty();
            return new TurnResponse(reply, guide, sources, memoryUsed, suggestions, degraded,
                    degraded ? "NO_EVIDENCE" : null, level);
        } catch (Exception exception) {
            return fallback(originalSteps, current, "教学结果格式不完整，先按原步骤操作。", "INVALID_MODEL_OUTPUT", sources, memoryUsed, level);
        }
    }

    private String systemPrompt(String action, String level) {
        return "你是锅仔做菜教练。只根据输入中的原菜谱和知识证据回答，不得编造来源。"
                + "先解决用户眼前问题；危险或无法判断时建议关小火/停火并核验。禁止医疗诊断。"
                + "教学密度=" + level + "。动作=" + action + "。只输出JSON："
                + "{\"reply\":\"\",\"guideSteps\":[{\"index\":1,\"instruction\":\"\",\"heat\":\"\",\"duration\":\"\",\"successSigns\":\"\",\"rescue\":\"\"}],\"suggestions\":[\"\"]}。"
                + "GUIDE必须逐一覆盖全部原步骤且字段非空；ASK可以返回空guideSteps。";
    }

    private String userPrompt(Recipe recipe, List<String> steps, int current, TurnRequest request,
                              UserProfile profile, List<CookingKnowledgeChunk> evidence) {
        String history = request.history() == null ? "" : request.history().stream().limit(8)
                .map(item -> safe(item.role(), 12) + ":" + safe(item.content(), 300)).reduce("", (a, b) -> a + "\n" + b);
        String evidenceText = evidence.isEmpty() ? "无可靠知识命中" : evidence.stream()
                .map(item -> "[" + item.getId() + "] " + item.getTitle() + "：" + item.getContent())
                .reduce("", (a, b) -> a + "\n" + b);
        return "菜名：" + safe(recipe.getName(), 120) + "\n食材：" + safe(recipe.getIngredients(), 1800)
                + "\n原步骤：" + steps + "\n当前步骤序号：" + (current + 1)
                + "\n用户问题：" + safe(request.message(), 500) + "\n本次对话：" + history
                + "\n个性化档案：" + safe(profile.summary(), 800) + "\n已检索证据：" + evidenceText;
    }

    private TurnResponse fallback(List<String> steps, int current, String reason, String code,
                                  List<Source> sources, List<String> memoryUsed, String level) {
        List<GuideStep> guide = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            guide.add(new GuideStep(i + 1, steps.get(i), "按原菜谱控制，拿不准先用中小火",
                    duration(steps.get(i)), "完成原步骤描述的状态再继续", "出现焦味或剧烈冒烟时先关火检查"));
        }
        String reply = reason == null || reason.isBlank() ? "先按原菜谱完成当前步骤。" : reason;
        return new TurnResponse(reply, guide, sources, memoryUsed, DEFAULT_SUGGESTIONS, true, code, level);
    }

    private List<GuideStep> parseGuide(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<GuideStep> result = new ArrayList<>();
        for (JsonNode item : node) {
            GuideStep step = new GuideStep(item.path("index").asInt(result.size() + 1),
                    required(item, "instruction"), required(item, "heat"), required(item, "duration"),
                    required(item, "successSigns"), required(item, "rescue"));
            result.add(step);
        }
        return result;
    }

    private String required(JsonNode node, String field) {
        String value = node.path(field).asText("").trim();
        if (value.isBlank()) throw new IllegalArgumentException(field + "缺失");
        return safe(value, 500);
    }

    private List<String> parseTextArray(JsonNode node, int limit) {
        if (!node.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        node.forEach(item -> { if (result.size() < limit && !item.asText("").isBlank()) result.add(safe(item.asText(), 80)); });
        return result;
    }

    private List<String> parseList(String value) {
        try {
            JsonNode node = json.readTree(value == null ? "[]" : value);
            if (!node.isArray()) return List.of();
            List<String> result = new ArrayList<>();
            node.forEach(item -> { if (!item.asText("").isBlank()) result.add(item.asText().trim()); });
            return result;
        } catch (Exception ignored) { return List.of(); }
    }

    private String duration(String step) {
        java.util.regex.Matcher match = java.util.regex.Pattern.compile("(\\d+)\\s*分钟").matcher(step);
        return match.find() ? match.group(1) + "分钟" : "观察状态，不只看时间";
    }

    private String safe(String value, int max) {
        if (value == null) return "";
        value = value.trim(); return value.length() <= max ? value : value.substring(0, max);
    }
    private String value(String value) { return value == null ? "" : value; }

    public record ChatMessage(String role, String content) {}
    public record TurnRequest(Long recipeId, Integer currentStep, String sessionId, String message,
                              String action, Boolean personalized, List<ChatMessage> history) {}
    public record GuideStep(int index, String instruction, String heat, String duration,
                            String successSigns, String rescue) {
        String asText() { return String.join(" ", instruction, heat, duration, successSigns, rescue); }
    }
    public record Source(Long id, String title, String sourceName, String sourceUrl,
                         String version, LocalDateTime reviewedAt) {}
    public record TurnResponse(String reply, List<GuideStep> guideSteps, List<Source> sources,
                               List<String> memoryUsed, List<String> suggestions, boolean degraded,
                               String degradeReason, String teachingLevel) {}
}
