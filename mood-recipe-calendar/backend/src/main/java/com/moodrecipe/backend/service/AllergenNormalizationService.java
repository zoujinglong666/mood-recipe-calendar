package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.agent.LlmClient;
import com.moodrecipe.backend.agent.LlmRequest;
import com.moodrecipe.backend.agent.TimeoutTier;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** 将用户主动填写的自然语言忌口转换为可用于硬过滤的食材匹配词。 */
@Service
public class AllergenNormalizationService {
    private static final Pattern SAFE_TERM = Pattern.compile("[\\p{IsHan}A-Za-z0-9·-]{1,24}");
    private static final int MAX_TERMS = 32;

    private final LlmClient llm;
    private final ObjectMapper json;

    public AllergenNormalizationService(LlmClient llm, ObjectMapper json) {
        this.llm = llm;
        this.json = json;
    }

    public List<String> normalize(String avoidIngredients, String allergens) {
        LinkedHashSet<String> terms = new LinkedHashSet<>(localTerms(avoidIngredients, allergens));
        if (llm.isConfigured()) terms.addAll(modelTerms(avoidIngredients, allergens));
        return terms.stream().limit(MAX_TERMS).toList();
    }

    public String normalizeJson(String avoidIngredients, String allergens) {
        try {
            return json.writeValueAsString(normalize(avoidIngredients, allergens));
        } catch (Exception ignored) {
            return "[]";
        }
    }

    private List<String> localTerms(String avoidIngredients, String allergens) {
        return Stream.concat(split(avoidIngredients).stream(), split(allergens).stream())
                .flatMap(term -> {
                    String normalized = term.replace("严重过敏", "").replace("过敏原", "")
                            .replace("过敏", "").replace("不耐受", "").trim();
                    // 甲壳类的关键同义词必须在本地完成，不能把安全边界交给模型是否在线。
                    if (normalized.contains("虾")) return Stream.of(normalized, "虾", "虾仁");
                    return normalized.isBlank() ? Stream.empty() : Stream.of(normalized);
                })
                .filter(this::validTerm).distinct().toList();
    }

    private List<String> modelTerms(String avoidIngredients, String allergens) {
        String prompt = """
                将用户明确写下的忌口与食物过敏原转换成菜谱文本可直接匹配的食材词和同义词。
                只返回 JSON：{\"terms\":[\"食材词\"]}。不得给医疗建议、不得增加用户未提及的过敏原。
                每个词必须是食材名或直接同义词，例如“对虾过敏”可返回“对虾”“虾”“虾仁”。
                忌口：%s
                过敏原：%s
                """.formatted(safe(avoidIngredients), safe(allergens));
        try {
            var result = llm.complete(LlmRequest.json("allergen-normalization", "你只做食材词归一化。", prompt,
                    0, 160, TimeoutTier.FAST));
            if (!result.ok()) return List.of();
            JsonNode terms = json.readTree(result.text()).path("terms");
            if (!terms.isArray()) return List.of();
            Set<String> normalized = new LinkedHashSet<>();
            for (JsonNode term : terms) {
                String value = term.asText("").trim();
                if (validTerm(value)) normalized.add(value);
                if (normalized.size() == MAX_TERMS) break;
            }
            return List.copyOf(normalized);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<String> split(String value) {
        if (value == null || value.isBlank()) return List.of();
        return List.of(value.split("[,，、;；\\s]+"));
    }

    private boolean validTerm(String value) {
        return value != null && SAFE_TERM.matcher(value).matches();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
