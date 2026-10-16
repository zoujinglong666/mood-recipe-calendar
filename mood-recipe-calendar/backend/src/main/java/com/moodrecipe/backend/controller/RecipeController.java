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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    private final com.moodrecipe.backend.service.RecipePosterService recipePosterService;

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
                            RecommendationExposureService exposures, UsageQuotaService quotas,
                            com.moodrecipe.backend.service.RecipePosterService recipePosterService) {
        this.repository = repository;
        this.interactions = interactions;
        this.guozaiAgent = guozaiAgent;
        this.virtualCommerceService = virtualCommerceService;
        this.operationalEvents = operationalEvents;
        this.recommendationJobs = recommendationJobs;
        this.exposures = exposures;
        this.quotas = quotas;
        this.recipePosterService = recipePosterService;
    }

    /**
     * 锅仔菜谱池（首页转盘/九宫格/翻牌抽样用）：AI 且图非占位/死链，按菜名去重。
     * 菜谱库浏览请用 /library（分页懒加载），不要走这个全量接口。
     */
    @GetMapping
    public ApiResponse<List<Recipe>> list() {
        return ApiResponse.ok(dedupeByName(repository.findAiWithImages()));
    }

    /**
     * 菜谱库分页列表（懒加载）：筛选全部下推 SQL，返回 {list,total,page,size}。
     * 同名菜用 MIN(id) 子查询去重（与 dedupeByName 口径一致），按 id 升序分页稳定。
     */
    @GetMapping("/library")
    public ApiResponse<Map<String, Object>> library(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String time,
            @RequestParam(required = false) String mood) {
        int pageNo = Math.max(0, page);
        int pageSize = Math.min(50, Math.max(1, size));
        var pageResult = repository.findAll(librarySpec(keyword, source, time, mood),
                PageRequest.of(pageNo, pageSize, Sort.by(Sort.Direction.ASC, "id")));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", pageResult.getContent());
        data.put("total", pageResult.getTotalElements());
        data.put("page", pageNo);
        data.put("size", pageSize);
        return ApiResponse.ok(data);
    }

    /** 菜谱库筛选条件：同名去重 + 关键词/来源/时长/心情，全部下推 SQL，分页与 total 按筛选后口径 */
    private org.springframework.data.jpa.domain.Specification<Recipe> librarySpec(
            String keyword, String source, String time, String mood) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            // 同名菜只保留最早入库（id 最小）的一条
            var sub = query.subquery(Long.class);
            var r2 = sub.from(Recipe.class);
            sub.select(cb.min(r2.<Long>get("id"))).groupBy(r2.get("name"));
            predicates.add(root.get("id").in(sub));
            if (keyword != null && !keyword.isBlank()) {
                String kw = "%" + keyword.trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("name"), kw),
                        cb.like(root.get("description"), kw),
                        cb.like(root.get("ingredients"), kw)));
            }
            if (source != null && !source.isBlank()) {
                predicates.add(cb.equal(root.get("source"), source));
            }
            if ("FAST".equals(time)) {
                // 与前端原口径一致：cookingTime 为空按 0 计，归入快手
                predicates.add(cb.or(root.get("cookingTime").isNull(),
                        cb.lessThanOrEqualTo(root.<Integer>get("cookingTime"), 30)));
            } else if ("SLOW".equals(time)) {
                predicates.add(cb.greaterThan(root.<Integer>get("cookingTime"), 30));
            }
            if (mood != null && !mood.isBlank()) {
                predicates.add(cb.like(root.get("moodTags"), "%" + mood.trim() + "%"));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    /** 菜谱库标签云：全表 mood_tags 聚合出的有效标签（去重取前 12），分页下前端拿不到全量 */
    @GetMapping("/mood-tags")
    public ApiResponse<List<String>> moodTags() {
        Set<String> tags = new LinkedHashSet<>();
        outer:
        for (String raw : repository.findAllMoodTags()) {
            for (String seg : raw.split("[,，、;；\\s]+")) {
                String s = seg.trim();
                // 只保留含中文/字母/数字的标签，过滤 emoji 入库变 ? 的乱码
                if (!s.isEmpty() && s.matches(".*[\\u4e00-\\u9fa5a-zA-Z0-9].*")) {
                    tags.add(s);
                    if (tags.size() >= 12) break outer;
                }
            }
        }
        return ApiResponse.ok(new ArrayList<>(tags));
    }

    /**
     * 同名菜去重：AI 推荐落库与种子菜谱存在同名多行，
     * 列表浏览与转盘/九宫格/翻牌抽样都不该出现两道同名菜。保留 id 最小（最早入库）的一条。
     */
    private List<Recipe> dedupeByName(List<Recipe> recipes) {
        Map<String, Recipe> picked = new LinkedHashMap<>();
        recipes.stream()
                .filter(r -> r.getName() != null)
                .sorted(Comparator.comparing(Recipe::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(r -> picked.putIfAbsent(r.getName(), r));
        return new ArrayList<>(picked.values());
    }

    /** 基础推荐：优先 AI 真实生成，失败回退到数据库菜谱（排除拒绝与最近看过，按反馈排序）。 */
    @GetMapping("/recommend")
    public ApiResponse<Recipe> recommend(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @RequestParam(defaultValue = "平静") String mood) {
        try { if (quotas != null) quotas.consume(openid, UsageQuotaService.Feature.HOME_RECOMMEND); }
        catch (IllegalStateException e) { return ApiResponse.error(403, e.getMessage()); }
        Recipe recipe;
        try { recipe = recommendInternal(openid, mood, null, null); }
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
            String mealType = request == null ? null : request.mealType();
        try {
            return ApiResponse.ok(recommendationJobs.start(openid, mood,
                    () -> { }, progress -> {
            try {
                Recipe recipe = recommendInternal(openid, mood, mealType, progress);
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
                // 漏斗埋点：推荐成功且首次被读取（chargeOnSuccess 幂等保证只记一次）
                try { operationalEvents.record("RECOMMEND_DONE", "INFO", openid, null,
                        job.get().recipe() == null ? "unknown" : job.get().recipe().getName()); } catch (RuntimeException ignored) { }
            }
            return ApiResponse.ok(job.get());
        } catch (IllegalStateException e) {
            return ApiResponse.error(403, e.getMessage());
        }
    }

    private Recipe recommendInternal(String openid, String mood, String mealType, RecommendationJobService.Progress progress) {
        return guozaiAgent.recommend(openid, mood, mealType, progress);
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

    /** 菜谱教程海报（借鉴竞品信息图教程卡）：Java2D 模板排版，文字不依赖生图模型；免费开放（裂变素材）。 */
    @PostMapping("/{id:\\d+}/poster")
    public ApiResponse<Map<String, String>> recipePoster(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @PathVariable Long id) {
        Recipe recipe = repository.findById(id).orElse(null);
        if (recipe == null) return ApiResponse.error(404, "菜谱不存在");
        try {
            String url = recipePosterService.generate(id, recipe.getName(), recipe.getDescription(),
                    recipe.getImage(), recipe.getIngredients(), recipe.getSteps(),
                    recipe.getCookingTime(), recipe.getDifficulty());
            return ApiResponse.ok(Map.of("url", url));
        } catch (IllegalStateException e) {
            return ApiResponse.error(500, e.getMessage());
        }
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
        return ApiResponse.ok(dedupeByName(repository.findAiWithImages().stream()
                .filter(r -> r.getMoodTags() != null && r.getMoodTags().contains(mood)).toList()));
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
    public record RecommendJobRequest(String mood, String mealType) { }
    public record RecipeHistoryItem(Long id, String name, String description, String image, String ingredients, String steps,
                                    Integer cookingTime, String difficulty, String source,
                                    java.time.LocalDateTime recommendedAt) { }
}
