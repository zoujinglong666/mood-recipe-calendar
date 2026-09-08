package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.entity.RecipeInteraction;
import com.moodrecipe.backend.entity.UserFoodPreference;
import com.moodrecipe.backend.repository.RecipeInteractionRepository;
import com.moodrecipe.backend.repository.RecipeRepository;
import com.moodrecipe.backend.repository.UserFoodPreferenceRepository;
import com.moodrecipe.backend.service.AiRecipeService;
import com.moodrecipe.backend.service.VirtualCommerceService;
import com.moodrecipe.backend.service.OperationalEventService;
import com.moodrecipe.backend.service.RecommendationJobService;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/recipes")
public class RecipeController {
    private static final Map<String, List<String>> CUISINE_KEYWORDS = Map.of(
            "川菜", List.of("麻婆", "宫保", "回锅", "鱼香", "水煮", "辣子", "口水鸡", "酸菜鱼", "担担"),
            "湘菜", List.of("剁椒", "小炒肉", "辣椒炒肉", "农家", "腊肉"),
            "粤菜", List.of("白切鸡", "叉烧", "煲仔", "河粉", "云吞", "豉汁", "白灼", "老火汤"),
            "江浙菜", List.of("东坡", "糖醋", "红烧", "清蒸", "油焖", "西湖", "葱油", "狮子头"),
            "东北菜", List.of("锅包肉", "地三鲜", "乱炖", "小鸡炖蘑菇", "酸菜", "酱骨"),
            "西北菜", List.of("羊肉", "牛肉面", "凉皮", "肉夹馍", "孜然", "臊子"),
            "云贵菜", List.of("酸汤", "过桥米线", "汽锅", "折耳根", "菌菇"),
            "日韩料理", List.of("泡菜", "寿司", "照烧", "石锅", "部队锅", "味噌", "咖喱"));

    private final RecipeRepository repository;
    private final RecipeInteractionRepository interactions;
    private final UserFoodPreferenceRepository preferences;
    private final AiRecipeService aiRecipeService;
    private final VirtualCommerceService virtualCommerceService;
    private final OperationalEventService operationalEvents;
    private final RecommendationJobService recommendationJobs;

    public RecipeController(RecipeRepository repository, RecipeInteractionRepository interactions, UserFoodPreferenceRepository preferences, AiRecipeService aiRecipeService, VirtualCommerceService virtualCommerceService, OperationalEventService operationalEvents, RecommendationJobService recommendationJobs) {
        this.repository = repository;
        this.interactions = interactions;
        this.preferences = preferences;
        this.aiRecipeService = aiRecipeService;
        this.virtualCommerceService = virtualCommerceService;
        this.operationalEvents = operationalEvents;
        this.recommendationJobs = recommendationJobs;
    }

    /** 全部菜谱 */
    @GetMapping
    public ApiResponse<List<Recipe>> list() {
        return ApiResponse.ok(repository.findAll());
    }

    /** 基础推荐：优先 AI 真实生成，失败回退到数据库菜谱（排除拒绝与最近看过，按反馈排序）。 */
    @GetMapping("/recommend")
    public ApiResponse<Recipe> recommend(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @RequestParam(defaultValue = "平静") String mood) {

        Recipe recipe = recommendInternal(openid, mood, null);
        return recipe == null
                ? ApiResponse.error(404, "没有找到符合当前忌口的菜，请到锅仔记忆里调整后再试")
                : ApiResponse.ok(recipe);
    }

    /** 创建异步今日推荐任务；同一用户同一心情的进行中任务会被复用。 */
    @PostMapping("/recommend-jobs")
    public ApiResponse<RecommendationJobService.JobView> createRecommendationJob(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @RequestBody RecommendJobRequest request) {
        String mood = request == null || request.mood() == null || request.mood().isBlank()
                ? "平静" : request.mood().trim();
        return ApiResponse.ok(recommendationJobs.start(openid, mood,
                progress -> recommendInternal(openid, mood, progress)));
    }

    /** 任务不存在和不属于当前用户统一返回 404，避免泄露他人任务。 */
    @GetMapping("/recommend-jobs/{jobId}")
    public ApiResponse<RecommendationJobService.JobView> recommendationJob(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @PathVariable String jobId) {
        return recommendationJobs.find(jobId, openid)
                .map(ApiResponse::ok)
                .orElseGet(() -> ApiResponse.error(404, "推荐任务已失效，请重新推荐"));
    }

    private Recipe recommendInternal(String openid, String mood, RecommendationJobService.Progress progress) {
        update(progress, RecommendationJobService.Stage.MEMORY, RecommendationJobService.StepStatus.RUNNING,
                "正在读取你告诉锅仔的口味");
        UserFoodPreference preference = preferences.findByOpenid(openid).orElse(null);
        update(progress, RecommendationJobService.Stage.MEMORY, RecommendationJobService.StepStatus.COMPLETED,
                preference == null ? "还没有口味记忆，这次先按心情推荐" : "已记起你的菜系、忌口和常吃习惯");

        // 优先 AI 真实推荐（结合用户口味偏好）
        try {
            String preferencePrompt = preferencePrompt(preference);
            Optional<Recipe> aiRecipe = aiRecipeService.recommend(mood, preferencePrompt,
                    event -> updateAiProgress(openid, progress, event));
            if (aiRecipe.isPresent()) {
                Recipe generated = aiRecipe.get();
                update(progress, RecommendationJobService.Stage.FINALIZE, RecommendationJobService.StepStatus.RUNNING,
                        "正在把菜名、食材和做法整理好");
                generated.setRecommendationReason("根据你现在「" + mood + "」的心情，锅仔特意为你想了这道菜。");
                update(progress, RecommendationJobService.Stage.FINALIZE, RecommendationJobService.StepStatus.COMPLETED,
                        "菜谱已经整理完成");
                return generated;
            }
        } catch (Exception ignored) {
            // AI 不可用时静默回退到数据库
        }

        // 回退：数据库菜谱推荐
        update(progress, RecommendationJobService.Stage.TEXT, RecommendationJobService.StepStatus.DEGRADED,
                "文本模型暂时不可用，改从锅仔菜谱库挑选");
        update(progress, RecommendationJobService.Stage.IMAGE, RecommendationJobService.StepStatus.DEGRADED,
                "本地菜谱使用已有封面");
        if (progress != null) progress.usedFallback();
        update(progress, RecommendationJobService.Stage.LOCAL_FALLBACK, RecommendationJobService.StepStatus.RUNNING,
                "正在按忌口、喜欢和最近看过的菜筛选");
        List<RecipeInteraction> history = interactions.findTop30ByOpenidOrderByCreatedAtDesc(openid);
        Set<Long> rejected = interactions.findByOpenidAndAction(openid, "DISLIKE").stream()
                .map(RecipeInteraction::getRecipeId).collect(Collectors.toSet());
        Set<Long> recentlyShown = history.stream().filter(i -> "SHOWN".equals(i.getAction())).limit(8)
                .map(RecipeInteraction::getRecipeId).collect(Collectors.toSet());
        Map<Long, Integer> score = new HashMap<>();
        history.forEach(i -> score.merge(i.getRecipeId(), switch (i.getAction()) {
            case "LIKE" -> 3;
            case "MADE" -> 5;
            default -> 0;
        }, Integer::sum));

        List<Recipe> candidates = repository.findByMoodTag(mood).stream()
                .filter(r -> !rejected.contains(r.getId()) && allowedByPreference(r, preference)).toList();
        if (candidates.isEmpty()) candidates = repository.findAll().stream()
                .filter(r -> !rejected.contains(r.getId()) && allowedByPreference(r, preference)).toList();
        if (candidates.isEmpty()) {
            update(progress, RecommendationJobService.Stage.LOCAL_FALLBACK, RecommendationJobService.StepStatus.FAILED,
                    "没有找到符合当前忌口的菜");
            operationalEvents.record("AI_RECOMMEND_FAILED", "ALERT", openid, null, "AI unavailable and no local candidate");
            return null;
        }
        List<Recipe> unseen = candidates.stream().filter(r -> !recentlyShown.contains(r.getId())).toList();
        if (!unseen.isEmpty()) candidates = unseen;
        Recipe recipe = candidates.stream()
                .max(Comparator.comparingInt((Recipe r) -> score.getOrDefault(r.getId(), 0) + preferenceScore(r, preference))
                        .thenComparing(Recipe::getId, Comparator.reverseOrder()))
                .orElse(null);
        if (recipe != null) {
            recipe.setRecommendationReason(recommendationReason(recipe, mood, preference, score));
            recordInteraction(openid, recipe.getId(), "SHOWN");
        }
        update(progress, RecommendationJobService.Stage.LOCAL_FALLBACK, RecommendationJobService.StepStatus.COMPLETED,
                "已从本地菜谱库找到合适的一道");
        update(progress, RecommendationJobService.Stage.FINALIZE, RecommendationJobService.StepStatus.RUNNING,
                "正在整理推荐理由和做法");
        update(progress, RecommendationJobService.Stage.FINALIZE, RecommendationJobService.StepStatus.COMPLETED,
                "菜谱已经整理完成");
        return recipe;
    }

    private void updateAiProgress(String openid, RecommendationJobService.Progress progress, AiRecipeService.GenerationEvent event) {
        if (event == AiRecipeService.GenerationEvent.TEXT_FAILED) {
            recordAiFailure(openid, "text generation unavailable");
        } else if (event == AiRecipeService.GenerationEvent.IMAGE_FAILED) {
            recordAiFailure(openid, "cover generation unavailable");
        }
        if (progress == null) return;
        switch (event) {
            case TEXT_STARTED -> progress.update(RecommendationJobService.Stage.TEXT, RecommendationJobService.StepStatus.RUNNING, "正在请文本模型生成今天的菜谱");
            case TEXT_COMPLETED -> progress.update(RecommendationJobService.Stage.TEXT, RecommendationJobService.StepStatus.COMPLETED, "菜名、食材和做法已经想好");
            case TEXT_FAILED -> progress.update(RecommendationJobService.Stage.TEXT, RecommendationJobService.StepStatus.DEGRADED, "文本模型暂时不可用，准备切换本地菜谱");
            case IMAGE_STARTED -> progress.update(RecommendationJobService.Stage.IMAGE, RecommendationJobService.StepStatus.RUNNING, "正在请图像模型制作菜品封面");
            case IMAGE_COMPLETED -> progress.update(RecommendationJobService.Stage.IMAGE, RecommendationJobService.StepStatus.COMPLETED, "菜品封面已经做好");
            case IMAGE_FAILED -> progress.update(RecommendationJobService.Stage.IMAGE, RecommendationJobService.StepStatus.DEGRADED, "封面暂时没做好，使用锅仔占位图");
        }
    }

    private void recordAiFailure(String openid, String detail) {
        try {
            operationalEvents.record("AI_RECOMMEND_FAILED", "WARN", openid, null, detail);
        } catch (Exception ignored) {
            // 告警入库失败不能阻断用户拿到本地推荐。
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

    private String recommendationReason(Recipe recipe, String mood, UserFoodPreference preference,
            Map<Long, Integer> historyScore) {
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
        return "根据你现在“" + mood + "”的心情，我想给你一顿好做又暖胃的饭。";
    }

    private String searchableText(Recipe recipe) {
        return safe(recipe.getName()) + safe(recipe.getDescription()) + safe(recipe.getIngredients()) + safe(recipe.getMoodTags());
    }

    private List<String> terms(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split("[,，、;；\\s]+"))
                .map(String::trim).filter(s -> !s.isBlank()).distinct().toList();
    }

    private boolean containsAny(String text, String... words) {
        return Arrays.stream(words).anyMatch(text::contains);
    }

    @PostMapping("/{id}/feedback")
    public ApiResponse<Void> feedback(@PathVariable Long id,
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @RequestBody RecipeFeedbackRequest request) {
        if (!repository.existsById(id)) return ApiResponse.error(404, "菜谱不存在");
        if (request == null || !Set.of("LIKE", "DISLIKE", "MADE").contains(request.action())) {
            return ApiResponse.error(400, "反馈类型无效");
        }
        recordInteraction(openid, id, request.action());
        return ApiResponse.ok(null);
    }

    private void recordInteraction(String openid, Long recipeId, String action) {
        RecipeInteraction interaction = new RecipeInteraction();
        interaction.setOpenid(openid);
        interaction.setRecipeId(recipeId);
        interaction.setAction(action);
        interactions.save(interaction);
    }

    /** 已购 AI 私人菜单权益的深度推荐入口。 */
    @PostMapping("/deep-recommend")
    public ApiResponse<Recipe> deepRecommend(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @RequestBody DeepRecommendRequest request) {
        var entitlement = virtualCommerceService.consumeEntitlement(openid, "AI_DEEP_RECOMMEND");
        if (entitlement.isEmpty()) {
            return ApiResponse.error(403, "请先解锁锅仔 AI 私人菜单权益");
        }

        UserFoodPreference memory = preferences.findByOpenid(openid).orElse(null);
        String preference = "已有食材：" + safe(request.ingredients())
                + "；期望时长：" + safe(request.maxMinutes())
                + "；本次口味与忌口：" + safe(request.preference())
                + "；长期口味记忆：" + preferencePrompt(memory);
        var recipe = aiRecipeService.recommend(request.mood(), preference);
        if (recipe.isEmpty()) {
            virtualCommerceService.restoreEntitlement(entitlement.get().getId());
            operationalEvents.record("AI_RECOMMEND_FAILED", "ALERT", openid, null, "provider unavailable or invalid response");
            return ApiResponse.error(503, "锅仔暂时没想好菜单，请稍后重试，本次权益未扣除");
        }
        return ApiResponse.ok(recipe.get());
    }

    private String safe(String value) {
        return value == null ? "" : value;
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
                + "，辣度" + safe(preference.getSpiceLevel());
    }

    private String answer(Boolean value) {
        return value == null ? "未设置" : (value ? "可以" : "不要");
    }

    /** 按心情列表 */
    @GetMapping("/by-mood")
    public ApiResponse<List<Recipe>> byMood(@RequestParam String mood) {
        return ApiResponse.ok(repository.findByMoodTag(mood));
    }

    /** 菜谱详情 */
    @GetMapping("/{id}")
    public ApiResponse<Recipe> detail(@PathVariable Long id) {
        return repository.findById(id)
            .map(ApiResponse::ok)
            .orElseGet(() -> ApiResponse.error(404, "菜谱不存在"));
    }

    public record DeepRecommendRequest(String mood, String ingredients, String maxMinutes, String preference) { }
    public record RecipeFeedbackRequest(String action) { }
    public record RecommendJobRequest(String mood) { }
}
