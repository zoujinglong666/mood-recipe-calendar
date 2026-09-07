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
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/recipes")
public class RecipeController {

    private final RecipeRepository repository;
    private final RecipeInteractionRepository interactions;
    private final UserFoodPreferenceRepository preferences;
    private final AiRecipeService aiRecipeService;
    private final VirtualCommerceService virtualCommerceService;
    private final OperationalEventService operationalEvents;

    public RecipeController(RecipeRepository repository, RecipeInteractionRepository interactions, UserFoodPreferenceRepository preferences, AiRecipeService aiRecipeService, VirtualCommerceService virtualCommerceService, OperationalEventService operationalEvents) {
        this.repository = repository;
        this.interactions = interactions;
        this.preferences = preferences;
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

        UserFoodPreference preference = preferences.findByOpenid(openid).orElse(null);

        List<Recipe> candidates = repository.findByMoodTag(mood).stream()
                .filter(r -> !rejected.contains(r.getId()) && allowedByPreference(r, preference)).toList();
        if (candidates.isEmpty()) candidates = repository.findAll().stream()
                .filter(r -> !rejected.contains(r.getId()) && allowedByPreference(r, preference)).toList();
        List<Recipe> unseen = candidates.stream().filter(r -> !recentlyShown.contains(r.getId())).toList();
        if (!unseen.isEmpty()) candidates = unseen;
        Recipe recipe = candidates.stream()
                .max(Comparator.comparingInt(r -> score.getOrDefault(r.getId(), 0) + preferenceScore(r, preference))
                        .thenComparing(Recipe::getId, Comparator.reverseOrder()))
                .orElse(null);
        if (recipe != null) {
            recipe.setRecommendationReason(recommendationReason(recipe, mood, preference, score));
            recordInteraction(openid, recipe.getId(), "SHOWN");
        }
        return ApiResponse.ok(recipe);
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
        if ("HOT".equals(preference.getSpiceLevel()) && matchesTag(text, "香辣")) score += 4;
        return score;
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
        if (preference != null && terms(preference.getFavoriteTags()).stream()
                .anyMatch(tag -> matchesTag(searchableText(recipe), tag))) {
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
}
