package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.repository.RecipeRepository;
import com.moodrecipe.backend.service.AiRecipeService;
import com.moodrecipe.backend.service.VirtualCommerceService;
import com.moodrecipe.backend.service.OperationalEventService;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recipes")
public class RecipeController {

    private final RecipeRepository repository;
    private final AiRecipeService aiRecipeService;
    private final VirtualCommerceService virtualCommerceService;
    private final OperationalEventService operationalEvents;

    public RecipeController(RecipeRepository repository, AiRecipeService aiRecipeService, VirtualCommerceService virtualCommerceService, OperationalEventService operationalEvents) {
        this.repository = repository;
        this.aiRecipeService = aiRecipeService;
        this.virtualCommerceService = virtualCommerceService;
        this.operationalEvents = operationalEvents;
    }

    /** 全部菜谱 */
    @GetMapping
    public ApiResponse<List<Recipe>> list() {
        return ApiResponse.ok(repository.findAll());
    }

    /** 按心情优先使用 AI 生成；AI 未配置或失败时回退到菜谱库。 */
    @GetMapping("/recommend")
    public ApiResponse<Recipe> recommend(@RequestParam(defaultValue = "平静") String mood) {
        Recipe recipe = aiRecipeService.recommend(mood).orElse(null);
        if (recipe != null) return ApiResponse.ok(recipe);

        recipe = repository.findRandomByMood(mood);
        if (recipe == null) {
            // 没有匹配的心情，随机返回一道
            List<Recipe> all = repository.findAll();
            if (!all.isEmpty()) {
                recipe = all.get((int) (Math.random() * all.size()));
            }
        }
        return ApiResponse.ok(recipe);
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
}
