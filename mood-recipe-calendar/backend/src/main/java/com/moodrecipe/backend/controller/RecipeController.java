package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.entity.RecipeInteraction;
import com.moodrecipe.backend.repository.RecipeInteractionRepository;
import com.moodrecipe.backend.repository.RecipeRepository;
import com.moodrecipe.backend.service.AiRecipeService;
import com.moodrecipe.backend.service.VirtualCommerceService;
import com.moodrecipe.backend.service.OperationalEventService;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/recipes")
public class RecipeController {

    private final RecipeRepository repository;
    private final RecipeInteractionRepository interactions;
    private final AiRecipeService aiRecipeService;
    private final VirtualCommerceService virtualCommerceService;
    private final OperationalEventService operationalEvents;

    public RecipeController(RecipeRepository repository, RecipeInteractionRepository interactions, AiRecipeService aiRecipeService, VirtualCommerceService virtualCommerceService, OperationalEventService operationalEvents) {
        this.repository = repository;
        this.interactions = interactions;
        this.aiRecipeService = aiRecipeService;
        this.virtualCommerceService = virtualCommerceService;
        this.operationalEvents = operationalEvents;
    }

    /** 全部菜谱 */
    @GetMapping
    public ApiResponse<List<Recipe>> list() {
        return ApiResponse.ok(repository.findAll());
    }

    /** 基础推荐：排除拒绝与最近看过的菜，并根据真实反馈排序。 */
    @GetMapping("/recommend")
    public ApiResponse<Recipe> recommend(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @RequestParam(defaultValue = "平静") String mood) {
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
                .filter(r -> !rejected.contains(r.getId())).toList();
        if (candidates.isEmpty()) candidates = repository.findAll().stream()
                .filter(r -> !rejected.contains(r.getId())).toList();
        List<Recipe> unseen = candidates.stream().filter(r -> !recentlyShown.contains(r.getId())).toList();
        if (!unseen.isEmpty()) candidates = unseen;
        Recipe recipe = candidates.stream()
                .max(Comparator.comparingInt(r -> score.getOrDefault(r.getId(), 0))
                        .thenComparing(Recipe::getId, Comparator.reverseOrder()))
                .orElse(null);
        if (recipe != null) recordInteraction(openid, recipe.getId(), "SHOWN");
        return ApiResponse.ok(recipe);
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

        String preference = "已有食材：" + safe(request.ingredients())
                + "；期望时长：" + safe(request.maxMinutes())
                + "；口味与忌口：" + safe(request.preference());
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
}
