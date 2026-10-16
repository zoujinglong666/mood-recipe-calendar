package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.agent.LlmClient;
import com.moodrecipe.backend.agent.LlmRequest;
import com.moodrecipe.backend.agent.LlmResult;
import com.moodrecipe.backend.agent.TimeoutTier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 菜谱营养建议（借鉴营养分析卡）：基于菜名+食材用模型估算糖/热量/脂肪含量，
 * 给出整体结论、常见人群建议与三层营养金字塔数据。
 *
 * 口径说明：数值是家常营养密度估算（非实验室测定），前端必须标注「估算值仅供参考」。
 * 内存缓存：同名同食材的结果稳定，命中即不再调用模型；缓存满时整表清空（LRU 级别足够）。
 */
@Service
public class NutritionAdviceService {

    private static final Logger log = LoggerFactory.getLogger(NutritionAdviceService.class);
    private static final int CACHE_MAX = 600;

    /** 人群固定候选与建议枚举，防止模型自由发挥输出不可控内容 */
    private static final List<String> AUDIENCES = List.of("高血压", "高血脂", "糖尿病", "减肥", "儿童", "老人", "孕妇");

    public record NutritionAdvice(
            String headline,        // 整体结论，如「高血糖」
            String headlineAdvice,  // 控制少吃 / 适量 / 建议多吃
            String keyNutrient,     // 结论对应的关键营养素，如「含糖量」
            String keyValue,        // 如「6.93克」
            List<GroupAdvice> groups,
            List<PyramidLayer> pyramid,
            List<String> bullets) { }

    public record GroupAdvice(String name, String advice) { }
    public record PyramidLayer(String key, String value, String advice, String group) { }

    private final LlmClient llm;
    private final ObjectMapper json;
    private final Map<String, Optional<NutritionAdvice>> cache = new ConcurrentHashMap<>();

    public NutritionAdviceService(LlmClient llm, ObjectMapper json) {
        this.llm = llm;
        this.json = json;
    }

    /** 菜谱营养分析：失败返回 empty（前端不渲染卡片，绝不阻塞页面） */
    public Optional<NutritionAdvice> analyze(String name, List<String> ingredients) {
        String dish = name == null ? "" : name.trim();
        String ings = String.join("、", ingredients == null ? List.of() : ingredients.stream()
                .map(String::trim).filter(s -> !s.isEmpty()).limit(30).toList());
        if (dish.isEmpty() && ings.isEmpty()) return Optional.empty();
        String cacheKey = dish + "|" + ings.hashCode();
        if (cache.size() > CACHE_MAX) cache.clear();
        return cache.computeIfAbsent(cacheKey, key -> doAnalyze(dish, ings));
    }

    private Optional<NutritionAdvice> doAnalyze(String dish, String ingredients) {
        if (!llm.isConfigured()) return Optional.empty();
        String prompt = """
                请按家常食材营养密度估算这道菜的营养构成，只输出一个 JSON 对象：
                {"headline":"整体结论如高血糖/低负担/高热量","headlineAdvice":"控制少吃|适量|建议多吃",
                 "keyNutrient":"结论对应的关键营养素名如含糖量/热量/脂肪","keyValue":"数值+单位，如 6.93克",
                 "groups":[{"name":"人群名","advice":"建议多吃|适量|控制少吃"}],
                 "pyramid":[{"key":"糖","value":"数值+单位","advice":"少吃|适量|多吃","group":"最需注意的人群"}],
                 "bullets":["一句话总结","一句话总结"]}
                硬性要求：
                - groups 从这七个里选 4 个：高血压、高血脂、糖尿病、减肥、儿童、老人、孕妇；advice 只能是「建议多吃/适量/控制少吃」。
                - pyramid 恰好三层：糖、热量、脂肪，value 是这道菜整份的估算量。
                - 数值用家常估算精度（克保留两位内、热量用大卡）；这是估算展示，不要编造精确实验数据。
                - bullets 恰好 2 条，口语化中文，各不超过 24 字。
                - 不要输出解释、不要 markdown 代码块。
                菜名：%s
                食材：%s
                """.formatted(dish, ingredients);
        try {
            LlmResult result = llm.complete(LlmRequest.json("nutrition-advice",
                    "你是家常菜营养估算助手，输出严谨克制的中文营养建议。", prompt, 0.2, 700, TimeoutTier.FAST));
            if (!result.ok()) return Optional.empty();
            JsonNode root = json.readTree(stripFence(result.text()));
            if (!root.isObject()) return Optional.empty();
            String headline = root.path("headline").asText("").trim();
            if (headline.isEmpty()) return Optional.empty();
            List<GroupAdvice> groups = new ArrayList<>();
            for (JsonNode item : root.path("groups")) {
                String group = item.path("name").asText("").trim();
                String advice = item.path("advice").asText("").trim();
                if (AUDIENCES.contains(group) && !advice.isEmpty()) groups.add(new GroupAdvice(group, advice));
            }
            List<PyramidLayer> pyramid = new ArrayList<>();
            for (JsonNode item : root.path("pyramid")) {
                String key = item.path("key").asText("").trim();
                String value = item.path("value").asText("").trim();
                if (key.isEmpty() || value.isEmpty()) continue;
                pyramid.add(new PyramidLayer(key, value,
                        item.path("advice").asText("适量").trim(),
                        item.path("group").asText("").trim()));
            }
            if (pyramid.size() < 2) return Optional.empty();
            List<String> bullets = new ArrayList<>();
            for (JsonNode item : root.path("bullets")) {
                String text = item.asText("").trim();
                if (!text.isEmpty()) bullets.add(text);
            }
            return Optional.of(new NutritionAdvice(headline,
                    root.path("headlineAdvice").asText("适量").trim(),
                    root.path("keyNutrient").asText("").trim(),
                    root.path("keyValue").asText("").trim(),
                    List.copyOf(groups), List.copyOf(pyramid), List.copyOf(bullets)));
        } catch (Exception e) {
            log.warn("[nutrition] 营养估算失败（返回空，前端不展示）: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static String stripFence(String content) {
        return content == null ? "" : content
                .replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
    }
}
