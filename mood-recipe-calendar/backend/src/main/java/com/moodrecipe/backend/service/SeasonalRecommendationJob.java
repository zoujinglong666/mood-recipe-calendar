package com.moodrecipe.backend.service;

import com.moodrecipe.backend.agent.LlmClient;
import com.moodrecipe.backend.agent.LlmRequest;
import com.moodrecipe.backend.agent.LlmResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.config.AppClock;
import com.moodrecipe.backend.entity.SeasonalIngredient;
import com.moodrecipe.backend.repository.SeasonalIngredientRepository;
import com.moodrecipe.backend.service.RecipeIngestionService.Draft;
import com.moodrecipe.backend.service.RecipeIngestionService.IngestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 每月时令菜推荐 Job（菜谱库均衡管理的时令补给）。
 * 数据源：seasonal_ingredients 表（可商用、启用的时令食材，startMonth/endMonth 区间，支持跨年）。
 * LLM 按当月时令食材 + 现有库菜名推荐新菜（结构化 JSON），入库前过 RecipeIngestionService 硬校验。
 * 调度：每月 1 日 04:57，收录结果经 WxPusher 推送管理员。
 */
@Service
public class SeasonalRecommendationJob {

    private static final Logger log = LoggerFactory.getLogger(SeasonalRecommendationJob.class);
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final SeasonalIngredientRepository seasonal;
    private final RecipeIngestionService ingestion;
    private final LlmClient llm;
    private final WxPusherService wxpusher;
    private final ObjectMapper json;
    private final boolean enabled;
    private final int batchSize;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SeasonalRecommendationJob(SeasonalIngredientRepository seasonal, RecipeIngestionService ingestion,
                                     LlmClient llm, WxPusherService wxpusher, ObjectMapper json,
                                     @Value("${recipes.seasonal.enabled:true}") boolean enabled,
                                     @Value("${recipes.seasonal.batch-size:8}") int batchSize) {
        this.seasonal = seasonal;
        this.ingestion = ingestion;
        this.llm = llm;
        this.wxpusher = wxpusher;
        this.json = json;
        this.enabled = enabled;
        this.batchSize = Math.max(1, batchSize);
    }

    @Scheduled(cron = "${recipes.seasonal.cron:0 57 4 1 * ?}", zone = "Asia/Shanghai")
    public void run() {
        if (!enabled || !running.compareAndSet(false, true)) return;
        try {
            if (!llm.isConfigured()) {
                log.warn("时令推荐跳过：LLM 未配置");
                return;
            }
            int month = AppClock.today().getMonthValue();
            List<String> inSeason = seasonal.findByEnabledTrueAndLicenseStatus("COMMERCIALLY_USABLE").stream()
                    .filter(item -> inSeason(item, month))
                    .map(SeasonalIngredient::getName)
                    .filter(name -> name != null && !name.isBlank())
                    .distinct()
                    .toList();
            if (inSeason.isEmpty()) {
                log.info("时令推荐跳过：{} 月无可用时令食材数据", month);
                return;
            }
            List<Draft> drafts = llmPropose(month, inSeason);
            int added = 0;
            List<String> addedLines = new ArrayList<>();
            List<String> failed = new ArrayList<>();
            for (Draft draft : drafts) {
                IngestResult result = ingestion.ingest(draft);
                if (result.ok()) {
                    added += 1;
                    addedLines.add("- ✅ 「" + draft.name() + "」，id=" + result.id());
                } else {
                    failed.add(draft.name() + "（" + result.reason() + "）");
                }
            }
            StringBuilder report = new StringBuilder();
            report.append("## ").append(month).append(" 月时令菜推荐报告\n")
                    .append("时间：").append(AppClock.now().format(HM))
                    .append("　时令食材：").append(String.join("、", inSeason))
                    .append("\nLLM 推荐 ").append(drafts.size()).append(" 道 · **收录 ")
                    .append(added).append("**\n\n");
            if (addedLines.isEmpty()) report.append("本月没有新菜入库。\n");
            addedLines.forEach(line -> report.append(line).append('\n'));
            if (!failed.isEmpty()) {
                report.append("\n**未收录**：\n");
                failed.forEach(item -> report.append("- ").append(item).append('\n'));
            }
            wxpusher.sendToAdmin("时令菜推荐：" + added + " 道新菜入库", report.toString());
        } catch (Exception e) {
            log.warn("时令推荐任务失败: {}", e.getMessage());
        } finally {
            running.set(false);
        }
    }

    /** 月份区间命中（支持跨年，如 11→2）。 */
    private boolean inSeason(SeasonalIngredient item, int month) {
        Integer start = item.getStartMonth();
        Integer end = item.getEndMonth();
        if (start == null || end == null) return false;
        return start <= end ? month >= start && month <= end : month >= start || month <= end;
    }

    /** LLM 生成以时令食材为主料的完整菜谱草稿。 */
    private List<Draft> llmPropose(int month, List<String> inSeason) {
        String existingSample = ingestion.existingNormalizedNames().stream()
                .limit(150)
                .collect(Collectors.joining("、"));
        String system = """
                你是中餐菜谱编辑。基于当月时令食材设计新菜，要求：
                1) 每道菜以给定的时令食材为主料之一，荤素搭配、家常易上手；
                2) 不能与现有菜谱重名（现有菜名清单已给出）；
                3) description ≤60 字；ingredients 每条「食材 + 用量」；steps 每条一句话可执行；
                4) cookingTime 数字（分钟）、difficulty ∈ 简单/中等/复杂、mealType ∈ breakfast/lunch/dinner/all；
                5) moodTags 从 开心、平静、疲惫、焦虑、难过、嘴馋、低落、想家、期待、满足、得意、害羞 里选 1-3 个。
                只返回合法 JSON 数组：[{"name":"","description":"","ingredients":[],"steps":[],
                "cookingTime":30,"difficulty":"","taste":"","moodTags":[],"mealType":"","season":""}]
                """;
        String user = "现在是 " + month + " 月，时令食材：" + String.join("、", inSeason)
                + "\n现有菜谱（不要重名）：" + existingSample + "\n最多推荐 " + batchSize + " 道。";
        LlmResult result = llm.complete(LlmRequest.json("seasonal-recommend", system, user, 0.4, 3000,
                com.moodrecipe.backend.agent.TimeoutTier.LONG));
        if (!result.ok()) {
            log.warn("时令推荐 LLM 调用失败: {}", result.reason());
            return List.of();
        }
        try {
            List<Draft> drafts = new ArrayList<>();
            for (JsonNode node : json.readTree(result.text())) {
                String name = node.path("name").asText("").trim();
                if (name.isEmpty()) continue;
                List<String> ingredients = new ArrayList<>();
                node.path("ingredients").forEach(n -> ingredients.add(n.asText()));
                List<String> steps = new ArrayList<>();
                node.path("steps").forEach(n -> steps.add(n.asText()));
                List<String> moodTags = new ArrayList<>();
                node.path("moodTags").forEach(n -> moodTags.add(n.asText()));
                drafts.add(new Draft(name, node.path("description").asText(""), ingredients, steps,
                        node.path("cookingTime").asInt(0), node.path("difficulty").asText(""),
                        node.path("taste").asText(""), moodTags, node.path("mealType").asText("all"),
                        month + "月时令", "时令推荐"));
            }
            return drafts;
        } catch (Exception e) {
            log.warn("时令推荐结果解析失败: {}", e.getMessage());
            return List.of();
        }
    }
}
