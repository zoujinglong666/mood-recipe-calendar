package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.entity.RecipeInteraction;
import com.moodrecipe.backend.entity.UserFoodPreference;
import com.moodrecipe.backend.repository.RecipeInteractionRepository;
import com.moodrecipe.backend.repository.RecipeRepository;
import com.moodrecipe.backend.repository.UserFoodPreferenceRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 锅仔智能体——后端统一入口。
 *
 * 整合三大能力：
 * 1. 菜谱推荐：AI 真实生成优先，失败回退本地菜谱库（结合偏好过滤+历史反馈排序）
 * 2. 深度陪伴寄语：基于情绪趋势+用餐习惯+连续记录，锅仔主动分析后生成个性化寄语
 * 3. 情绪洞察：分析用户最近心情趋势，给出一句话总结
 *
 * 所有 AI 调用的人格 prompt 统一由 GuozaiPersona 管理，
 * 用户记忆统一由 GuozaiMemory 聚合。
 */
@Service
public class GuozaiAgent {

    private static final Map<String, List<String>> CUISINE_KEYWORDS = Map.of(
            "川菜", List.of("麻婆", "宫保", "回锅", "鱼香", "水煮", "辣子", "口水鸡", "酸菜鱼", "担担"),
            "湘菜", List.of("剁椒", "小炒肉", "辣椒炒肉", "农家", "腊肉"),
            "粤菜", List.of("白切鸡", "叉烧", "煲仔", "河粉", "云吞", "豉汁", "白灼", "老火汤"),
            "江浙菜", List.of("东坡", "糖醋", "红烧", "清蒸", "油焖", "西湖", "葱油", "狮子头"),
            "东北菜", List.of("锅包肉", "地三鲜", "乱炖", "小鸡炖蘑菇", "酸菜", "酱骨"),
            "西北菜", List.of("羊肉", "牛肉面", "凉皮", "肉夹馍", "孜然", "臊子"),
            "云贵菜", List.of("酸汤", "过桥米线", "汽锅", "折耳根", "菌菇"),
            "日韩料理", List.of("泡菜", "寿司", "照烧", "石锅", "部队锅", "味噌", "咖喱"));

    private final AiRecipeService aiRecipeService;
    private final GuozaiMemory memory;
    private final GuozaiPersona persona;
    private final RecipeRepository recipeRepository;
    private final RecipeInteractionRepository interactions;
    private final UserFoodPreferenceRepository preferences;
    private final OperationalEventService operationalEvents;
    private final RecommendationExposureService exposures;

    public GuozaiAgent(AiRecipeService aiRecipeService, GuozaiMemory memory, GuozaiPersona persona,
                       RecipeRepository recipeRepository, RecipeInteractionRepository interactions,
                       UserFoodPreferenceRepository preferences, OperationalEventService operationalEvents,
                       RecommendationExposureService exposures) {
        this.aiRecipeService = aiRecipeService;
        this.memory = memory;
        this.persona = persona;
        this.recipeRepository = recipeRepository;
        this.interactions = interactions;
        this.preferences = preferences;
        this.operationalEvents = operationalEvents;
        this.exposures = exposures;
    }

    // ==================== 能力一：菜谱推荐 ====================

    /**
     * 锅仔推荐菜谱——智能体完整流程：
     * 1. 聚合用户记忆（口味偏好 + 情绪趋势 + 历史记录）
     * 2. 锅仔主动分析（不是简单拼接，而是提炼洞察）
     * 3. 基于分析构建 prompt，调用 AI 生成菜谱
     * 4. 动态生成推荐理由（结合用户真实行为细节）
     * AI 失败时回退本地菜谱库。
     */
    public Recipe recommend(String openid, String mood, RecommendationJobService.Progress progress) {
        update(progress, RecommendationJobService.Stage.MEMORY,
                RecommendationJobService.StepStatus.RUNNING, "锅仔正在回想你的吃饭习惯");
        UserFoodPreference preference = preferences.findByOpenid(openid).orElse(null);

        // 智能体记忆聚合：口味偏好 + 情绪趋势 + 历史记录
        int hour = java.time.LocalTime.now().getHour();
        GuozaiMemory.MemorySnapshot snapshot = memory.snapshot(openid, hour);
        String userAnalysis = memory.buildAnalysis(snapshot);

        update(progress, RecommendationJobService.Stage.MEMORY,
                RecommendationJobService.StepStatus.COMPLETED,
                snapshot.streak() > 0
                        ? "已记起你连续记录 " + snapshot.streak() + " 天，最近常做 " + snapshot.topDish()
                        : "还在了解你的口味，这次先按心情推荐");

        // 优先 AI 智能体推荐（基于记忆主动分析，不是简单传 mood+preference）
        try {
            String preferenceText = preferencePrompt(preference);
            String smartPrompt = persona.recipePrompt(mood, userAnalysis, preferenceText);
            Optional<Recipe> aiRecipe = aiRecipeService.recommendWithPersona(mood, smartPrompt,
                    event -> updateAiProgress(openid, progress, event));
            if (aiRecipe.isPresent() && allowedByPreference(aiRecipe.get(), preference)
                    && !exposures.wasRecentlyShownOrRejected(openid, aiRecipe.get())) {
                Recipe generated = aiRecipe.get();
                update(progress, RecommendationJobService.Stage.FINALIZE,
                        RecommendationJobService.StepStatus.RUNNING, "锅仔正在整理这道菜");
                // 动态推荐理由：结合记忆分析 + AI 生成的 description
                generated.setRecommendationReason(buildSmartReason(mood, snapshot, preference, generated.getDescription()));
                generated.setExposureId(exposures.recordShown(openid, generated, "AI"));
                update(progress, RecommendationJobService.Stage.FINALIZE,
                        RecommendationJobService.StepStatus.COMPLETED, "菜谱已经整理完成");
                return generated;
            }
        } catch (Exception ignored) {
            // AI 不可用时静默回退
        }

        // 回退：本地菜谱库
        return fallbackLocalRecipe(openid, mood, preference, progress);
    }

    /** 深度推荐（已购权益）——结合用户输入的食材、时长、口味 + 锅仔记忆分析。 */
    public Optional<Recipe> deepRecommend(String openid, String mood, String ingredients,
                                          String maxMinutes, String userPreference) {
        UserFoodPreference foodPref = preferences.findByOpenid(openid).orElse(null);
        int hour = java.time.LocalTime.now().getHour();
        GuozaiMemory.MemorySnapshot snapshot = memory.snapshot(openid, hour);
        String userAnalysis = memory.buildAnalysis(snapshot);

        String preference = "已有食材：" + safe(ingredients)
                + "；期望时长：" + safe(maxMinutes)
                + "；本次口味与忌口：" + safe(userPreference)
                + "；长期口味记忆：" + preferencePrompt(foodPref);
        String smartPrompt = persona.recipePrompt(mood, userAnalysis, preference);
        Optional<Recipe> generated = aiRecipeService.recommendWithPersona(mood, smartPrompt, ignored -> { });
        if (generated.isEmpty() || !allowedByPreference(generated.get(), foodPref)
                || exposures.wasRecentlyShownOrRejected(openid, generated.get())) {
            return Optional.empty();
        }
        generated.get().setExposureId(exposures.recordShown(openid, generated.get(), "AI_DEEP"));
        return generated;
    }

    /**
     * 周计划智能体：先读取长期记忆和近期反馈，再为每天选择主菜/配菜组合。
     * 该规划完全基于本地菜谱库，因此文本模型不可用时也不会退化为顺序取菜。
     */
    public List<Recipe> planWeeklyMenu(String openid, int days, int dishesPerDay, String requestedHealthGoal) {
        UserFoodPreference preference = preferences.findByOpenid(openid).orElse(null);
        GuozaiMemory.MemorySnapshot snapshot = memory.snapshot(openid, java.time.LocalTime.now().getHour());
        Set<Long> rejected = interactions.findByOpenidAndAction(openid, "DISLIKE").stream()
                .map(RecipeInteraction::getRecipeId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Integer> feedback = new HashMap<>();
        interactions.findTop30ByOpenidOrderByCreatedAtDesc(openid).forEach(interaction -> {
            int value = switch (interaction.getAction()) {
                case "MADE" -> 5;
                case "LIKE" -> 3;
                default -> 0;
            };
            feedback.merge(interaction.getRecipeId(), value, Integer::sum);
        });
        List<Recipe> candidates = recipeRepository.findAll().stream()
                .filter(recipe -> !rejected.contains(recipe.getId()))
                .filter(recipe -> allowedByPreference(recipe, preference)).toList();
        if (candidates.isEmpty()) return List.of();

        int safeDays = Math.max(1, Math.min(days, 7));
        int safeDishes = Math.max(1, Math.min(dishesPerDay, 3));
        Set<Long> used = new HashSet<>();
        List<Recipe> menu = new ArrayList<>();
        for (int day = 0; day < safeDays; day++) {
            for (int course = 0; course < safeDishes; course++) {
                boolean sideDish = safeDishes > 1 && course > 0;
                List<Recipe> pool = candidates.stream().filter(recipe -> !used.contains(recipe.getId())).toList();
                if (pool.isEmpty()) pool = candidates;
                Recipe picked = pool.stream().max(Comparator.comparingInt(recipe -> weeklyPlanScore(
                        recipe, preference, snapshot, feedback, requestedHealthGoal, sideDish))).orElse(null);
                if (picked != null) {
                    menu.add(picked);
                    used.add(picked.getId());
                }
            }
        }
        return menu;
    }

    private Recipe fallbackLocalRecipe(String openid, String mood, UserFoodPreference preference,
                                       RecommendationJobService.Progress progress) {
        update(progress, RecommendationJobService.Stage.TEXT,
                RecommendationJobService.StepStatus.DEGRADED, "文本模型暂时不可用，改从锅仔菜谱库挑选");
        update(progress, RecommendationJobService.Stage.IMAGE,
                RecommendationJobService.StepStatus.DEGRADED, "本地菜谱使用已有封面");
        if (progress != null) progress.usedFallback();
        update(progress, RecommendationJobService.Stage.LOCAL_FALLBACK,
                RecommendationJobService.StepStatus.RUNNING, "正在按忌口、喜欢和最近看过的菜筛选");

        List<RecipeInteraction> history = interactions.findTop30ByOpenidOrderByCreatedAtDesc(openid);
        Set<Long> rejected = interactions.findByOpenidAndAction(openid, "DISLIKE").stream()
                .map(RecipeInteraction::getRecipeId).collect(Collectors.toSet());
        Set<Long> recentlyShown = history.stream()
                .filter(i -> "SHOWN".equals(i.getAction())).limit(8)
                .map(RecipeInteraction::getRecipeId).collect(Collectors.toSet());
        Map<Long, Integer> score = new HashMap<>();
        history.forEach(i -> score.merge(i.getRecipeId(), switch (i.getAction()) {
            case "LIKE" -> 3;
            case "MADE" -> 5;
            default -> 0;
        }, Integer::sum));

        List<Recipe> candidates = recipeRepository.findByMoodTag(mood).stream()
                .filter(r -> !rejected.contains(r.getId()) && allowedByPreference(r, preference)).toList();
        if (candidates.isEmpty()) {
            candidates = recipeRepository.findAll().stream()
                    .filter(r -> !rejected.contains(r.getId()) && allowedByPreference(r, preference)).toList();
        }
        if (candidates.isEmpty()) {
            update(progress, RecommendationJobService.Stage.LOCAL_FALLBACK,
                    RecommendationJobService.StepStatus.FAILED, "没有找到符合当前忌口的菜");
            operationalEvents.record("AI_RECOMMEND_FAILED", "ALERT", openid, null,
                    "AI unavailable and no local candidate");
            return null;
        }
        List<Recipe> unseen = candidates.stream().filter(r -> !recentlyShown.contains(r.getId())).toList();
        if (!unseen.isEmpty()) candidates = unseen;

        Recipe recipe = candidates.stream()
                .max(Comparator.comparingInt((Recipe r) -> score.getOrDefault(r.getId(), 0) + preferenceScore(r, preference))
                        .thenComparing(Recipe::getId, Comparator.reverseOrder()))
                .orElse(null);
        if (recipe != null) {
            recipe.setRecommendationReason(localRecommendationReason(recipe, mood, preference, score));
            recordInteraction(openid, recipe.getId(), "SHOWN");
        }
        update(progress, RecommendationJobService.Stage.LOCAL_FALLBACK,
                RecommendationJobService.StepStatus.COMPLETED, "已从本地菜谱库找到合适的一道");
        update(progress, RecommendationJobService.Stage.FINALIZE,
                RecommendationJobService.StepStatus.RUNNING, "正在整理推荐理由和做法");
        update(progress, RecommendationJobService.Stage.FINALIZE,
                RecommendationJobService.StepStatus.COMPLETED, "菜谱已经整理完成");
        return recipe;
    }

    private int weeklyPlanScore(Recipe recipe, UserFoodPreference preference, GuozaiMemory.MemorySnapshot snapshot,
                                Map<Long, Integer> feedback, String requestedHealthGoal, boolean sideDish) {
        String text = searchableText(recipe);
        int score = preferenceScore(recipe, preference) + feedback.getOrDefault(recipe.getId(), 0);
        boolean lightSide = containsAny(text, "西兰花", "生菜", "油菜", "空心菜", "菜心", "黄瓜", "木耳", "百合", "菌菇", "汤");
        score += sideDish == lightSide ? 7 : -3;
        if (!snapshot.topDish().isBlank() && text.contains(snapshot.topDish())) score -= 4;
        if ("FITNESS".equals(requestedHealthGoal) && containsAny(text, "鸡", "牛", "鱼", "虾", "蛋", "豆腐")) score += 5;
        if ("LEAN".equals(requestedHealthGoal) && containsAny(text, "清蒸", "白灼", "蔬菜", "西兰花", "菌菇", "番茄")) score += 5;
        return score;
    }

    // ==================== 能力二：深度陪伴寄语 ====================

    /**
     * 锅仔深度寄语——不是简单问候，而是基于情绪趋势、用餐习惯、连续记录等
     * 真实数据主动分析后，由 AI 生成的个性化寄语。
     */
    public CompanionMessageService.Message companion(String openid, int hour) {
        memory.rememberHomeOpen(openid, hour);
        GuozaiMemory.MemorySnapshot snap = memory.snapshot(openid, hour);
        String greeting = greeting(snap.period());
        String analysis = memory.buildAnalysis(snap);
        String fallback = buildFallbackMessage(snap);
        String message = aiRecipeService.companionMessageWithPersona(
                persona.companionPrompt(analysis)).orElse(fallback);
        String insight = buildInsight(snap);
        String actionText = snap.recordedToday() ? "看看今天的食光" : "告诉我现在的心情";
        return new CompanionMessageService.Message(greeting, message, insight, actionText);
    }

    // ==================== 能力三：情绪洞察 ====================

    /**
     * 锅仔情绪洞察——分析用户最近心情趋势，给出一句话总结。
     */
    public String moodInsight(String openid) {
        GuozaiMemory.MemorySnapshot snap = memory.snapshot(openid, 12);
        if (snap.isNewUser()) {
            return "从第一顿开始认识你，慢慢来。";
        }
        GuozaiMemory.MoodTrend trend = snap.moodTrend();
        if (trend.last30Days().isEmpty()) {
            return "记录还不多，锅仔正在慢慢了解你。";
        }
        String trendData = buildTrendData(trend);
        return aiRecipeService.insightMessageWithPersona(
                persona.moodInsightPrompt(trendData)).orElse(defaultInsight(trend));
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 动态生成推荐理由——不是固定模板，而是结合用户记忆和 AI 描述。
     * 优先级：AI 生成的 description > 基于连续记录/常做菜的动态理由 > 固定模板
     */
    private String buildSmartReason(String mood, GuozaiMemory.MemorySnapshot snap,
                                    UserFoodPreference preference, String aiDescription) {
        String healthReason = healthRecommendationReason(preference);
        // 1. 如果 AI 生成了有意义的描述，直接用（它已经基于记忆分析）
        if (aiDescription != null && !aiDescription.isBlank() && aiDescription.length() > 4) {
            return healthReason.isBlank() ? aiDescription : healthReason + aiDescription;
        }
        if (!healthReason.isBlank()) return healthReason;
        // 2. 基于连续记录天数
        if (snap.streak() >= 7) {
            return "你已经连续记录 " + snap.streak() + " 天了，锅仔记得你常做 " + snap.topDish()
                    + "，今天「" + mood + "」换这道试试。";
        }
        if (snap.streak() >= 3) {
            return "连续 " + snap.streak() + " 天好好吃饭，锅仔给你点个赞。今天「" + mood + "」，这道正合适。";
        }
        // 3. 基于常做菜
        if (!snap.topDish().isBlank() && !snap.topDish().equals("还在了解")) {
            return "锅仔看你常做 " + snap.topDish() + "，今天「" + mood + "」，给你换个口味。";
        }
        // 4. 新用户/无记忆
        if (snap.isNewUser()) {
            return "锅仔还在了解你，先根据你现在「" + mood + "」的心情推荐这道。";
        }
        // 5. 兜底
        return "根据你现在「" + mood + "」的心情，锅仔特意为你想了这道菜。";
    }

    private String buildTrendData(GuozaiMemory.MoodTrend trend) {
        StringBuilder sb = new StringBuilder();
        sb.append("最近30天心情分布：");
        trend.last30Days().forEach((mood, count) -> sb.append(mood).append("(").append(count).append("次) "));
        if (!trend.recentShift().isBlank()) {
            sb.append("；").append(trend.recentShift());
        }
        if (!trend.moodDishPairs().isEmpty()) {
            sb.append("；").append(String.join("；", trend.moodDishPairs()));
        }
        return sb.toString();
    }

    private String defaultInsight(GuozaiMemory.MoodTrend trend) {
        if (!trend.recentShift().isBlank()) {
            return trend.recentShift() + "，锅仔都看在眼里。";
        }
        if (!"还在了解".equals(trend.dominantMood())) {
            return "最近「" + trend.dominantMood() + "」多一些，好好吃饭就好。";
        }
        return "每一餐都在帮锅仔更懂你。";
    }

    private String buildFallbackMessage(GuozaiMemory.MemorySnapshot snap) {
        List<String> candidates = new ArrayList<>();
        if (snap.isNewUser()) {
            candidates.add("先告诉我几样爱吃的，往后的每一顿我都会更懂你。");
        }
        if (!snap.favoriteCuisine().isBlank()) {
            candidates.add("你喜欢的「" + snap.favoriteCuisine() + "」我记在心里了，今天也往这个方向替你挑。");
        }
        if (!snap.favorite().isBlank()) {
            candidates.add("你常惦记的「" + snap.favorite() + "」我记得，今天也挑一道合口味的。");
        }
        if (!snap.topMood().isBlank()) {
            candidates.add("最近「" + snap.topMood() + "」出现得多，今天给自己留一顿舒服的饭吧。");
        }
        if (snap.streak() >= 2) {
            candidates.add("已经连续好好吃饭 " + snap.streak() + " 天了，这份认真很值得被记住。");
        }
        if (snap.recordedToday()) {
            candidates.add("今天这顿已经被我收好了，晚些时候也别忘了喝水休息。");
        }
        if (!"还在了解".equals(snap.usualPeriod())) {
            candidates.add("你常在" + snap.usualPeriod() + "来找我，这个饭点我会提前替你多想一步。");
        }
        if (snap.preference() != null && Boolean.FALSE.equals(snap.preference().getEatCilantro())) {
            candidates.add("不放香菜这件事我一直记得，放心把今天这顿交给我。");
        }
        String healthMessage = healthCompanionMessage(snap.preference());
        if (!healthMessage.isBlank()) candidates.add(healthMessage);
        candidates.add(switch (snap.period()) {
            case "早晨" -> "早饭不用复杂，热乎、顺口，就能给今天一个温柔的开始。";
            case "午间" -> "忙归忙，午饭还是要认真吃，锅仔帮你挑一道省心的。";
            case "下午" -> "现在想好晚饭，等饿的时候就不用匆忙做决定了。";
            case "晚间" -> "今天发生了很多事，先用一顿合胃口的饭把自己接住。";
            default -> "这么晚还没休息，就选点清淡省事的，吃完早点睡。";
        });
        return candidates.get(Math.floorMod(Objects.hash(snap.usualPeriod(), snap.favorite(), snap.period()),
                candidates.size()));
    }

    private String buildInsight(GuozaiMemory.MemorySnapshot snap) {
        if (snap.isNewUser()) return "从第一顿开始认识你";
        if (snap.streak() >= 2) return "记得你已连续记录 " + snap.streak() + " 天";
        if (!healthGoalLabel(snap.preference()).isBlank()) return "记得你想" + healthGoalLabel(snap.preference());
        if (!snap.favoriteCuisine().isBlank()) return "记得你喜欢 " + snap.favoriteCuisine();
        if (!snap.favorite().isBlank()) return "记得你喜欢 " + snap.favorite();
        if (snap.preference() != null && snap.preference().isOnboardingCompleted())
            return "你的口味和忌口都收好了";
        if (!"还在了解".equals(snap.usualPeriod())) return "发现你常在" + snap.usualPeriod() + "来看看";
        return "每次选择，都让我更懂你";
    }

    private String greeting(String period) {
        return switch (period) {
            case "早晨" -> "早上好，先把自己照顾好";
            case "午间" -> "到饭点了，别让肚子等太久";
            case "下午" -> "下午好，想想今晚吃什么";
            case "晚间" -> "晚上好，今天辛苦啦";
            default -> "夜深了，吃点轻松温暖的";
        };
    }

    private void updateAiProgress(String openid, RecommendationJobService.Progress progress,
                                  AiRecipeService.GenerationEvent event) {
        if (event == AiRecipeService.GenerationEvent.TEXT_FAILED) {
            recordAiFailure(openid, "text generation unavailable");
        } else if (event == AiRecipeService.GenerationEvent.IMAGE_FAILED) {
            recordAiFailure(openid, "cover generation unavailable");
        }
        if (progress == null) return;
        switch (event) {
            case TEXT_STARTED -> progress.update(RecommendationJobService.Stage.TEXT,
                    RecommendationJobService.StepStatus.RUNNING, "锅仔正在想今天吃什么");
            case TEXT_COMPLETED -> progress.update(RecommendationJobService.Stage.TEXT,
                    RecommendationJobService.StepStatus.COMPLETED, "菜名、食材和做法已经想好");
            case TEXT_FAILED -> progress.update(RecommendationJobService.Stage.TEXT,
                    RecommendationJobService.StepStatus.DEGRADED, "锅仔暂时没想好，准备切换本地菜谱");
            case IMAGE_STARTED -> progress.update(RecommendationJobService.Stage.IMAGE,
                    RecommendationJobService.StepStatus.RUNNING, "正在请图像模型制作菜品封面");
            case IMAGE_COMPLETED -> progress.update(RecommendationJobService.Stage.IMAGE,
                    RecommendationJobService.StepStatus.COMPLETED, "菜品封面已经做好");
            case IMAGE_FAILED -> progress.update(RecommendationJobService.Stage.IMAGE,
                    RecommendationJobService.StepStatus.DEGRADED, "封面暂时没做好，使用锅仔占位图");
        }
    }

    private void recordAiFailure(String openid, String detail) {
        try {
            operationalEvents.record("AI_RECOMMEND_FAILED", "WARN", openid, null, detail);
        } catch (Exception ignored) {
            // 告警入库失败不能阻断推荐
        }
    }

    private void update(RecommendationJobService.Progress progress, RecommendationJobService.Stage stage,
                        RecommendationJobService.StepStatus status, String message) {
        if (progress != null) progress.update(stage, status, message);
    }

    private boolean allowedByPreference(Recipe recipe, UserFoodPreference preference) {
        if (preference == null) return true;
        String text = searchableText(recipe);
        List<String> blocked = new ArrayList<>(terms(preference.getAvoidIngredients()));
        blocked.addAll(terms(preference.getAllergens()));
        if (Boolean.FALSE.equals(preference.getEatScallion())) blocked.add("葱");
        if (Boolean.FALSE.equals(preference.getEatCilantro())) blocked.add("香菜");
        if ("NONE".equals(preference.getSpiceLevel())) blocked.addAll(List.of("辣", "麻婆", "剁椒"));
        return blocked.stream().noneMatch(text::contains);
    }

    private int preferenceScore(Recipe recipe, UserFoodPreference preference) {
        if (preference == null) return 0;
        String text = searchableText(recipe);
        int score = terms(preference.getFavoriteTags()).stream()
                .mapToInt(tag -> matchesTag(text, tag) ? 6 : 0).sum();
        score += terms(preference.getFavoriteDishes()).stream()
                .mapToInt(dish -> text.contains(dish) ? 10 : 0).sum();
        score += terms(preference.getFavoriteCuisines()).stream()
                .mapToInt(cuisine -> matchesCuisine(text, cuisine) ? 8 : 0).sum();
        if ("HOT".equals(preference.getSpiceLevel()) && matchesTag(text, "香辣")) score += 4;
        if ("FITNESS".equals(healthGoal(preference)) && containsAny(text, "鸡", "牛", "鱼", "虾", "蛋", "豆腐", "豆", "瘦")) score += 8;
        if ("LEAN".equals(healthGoal(preference))) {
            if (containsAny(text, "蔬菜", "西兰花", "菌菇", "番茄", "清淡", "少油", "清蒸", "白灼")) score += 7;
            if (containsAny(text, "鸡", "鱼", "虾", "蛋", "豆腐")) score += 4;
            if (containsAny(text, "红烧", "回锅", "炸", "五花", "肥")) score -= 6;
        }
        return score;
    }

    private boolean matchesCuisine(String text, String cuisine) {
        return CUISINE_KEYWORDS.getOrDefault(cuisine, List.of()).stream().anyMatch(text::contains);
    }

    private boolean matchesTag(String text, String tag) {
        return switch (tag) {
            case "家常菜" -> containsAny(text, "家常", "下饭", "想家");
            case "汤粥" -> containsAny(text, "汤", "粥");
            case "面食" -> containsAny(text, "面", "粉", "河粉", "凉皮");
            case "米饭" -> containsAny(text, "饭", "盖饭", "煲仔");
            case "清淡" -> containsAny(text, "清淡", "清爽", "清甜", "温和", "少油");
            case "香辣" -> containsAny(text, "辣", "麻婆", "剁椒", "青椒");
            case "肉食" -> containsAny(text, "肉", "鸡", "牛", "羊", "排骨", "鸭");
            case "海鲜" -> containsAny(text, "虾", "鱼", "带鱼", "鲈鱼");
            default -> text.contains(tag);
        };
    }

    private String localRecommendationReason(Recipe recipe, String mood, UserFoodPreference preference,
                                             Map<Long, Integer> historyScore) {
        String healthReason = healthRecommendationReason(preference);
        if (!healthReason.isBlank()) return healthReason;
        if (historyScore.getOrDefault(recipe.getId(), 0) >= 5) {
            return "我记得你做过并喜欢这类菜，今天再吃一次也很合适。";
        }
        if (preference != null) {
            String text = searchableText(recipe);
            Optional<String> cuisine = terms(preference.getFavoriteCuisines()).stream()
                    .filter(item -> matchesCuisine(text, item)).findFirst();
            if (cuisine.isPresent()) {
                return "我记得你喜欢" + cuisine.get() + "，这道菜很值得今天尝尝。";
            }
        }
        if (preference != null && (terms(preference.getFavoriteTags()).stream()
                .anyMatch(tag -> matchesTag(searchableText(recipe), tag))
                || terms(preference.getFavoriteDishes()).stream().anyMatch(searchableText(recipe)::contains))) {
            return "我记得你的口味，也避开了你不吃的食材，这道菜更像你会喜欢的。";
        }
        return "根据你现在「" + mood + "」的心情，我想给你一顿好做又暖胃的饭。";
    }

    private String preferencePrompt(UserFoodPreference preference) {
        if (preference == null) return "暂无";
        return "喜欢" + safe(preference.getFavoriteTags())
                + "，偏爱菜系" + safe(preference.getFavoriteCuisines())
                + "，常吃的菜" + safe(preference.getFavoriteDishes())
                + "，不吃" + safe(preference.getAvoidIngredients())
                + "，过敏" + safe(preference.getAllergens())
                + "，葱" + answer(preference.getEatScallion())
                + "，香菜" + answer(preference.getEatCilantro())
                + "，辣度" + safe(preference.getSpiceLevel())
                + "，饮食目标" + healthGoalLabel(preference);
    }

    private String healthGoal(UserFoodPreference preference) {
        return preference == null || preference.getHealthGoal() == null ? "BALANCED" : preference.getHealthGoal();
    }

    private String healthGoalLabel(UserFoodPreference preference) {
        return switch (healthGoal(preference)) {
            case "FITNESS" -> "健身增肌";
            case "LEAN" -> "轻盈减脂";
            default -> "";
        };
    }

    private String healthRecommendationReason(UserFoodPreference preference) {
        return switch (healthGoal(preference)) {
            case "FITNESS" -> "考虑到你正在健身增肌，锅仔优先挑了有优质蛋白、适量主食和蔬菜的搭配。";
            case "LEAN" -> "考虑到你想轻盈减脂，锅仔优先挑了蔬菜、优质蛋白和少油做法的搭配。";
            default -> "";
        };
    }

    private String healthCompanionMessage(UserFoodPreference preference) {
        return switch (healthGoal(preference)) {
            case "FITNESS" -> "练完别只吃沙拉，今天给自己留够优质蛋白、主食和蔬菜。";
            case "LEAN" -> "轻盈吃饭不是挨饿，今天把蔬菜、优质蛋白和少油做法安排好。";
            default -> "";
        };
    }

    private void recordInteraction(String openid, Long recipeId, String action) {
        RecipeInteraction interaction = new RecipeInteraction();
        interaction.setOpenid(openid);
        interaction.setRecipeId(recipeId);
        interaction.setAction(action);
        interactions.save(interaction);
    }

    private String searchableText(Recipe recipe) {
        return safe(recipe.getName()) + safe(recipe.getDescription())
                + safe(recipe.getIngredients()) + safe(recipe.getMoodTags());
    }

    private List<String> terms(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split("[,，、;；\\s]+"))
                .map(String::trim).filter(s -> !s.isBlank()).distinct().toList();
    }

    private boolean containsAny(String text, String... words) {
        return Arrays.stream(words).anyMatch(text::contains);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String answer(Boolean value) {
        return value == null ? "未设置" : (value ? "可以" : "不要");
    }
}
