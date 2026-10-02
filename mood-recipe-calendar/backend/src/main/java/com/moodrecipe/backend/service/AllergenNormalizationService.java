package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.agent.LlmClient;
import com.moodrecipe.backend.agent.LlmRequest;
import com.moodrecipe.backend.agent.TimeoutTier;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** 将用户主动填写的自然语言忌口转换为可用于硬过滤的食材匹配词。 */
@Service
public class AllergenNormalizationService {
    private static final Pattern SAFE_TERM = Pattern.compile("[\\p{IsHan}A-Za-z0-9·-]{1,24}");
    private static final int MAX_TERMS = 32;

    /**
     * 类目级安全词典：用户写"海鲜/坚果/乳制品过敏"时，本地确定性地展开为成员食材词。
     * 与虾类分支一样，这是安全边界，绝不能依赖模型是否在线；模型只做增量补充。
     * 展开一律偏保守（宁多拦勿漏拦），例如大豆过敏会连带拦截酱油、味噌等含豆调味。
     */
    private static final Map<String, List<String>> CATEGORY_TERMS = Map.ofEntries(
            Map.entry("海鲜", List.of("虾", "虾仁", "虾皮", "虾米", "蟹", "螃蟹", "贝", "蛤", "蛏", "牡蛎", "扇贝", "鱿鱼", "章鱼", "墨鱼", "鱼")),
            Map.entry("海产", List.of("虾", "虾仁", "蟹", "贝", "蛤", "鱼")),
            Map.entry("水产", List.of("虾", "蟹", "贝", "鱼")),
            Map.entry("甲壳", List.of("虾", "虾仁", "虾皮", "虾米", "基围虾", "明虾", "对虾", "龙虾", "蟹", "螃蟹")),
            Map.entry("虾", List.of("虾", "虾仁", "虾皮", "虾米", "基围虾", "明虾", "对虾", "龙虾")),
            Map.entry("蟹", List.of("蟹", "螃蟹", "蟹黄", "蟹肉", "蟹柳")),
            Map.entry("贝", List.of("贝", "蛤", "蛏", "牡蛎", "扇贝", "青口", "贻贝", "螺", "蚬")),
            Map.entry("鱼", List.of("鱼", "鱼片", "鱼柳", "鱼丸", "咸鱼", "鱼露", "鱼籽")),
            Map.entry("蛋", List.of("蛋", "鸡蛋", "鸭蛋", "鹌鹑蛋", "皮蛋", "咸蛋", "蛋黄", "蛋清", "蛋白")),
            Map.entry("奶", List.of("奶", "牛奶", "奶粉", "奶油", "奶酪", "芝士", "乳酪", "黄油", "酸奶", "炼乳")),
            Map.entry("乳", List.of("奶", "牛奶", "奶粉", "奶油", "奶酪", "芝士", "乳酪", "黄油", "酸奶", "炼乳")),
            Map.entry("乳制品", List.of("奶", "牛奶", "奶粉", "奶油", "奶酪", "芝士", "乳酪", "黄油", "酸奶", "炼乳")),
            Map.entry("牛奶", List.of("牛奶", "奶", "奶粉", "奶油", "奶酪", "芝士", "黄油", "酸奶")),
            Map.entry("坚果", List.of("坚果", "花生", "核桃", "腰果", "杏仁", "榛子", "开心果", "夏威夷果", "松子", "板栗")),
            Map.entry("芝麻", List.of("芝麻", "香油", "麻油", "芝麻酱")),
            Map.entry("麸质", List.of("麸质", "小麦", "面粉", "面筋", "面条", "面包", "馒头", "饺子", "挂面", "意面", "拉面")),
            Map.entry("小麦", List.of("小麦", "麸质", "面粉", "面筋", "面条", "面包", "馒头", "饺子")),
            Map.entry("大豆", List.of("大豆", "黄豆", "豆腐", "豆浆", "豆干", "豆皮", "腐竹", "毛豆", "豆芽", "酱油", "味噌")),
            Map.entry("豆制品", List.of("豆腐", "豆浆", "豆干", "豆皮", "腐竹", "毛豆", "酱油", "味噌")),
            Map.entry("豆类", List.of("大豆", "黄豆", "豆腐", "豆浆", "豆干", "豆皮", "腐竹", "毛豆", "豆芽")),
            Map.entry("豆", List.of("大豆", "黄豆", "豆腐", "豆浆", "豆干", "豆皮", "腐竹", "毛豆")));

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
                    if (normalized.isBlank()) return Stream.empty();
                    // 甲壳类的关键同义词必须在本地完成，不能把安全边界交给模型是否在线。
                    // 任何含"虾"的表达（对虾/基围虾…）都保底带上"虾/虾仁"与虾类全族。
                    if (normalized.contains("虾")) {
                        return Stream.concat(Stream.of(normalized, "虾", "虾仁"), expand("虾"));
                    }
                    return Stream.concat(Stream.of(normalized), expand(normalized.replace("类", "")));
                })
                .filter(this::validTerm).distinct().toList();
    }

    /** 类目词（海鲜/坚果/乳…）确定性展开为成员食材词；未命中则原样返回。 */
    private Stream<String> expand(String normalized) {
        List<String> members = CATEGORY_TERMS.get(normalized);
        return members == null ? Stream.empty() : members.stream();
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
