package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.agent.LlmClient;
import com.moodrecipe.backend.agent.LlmRequest;
import com.moodrecipe.backend.agent.LlmResult;
import com.moodrecipe.backend.agent.TimeoutTier;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.repository.RecipeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 菜谱心情定时精准分类。
 *
 * 背景：心情标签（mood_tags）目前来自生成时刻的随手赋值/种子数据，准确率差，
 * 藏菜阁按心情筛选经常翻空。本任务用 LLM 按固定 12 心情白名单周期性复核：
 *  1) 从未分类的（mood_classified_at 为 NULL，含种子与新生成）优先补齐；
 *  2) 其余按最久未复核轮转，越跑越准。
 * 分类失败不动旧标签（宁可保持现状，也不清空导致筛选翻空）。
 */
@Service
public class RecipeMoodClassificationJob {

    private static final Logger log = LoggerFactory.getLogger(RecipeMoodClassificationJob.class);

    /** 与前端固定心情集合保持一致（首页 pickMeal 的 12 心情）。 */
    private static final Set<String> MOOD_WHITELIST = Set.of(
            "开心", "平静", "疲惫", "焦虑", "难过", "嘴馋", "低落", "想家", "期待", "满足", "得意", "害羞");

    private static final String SYSTEM_PROMPT = """
            你是中餐菜谱编辑，负责给菜谱打「适合的心情场景」标签。
            只能从这个固定集合里选 1-3 个最匹配的：开心、平静、疲惫、焦虑、难过、嘴馋、低落、想家、期待、满足、得意、害羞。
            判断口径：汤羹炖菜/家常热炒→想家、满足；快手小炒/下饭菜→开心、满足；甜品零食/夜宵→嘴馋、开心；
            清淡轻食/减脂餐→平静、期待；硬菜宴客菜→期待、得意；一人食简餐→疲惫、平静；早餐→期待、平静。
            只返回合法 JSON，格式严格为：{"moodTags":["满足","想家"]}
            """;

    /** 单批上限：白天增量小批，夜间大批复核。 */
    private static final int INCREMENTAL_BATCH = 12;
    private static final int NIGHTLY_BATCH = 150;

    private final RecipeRepository repository;
    private final LlmClient llm;
    private final ObjectMapper json;
    private final boolean enabled;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public RecipeMoodClassificationJob(RecipeRepository repository,
                                       LlmClient llm,
                                       ObjectMapper json,
                                       @Value("${ai.mood-classify.enabled:true}") boolean enabled) {
        this.repository = repository;
        this.llm = llm;
        this.json = json;
        this.enabled = enabled;
    }

    /** 白天每 30 分钟小批补齐「从未分类」的（新入库 AI 菜谱 + 种子积压），启动 2 分钟后先跑一轮。 */
    @Scheduled(fixedDelay = 30 * 60 * 1000, initialDelay = 2 * 60 * 1000)
    public void runIncremental() {
        classifyBatch(INCREMENTAL_BATCH, true);
    }

    /** 每天凌晨 4:37 大批复核（错开 3:17 的菜谱生成），最久未复核的优先，全池缓慢轮转。 */
    @Scheduled(cron = "0 37 4 * * ?")
    public void runNightly() {
        classifyBatch(NIGHTLY_BATCH, false);
    }

    /** onlyUnclassified=true 时跳过已分类的，只补积压；否则按「未分类优先、最久未复核其次」轮转。 */
    private void classifyBatch(int limit, boolean onlyUnclassified) {
        if (!enabled || running.getAndSet(true)) return;
        try {
            if (!llm.isConfigured()) return;
            List<Recipe> batch = repository.findClassificationBatch(limit);
            int done = 0;
            for (Recipe recipe : batch) {
                if (onlyUnclassified && recipe.getMoodClassifiedAt() != null) continue;
                if (classifyOne(recipe)) done++;
            }
            if (done > 0)
                log.info("[心情精准分类] 本批 {} 道，更新 {} 道（onlyUnclassified={}）", batch.size(), done, onlyUnclassified);
        } catch (Exception e) {
            log.warn("[心情精准分类] 本轮失败：{}", e.getMessage());
        } finally {
            running.set(false);
        }
    }

    /** @return 是否实际更新了标签（失败/未解析出有效标签时保留旧值，仅推进复核时间）。 */
    private boolean classifyOne(Recipe recipe) {
        String name = recipe.getName() == null ? "" : recipe.getName().trim();
        if (name.isEmpty()) return advanceOnly(recipe);
        String desc = recipe.getDescription() == null ? "" : recipe.getDescription();
        String ingredients = recipe.getIngredients() == null ? "" : recipe.getIngredients();
        String user = """
                菜名：%s
                简介：%s
                食材：%s
                """.formatted(name, abbreviate(desc, 60), abbreviate(ingredients, 160));

        LlmResult result = llm.complete(LlmRequest.json(
                "recipe-mood-classify", SYSTEM_PROMPT, user, 0.1, 256, TimeoutTier.STANDARD));
        if (!result.ok()) return advanceOnly(recipe);

        try {
            JsonNode node = json.readTree(result.text());
            JsonNode tags = node.path("moodTags");
            if (!tags.isArray() || tags.isEmpty()) return advanceOnly(recipe);
            Set<String> valid = new LinkedHashSet<>();
            for (JsonNode t : tags) {
                String s = t.asText("").trim();
                if (MOOD_WHITELIST.contains(s)) valid.add(s);
            }
            if (valid.isEmpty()) return advanceOnly(recipe);
            String joined = String.join(",", valid);
            boolean changed = !joined.equals(recipe.getMoodTags());
            recipe.setMoodTags(joined);
            recipe.setMoodClassifiedAt(LocalDateTime.now());
            repository.save(recipe);
            return changed;
        } catch (Exception e) {
            log.debug("[心情精准分类] 解析失败 {}: {}", name, e.getMessage());
            return advanceOnly(recipe);
        }
    }

    /** 分类未成功也要推进时间戳，否则这道菜会一直排最前反复重试同一道。 */
    private boolean advanceOnly(Recipe recipe) {
        recipe.setMoodClassifiedAt(LocalDateTime.now());
        repository.save(recipe);
        return false;
    }

    private static String abbreviate(String s, int max) {
        if (s == null) return "";
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max);
    }
}
