package com.moodrecipe.backend.service;

import com.moodrecipe.backend.agent.LlmClient;
import com.moodrecipe.backend.agent.LlmRequest;
import com.moodrecipe.backend.agent.LlmResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.config.AppClock;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.service.RecipeIngestionService.Draft;
import com.moodrecipe.backend.service.RecipeIngestionService.IngestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 每周菜谱采集 Job（菜谱库均衡管理的数据入口）。
 * 数据源：HowToCook 开源菜谱库（dishes/<分类>/<菜名>/README.md，Trees API 一次拿全量）。
 * LLM 两段式：①选菜（按主料均衡/上手难度/食材可得性/不重复）②抽取结构化菜谱，入库前硬校验。
 * 调度：每周一 03:37，结果经 WxPusher 推送管理员。
 */
@Service
public class RecipeAcquisitionJob {

    private static final Logger log = LoggerFactory.getLogger(RecipeAcquisitionJob.class);
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final String TREE_API = "https://api.github.com/repos/Anduin2017/HowToCook/git/trees/master?recursive=1";
    private static final String RAW_BASE = "https://raw.githubusercontent.com/Anduin2017/HowToCook/master/";

    /** 库内主料分布画像的关键词桶：给 LLM 的「缺什么补什么」依据。 */
    private static final List<String[]> PROFILE_BUCKETS = List.of(
            new String[]{"蛋类", "蛋"}, new String[]{"鸡肉", "鸡"}, new String[]{"牛肉", "牛"},
            new String[]{"猪肉", "猪"}, new String[]{"虾类", "虾"}, new String[]{"鱼类", "鱼"},
            new String[]{"豆制品", "豆"}, new String[]{"叶菜", "菜"}, new String[]{"瓜茄", "瓜"},
            new String[]{"菌菇", "菇"}, new String[]{"主食", "饭"});

    private final RecipeIngestionService ingestion;
    private final RecipePool recipePool;
    private final LlmClient llm;
    private final WxPusherService wxpusher;
    private final ObjectMapper json;
    private final boolean enabled;
    private final int batchSize;
    private final int maxCandidates;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final AtomicBoolean running = new AtomicBoolean(false);

    public RecipeAcquisitionJob(RecipeIngestionService ingestion, RecipePool recipePool, LlmClient llm,
                                WxPusherService wxpusher, ObjectMapper json,
                                @Value("${recipes.acquire.enabled:true}") boolean enabled,
                                @Value("${recipes.acquire.batch-size:10}") int batchSize,
                                @Value("${recipes.acquire.max-candidates:300}") int maxCandidates) {
        this.ingestion = ingestion;
        this.recipePool = recipePool;
        this.llm = llm;
        this.wxpusher = wxpusher;
        this.json = json;
        this.enabled = enabled;
        this.batchSize = Math.max(1, batchSize);
        this.maxCandidates = Math.max(20, maxCandidates);
    }

    @Scheduled(cron = "${recipes.acquire.cron:0 37 3 ? * MON}", zone = "Asia/Shanghai")
    public void run() {
        if (!enabled || !running.compareAndSet(false, true)) return;
        try {
            if (!llm.isConfigured()) {
                log.warn("菜谱采集跳过：LLM 未配置");
                return;
            }
            Map<String, String> catalog = fetchHowToCookCatalog();
            if (catalog.isEmpty()) {
                log.warn("菜谱采集跳过：HowToCook 目录清单获取失败");
                return;
            }
            Set<String> existing = ingestion.existingNormalizedNames();
            List<Map.Entry<String, String>> missing = catalog.entrySet().stream()
                    .filter(e -> !existing.contains(RecipeIngestionService.normalize(e.getKey())))
                    .limit(maxCandidates)
                    .toList();
            if (missing.isEmpty()) {
                log.info("菜谱采集完成：HowToCook {} 道菜已全部收录或无新增候选", catalog.size());
                return;
            }
            List<Pick> picked = llmPick(missing, libraryProfile());
            int added = 0;
            List<String> addedLines = new ArrayList<>();
            List<String> failed = new ArrayList<>();
            for (Pick pick : picked) {
                try {
                    String markdown = fetchText(RAW_BASE + pick.path());
                    if (markdown.isBlank()) {
                        failed.add(pick.name() + "（README 拉取失败）");
                        continue;
                    }
                    Draft draft = llmExtract(markdown, pick);
                    if (draft == null) {
                        failed.add(pick.name() + "（LLM 抽取失败）");
                        continue;
                    }
                    IngestResult result = ingestion.ingest(draft);
                    if (result.ok()) {
                        added += 1;
                        addedLines.add("- ✅ 「" + pick.name() + "」" + pick.reason() + "，id=" + result.id());
                    } else {
                        failed.add(pick.name() + "（" + result.reason() + "）");
                    }
                } catch (Exception e) {
                    failed.add(pick.name() + "（异常：" + e.getMessage() + "）");
                }
            }
            StringBuilder report = new StringBuilder();
            report.append("## 每周菜谱采集报告\n")
                    .append("时间：").append(AppClock.now().format(HM))
                    .append("　未收录候选 ").append(missing.size())
                    .append(" · LLM 入选 ").append(picked.size())
                    .append(" · **收录 ").append(added).append("**\n\n");
            if (addedLines.isEmpty()) report.append("本周没有新菜入库。\n");
            addedLines.forEach(line -> report.append(line).append('\n'));
            wxpusher.sendToAdmin("每周菜谱采集：" + added + " 道新菜入库",
                    report.toString() + failureSection(failed));
        } catch (Exception e) {
            log.warn("菜谱采集任务失败: {}", e.getMessage());
        } finally {
            running.set(false);
        }
    }

    private record Pick(String name, String path, String reason) { }

    /** GitHub Trees API 一次拉全量：dishes/<分类>/<菜名>/README.md → (菜名, 相对路径)。 */
    private Map<String, String> fetchHowToCookCatalog() {
        Map<String, String> catalog = new LinkedHashMap<>();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(TREE_API))
                    .header("User-Agent", "mood-recipe-backend")
                    .timeout(Duration.ofSeconds(30))
                    .GET().build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode tree = json.readTree(resp.body()).path("tree");
            for (JsonNode node : tree) {
                String path = node.path("path").asText();
                if (!path.startsWith("dishes/") || !path.endsWith("/README.md")) continue;
                String[] parts = path.split("/");
                if (parts.length != 4) continue;
                catalog.putIfAbsent(parts[2], path);
            }
        } catch (Exception e) {
            log.warn("HowToCook 目录清单获取失败: {}", e.getMessage());
        }
        return catalog;
    }

    /** 库内主料分布画像：给 LLM「缺什么补什么」的量化依据。 */
    private String libraryProfile() {
        List<Recipe> all = recipePool.all();
        Map<String, Integer> buckets = new LinkedHashMap<>();
        for (String[] bucket : PROFILE_BUCKETS) buckets.put(bucket[0], 0);
        for (Recipe r : all) {
            String text = (r.getName() == null ? "" : r.getName()) + " "
                    + (r.getIngredients() == null ? "" : r.getIngredients());
            for (String[] bucket : PROFILE_BUCKETS) {
                if (text.contains(bucket[1])) buckets.merge(bucket[0], 1, Integer::sum);
            }
        }
        StringBuilder sb = new StringBuilder("现有菜谱 ").append(all.size()).append(" 道，主料分布：");
        buckets.forEach((k, v) -> sb.append(k).append(' ').append(v).append('、'));
        return sb.toString();
    }

    /** LLM 第一段：从候选里选出最能补均衡、易上手的 batch-size 道。 */
    private List<Pick> llmPick(List<Map.Entry<String, String>> missing, String profile) {
        StringBuilder candidateList = new StringBuilder();
        int i = 1;
        for (Map.Entry<String, String> e : missing) {
            String[] parts = e.getValue().split("/");
            candidateList.append(i++).append(". ").append(e.getKey()).append("（").append(parts[1]).append("）\n");
        }
        String system = """
                你是中餐菜谱库的运营编辑。目标：荤素均衡、主料多样、家常易上手。
                从候选清单中选出最值得收录的菜，要求：
                1) 优先补足现有库稀缺的主料类别（豆制品/蔬菜/菌菇/鱼虾等分布薄的优先）；
                2) 食材常规可得（菜场/超市能买到），步骤可控、适合普通家庭厨房；
                3) 跳过过于猎奇、重复风味、工艺复杂（需要专业设备）的菜；
                4) 数量不超过上限。
                只返回合法 JSON：{"picked":[{"name":"菜名","reason":"≤20字理由"}]}
                """;
        String user = profile + "\n候选清单：\n" + candidateList + "\n最多选 " + batchSize + " 道。";
        LlmResult result = llm.complete(LlmRequest.json("recipe-acquire-pick", system, user, 0.3, 2000,
                com.moodrecipe.backend.agent.TimeoutTier.STANDARD));
        if (!result.ok()) {
            log.warn("采集选菜 LLM 调用失败: {}", result.reason());
            return List.of();
        }
        try {
            List<Pick> picks = new ArrayList<>();
            for (JsonNode node : json.readTree(result.text()).path("picked")) {
                String name = node.path("name").asText("").trim();
                String reason = node.path("reason").asText("").trim();
                String path = missing.stream()
                        .filter(e -> RecipeIngestionService.normalize(e.getKey())
                                .equals(RecipeIngestionService.normalize(name)))
                        .map(Map.Entry::getValue).findFirst().orElse("");
                if (!path.isEmpty()) picks.add(new Pick(name, path, reason.isEmpty() ? "" : "（" + reason + "）"));
            }
            return picks;
        } catch (Exception e) {
            log.warn("采集选菜结果解析失败: {}", e.getMessage());
            return List.of();
        }
    }

    /** LLM 第二段：把 README 抽成结构化菜谱草稿。 */
    private Draft llmExtract(String markdown, Pick pick) {
        String system = """
                你是中餐菜谱编辑。把给定的菜谱 Markdown 抽成结构化 JSON，要求：
                1) description ≤60 字、突出风味与场景，不写营销话术；
                2) ingredients 每条「食材 + 用量」，steps 每条一句话、可执行、按顺序；
                3) cookingTime 数字（分钟）、difficulty ∈ 简单/中等/复杂、mealType ∈ breakfast/lunch/dinner/all；
                4) moodTags 从 开心、平静、疲惫、焦虑、难过、嘴馋、低落、想家、期待、满足、得意、害羞 里选 1-3 个；
                5) 不编造原文没有的步骤；剔除图片链接与无关章节。
                只返回合法 JSON：{"name":"","description":"","ingredients":[],"steps":[],"cookingTime":30,
                "difficulty":"","taste":"","moodTags":[],"mealType":"","season":"四季"}
                """;
        String content = markdown.length() > 6000 ? markdown.substring(0, 6000) : markdown;
        String user = "菜名：" + pick.name() + "\n原文：\n" + content;
        LlmResult result = llm.complete(LlmRequest.json("recipe-acquire-extract", system, user, 0.2, 1600,
                com.moodrecipe.backend.agent.TimeoutTier.LONG));
        if (!result.ok()) return null;
        try {
            JsonNode node = json.readTree(result.text());
            List<String> ingredients = new ArrayList<>();
            node.path("ingredients").forEach(n -> ingredients.add(n.asText()));
            List<String> steps = new ArrayList<>();
            node.path("steps").forEach(n -> steps.add(n.asText()));
            List<String> moodTags = new ArrayList<>();
            node.path("moodTags").forEach(n -> moodTags.add(n.asText()));
            return new Draft(node.path("name").asText(pick.name()), node.path("description").asText(""),
                    ingredients, steps, node.path("cookingTime").asInt(0), node.path("difficulty").asText(""),
                    node.path("taste").asText(""), moodTags, node.path("mealType").asText("all"),
                    node.path("season").asText("四季"), pick.reason());
        } catch (Exception e) {
            log.warn("抽取结果解析失败 {}: {}", pick.name(), e.getMessage());
            return null;
        }
    }

    private String fetchText(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "mood-recipe-backend")
                    .timeout(Duration.ofSeconds(20))
                    .GET().build();
            return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
        } catch (Exception e) {
            return "";
        }
    }

    private String failureSection(List<String> failed) {
        if (failed.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\n**未收录**：\n");
        int shown = 0;
        for (String item : failed) {
            if (shown++ >= 20) {
                sb.append("- ……等共 ").append(failed.size()).append(" 项\n");
                break;
            }
            sb.append("- ").append(item).append('\n');
        }
        return sb.toString();
    }
}
