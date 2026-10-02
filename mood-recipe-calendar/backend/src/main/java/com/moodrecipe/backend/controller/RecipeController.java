package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.entity.UserEntitlement;
import com.moodrecipe.backend.entity.RecipeInteraction;
import com.moodrecipe.backend.repository.RecipeInteractionRepository;
import com.moodrecipe.backend.repository.RecipeRepository;
import com.moodrecipe.backend.service.GuozaiAgent;
import com.moodrecipe.backend.service.VirtualCommerceService;
import com.moodrecipe.backend.service.OperationalEventService;
import com.moodrecipe.backend.service.RecommendationJobService;
import com.moodrecipe.backend.service.RecommendationExposureService;
import com.moodrecipe.backend.service.UsageQuotaService;
import org.springframework.beans.factory.annotation.Autowired;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/recipes")
public class RecipeController {
    private final RecipeRepository repository;
    private final RecipeInteractionRepository interactions;
    private final GuozaiAgent guozaiAgent;
    private final VirtualCommerceService virtualCommerceService;
    private final OperationalEventService operationalEvents;
    private final RecommendationJobService recommendationJobs;
    private final RecommendationExposureService exposures;
    private final UsageQuotaService quotas;

    public RecipeController(RecipeRepository repository, RecipeInteractionRepository interactions,
                            GuozaiAgent guozaiAgent, VirtualCommerceService virtualCommerceService,
                            OperationalEventService operationalEvents, RecommendationJobService recommendationJobs,
                            RecommendationExposureService exposures) {
        this(repository, interactions, guozaiAgent, virtualCommerceService, operationalEvents, recommendationJobs, exposures, null);
    }

    @Autowired
    public RecipeController(RecipeRepository repository, RecipeInteractionRepository interactions,
                            GuozaiAgent guozaiAgent, VirtualCommerceService virtualCommerceService,
                            OperationalEventService operationalEvents, RecommendationJobService recommendationJobs,
                            RecommendationExposureService exposures, UsageQuotaService quotas) {
        this.repository = repository;
        this.interactions = interactions;
        this.guozaiAgent = guozaiAgent;
        this.virtualCommerceService = virtualCommerceService;
        this.operationalEvents = operationalEvents;
        this.recommendationJobs = recommendationJobs;
        this.exposures = exposures;
        this.quotas = quotas;
    }

    /** 全部菜谱 */
    @GetMapping
    public ApiResponse<List<Recipe>> list() {
        return ApiResponse.ok(repository.findAiWithImages());
    }

    /** 基础推荐：优先 AI 真实生成，失败回退到数据库菜谱（排除拒绝与最近看过，按反馈排序）。 */
    @GetMapping("/recommend")
    public ApiResponse<Recipe> recommend(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @RequestParam(defaultValue = "平静") String mood) {
        try { if (quotas != null) quotas.consume(openid, UsageQuotaService.Feature.HOME_RECOMMEND); }
        catch (IllegalStateException e) { return ApiResponse.error(403, e.getMessage()); }
        Recipe recipe;
        try { recipe = recommendInternal(openid, mood, null); }
        catch (RuntimeException e) { if (quotas != null) quotas.release(openid, UsageQuotaService.Feature.HOME_RECOMMEND); throw e; }
        if (recipe == null && quotas != null) quotas.release(openid, UsageQuotaService.Feature.HOME_RECOMMEND);
        return recipe == null
                ? ApiResponse.error(404, "没有找到符合当前忌口的菜，请到锅仔记忆里调整后再试")
                : ApiResponse.ok(recipe);
    }

    /** 创建异步今日推荐任务；同一用户的进行中任务会被复用。 */
    @PostMapping("/recommend-jobs")
    public ApiResponse<RecommendationJobService.JobView> createRecommendationJob(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @RequestBody RecommendJobRequest request) {
            String mood = request == null || request.mood() == null || request.mood().isBlank()
                ? "平静" : request.mood().trim();
        try {
            return ApiResponse.ok(recommendationJobs.start(openid, mood,
                    () -> { }, progress -> {
            try {
                Recipe recipe = recommendInternal(openid, mood, progress);
                if (recipe == null && quotas != null) quotas.release(openid, UsageQuotaService.Feature.HOME_RECOMMEND);
                return recipe;
            } catch (RuntimeException e) {
                if (quotas != null) quotas.release(openid, UsageQuotaService.Feature.HOME_RECOMMEND);
                throw e;
            }
        }));
        } catch (IllegalStateException e) { return ApiResponse.error(403, e.getMessage()); }
    }

    @GetMapping("/quota")
    public ApiResponse<UsageQuotaService.View> quota(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        return quotas == null ? ApiResponse.error(503, "配额服务不可用") : ApiResponse.ok(quotas.view(openid, UsageQuotaService.Feature.HOME_RECOMMEND));
    }

    @GetMapping("/history")
    public ApiResponse<List<RecipeHistoryItem>> history(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        List<RecipeInteraction> recent = interactions.findTop30ByOpenidAndActionOrderByCreatedAtDesc(openid, "SHOWN");
        // 批量取菜谱，避免循环 findById 触发 N+1
        List<Long> ids = recent.stream().map(RecipeInteraction::getRecipeId).filter(Objects::nonNull).toList();
        Map<Long, Recipe> byId = repository.findAllById(ids).stream()
                .collect(Collectors.toMap(Recipe::getId, r -> r));
        List<RecipeHistoryItem> items = recent.stream()
                .map(item -> {
                    Recipe recipe = byId.get(item.getRecipeId());
                    if (recipe == null) return null;
                    return new RecipeHistoryItem(recipe.getId(), recipe.getName(), recipe.getDescription(),
                            recipe.getImage(), recipe.getIngredients(), recipe.getSteps(),
                            recipe.getCookingTime(), recipe.getDifficulty(), recipe.getSource(), item.getCreatedAt());
                })
                .filter(Objects::nonNull)
                .toList();
        return ApiResponse.ok(items);
    }

    /** 任务不存在和不属于当前用户统一返回 404，避免泄露他人任务。 */
    @GetMapping("/recommend-jobs/{jobId}")
    public ApiResponse<RecommendationJobService.JobView> recommendationJob(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @PathVariable String jobId) {
        var job = recommendationJobs.find(jobId, openid);
        if (job.isEmpty()) return ApiResponse.error(404, "推荐任务已失效，请重新推荐");
        try {
            if (quotas != null && job.get().status() == RecommendationJobService.JobStatus.SUCCEEDED) {
                recommendationJobs.chargeOnSuccess(jobId, openid,
                        () -> quotas.consume(openid, UsageQuotaService.Feature.HOME_RECOMMEND));
            }
            return ApiResponse.ok(job.get());
        } catch (IllegalStateException e) {
            return ApiResponse.error(403, e.getMessage());
        }
    }

    private Recipe recommendInternal(String openid, String mood, RecommendationJobService.Progress progress) {
        return guozaiAgent.recommend(openid, mood, progress);
    }

    @PostMapping("/{id}/feedback")
    @Transactional
    public ApiResponse<FeedbackState> feedback(@PathVariable Long id,
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @RequestBody RecipeFeedbackRequest request) {
        if (!repository.existsById(id)) return ApiResponse.error(404, "菜谱不存在");
        if (request == null || !Set.of("LIKE", "DISLIKE").contains(request.action())) {
            return ApiResponse.error(400, "反馈类型无效");
        }
        interactions.deleteByOpenidAndRecipeIdAndActionIn(openid, id,
                List.of("LIKE".equals(request.action()) ? "DISLIKE" : "LIKE"));
        if (!interactions.existsByOpenidAndRecipeIdAndAction(openid, id, request.action())) {
            recordInteraction(openid, id, request.action());
        }
        return ApiResponse.ok(feedbackState(openid, id));
    }

    @GetMapping("/{id}/feedback")
    public ApiResponse<FeedbackState> feedbackState(@PathVariable Long id,
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        if (!repository.existsById(id)) return ApiResponse.error(404, "菜谱不存在");
        return ApiResponse.ok(feedbackState(openid, id));
    }

    @PostMapping("/exposures/{exposureId}/feedback")
    public ApiResponse<RecommendationExposureService.FeedbackState> exposureFeedback(@PathVariable String exposureId,
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @RequestBody RecipeFeedbackRequest request) {
        if (request == null || !Set.of("LIKE", "DISLIKE").contains(request.action())) {
            return ApiResponse.error(400, "反馈类型无效");
        }
        var state = exposures.feedback(openid, exposureId, request.action());
        return state != null ? ApiResponse.ok(state) : ApiResponse.error(404, "推荐记录不存在或已失效");
    }

    @GetMapping("/exposures/{exposureId}/feedback")
    public ApiResponse<RecommendationExposureService.FeedbackState> exposureFeedbackState(
            @PathVariable String exposureId,
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        var state = exposures.state(openid, exposureId);
        return state != null ? ApiResponse.ok(state) : ApiResponse.error(404, "推荐记录不存在或已失效");
    }

    private void recordInteraction(String openid, Long recipeId, String action) {
        RecipeInteraction interaction = new RecipeInteraction();
        interaction.setOpenid(openid);
        interaction.setRecipeId(recipeId);
        interaction.setAction(action);
        interactions.save(interaction);
    }

    private FeedbackState feedbackState(String openid, Long recipeId) {
        return new FeedbackState(
                interactions.existsByOpenidAndRecipeIdAndAction(openid, recipeId, "LIKE"),
                interactions.existsByOpenidAndRecipeIdAndAction(openid, recipeId, "DISLIKE"),
                interactions.existsByOpenidAndRecipeIdAndAction(openid, recipeId, "MADE"));
    }

    /** 已购 AI 私人菜单权益的深度推荐入口。 */
    @PostMapping("/deep-recommend")
    public ApiResponse<Recipe> deepRecommend(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @RequestBody DeepRecommendRequest request) {
        if (!validDeepRequest(request)) {
            return ApiResponse.error(400, "请填写有效的心情与定制条件");
        }
        boolean member = virtualCommerceService.isActiveMember(openid);
        var entitlement = member ? Optional.<UserEntitlement>empty()
                : virtualCommerceService.consumeEntitlement(openid, "AI_DEEP_RECOMMEND");
        if (!member && entitlement.isEmpty()) {
            return ApiResponse.error(403, "请先解锁锅仔私人菜单权益");
        }
        boolean success = false;
        try {
            var recipe = guozaiAgent.deepRecommend(openid, request.mood().trim(), request.ingredients(),
                    request.maxMinutes(), request.preference());
            if (recipe.isEmpty()) {
                recordDeepFailure(openid);
                return ApiResponse.error(503, "锅仔暂时没想好菜单，请稍后重试，本次权益未扣除");
            }
            success = true;
            return ApiResponse.ok(recipe.get());
        } catch (RuntimeException ignored) {
            recordDeepFailure(openid);
            return ApiResponse.error(503, "锅仔暂时没想好菜单，请稍后重试，本次权益未扣除");
        } finally {
            if (!success && entitlement.isPresent()) {
                try {
                    virtualCommerceService.restoreEntitlement(entitlement.get().getId());
                } catch (RuntimeException ignored) {
                    try {
                        operationalEvents.record("ENTITLEMENT_RESTORE_FAILED", "ALERT", openid, null,
                                "deep recommendation compensation failed");
                    } catch (RuntimeException ignoredAgain) {
                        // 告警失败不能覆盖原始业务响应。
                    }
                }
            }
        }
    }

    private boolean validDeepRequest(DeepRecommendRequest request) {
        return request != null && request.mood() != null && !request.mood().isBlank()
                && request.mood().length() <= 20
                && length(request.ingredients()) <= 500
                && length(request.preference()) <= 500
                && length(request.maxMinutes()) <= 30;
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }

    private void recordDeepFailure(String openid) {
        try {
            operationalEvents.record("AI_RECOMMEND_FAILED", "ALERT", openid, null,
                    "provider unavailable or invalid response");
        } catch (RuntimeException ignored) {
            // 告警失败不能覆盖权益补偿与业务响应。
        }
    }

    /** 按心情列表 */
    @GetMapping("/by-mood")
    public ApiResponse<List<Recipe>> byMood(@RequestParam String mood) {
        return ApiResponse.ok(repository.findAiWithImages().stream()
                .filter(r -> r.getMoodTags() != null && r.getMoodTags().contains(mood)).toList());
    }

    /** 菜谱详情 */
    /** 仅匹配数字 ID，避免 /quota 被旧版或不同 Spring 路由策略误判为 Long。 */
    @GetMapping("/{id:\\d+}")
    public ApiResponse<Recipe> detail(@PathVariable Long id) {
        return repository.findById(id)
            .map(ApiResponse::ok)
            .orElseGet(() -> ApiResponse.error(404, "菜谱不存在"));
    }

    public record DeepRecommendRequest(String mood, String ingredients, String maxMinutes, String preference) { }
    public record RecipeFeedbackRequest(String action) { }
    public record FeedbackState(boolean liked, boolean disliked, boolean made) { }
    public record RecommendJobRequest(String mood) { }
    public record RecipeHistoryItem(Long id, String name, String description, String image, String ingredients, String steps,
                                    Integer cookingTime, String difficulty, String source,
                                    java.time.LocalDateTime recommendedAt) { }
}
