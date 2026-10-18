package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.repository.RecipeRepository;
import com.moodrecipe.backend.service.NutritionAdviceService;
import com.moodrecipe.backend.service.UsageQuotaService;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 菜谱营养建议（借鉴营养分析卡）：LLM 按食材估算，结果内存缓存。
 * 失败返回 code=404（前端不渲染卡片），绝不阻塞页面。
 */
@RestController
@RequestMapping("/api/nutrition")
public class NutritionController {

    private final NutritionAdviceService nutrition;
    private final RecipeRepository recipes;
    private final UsageQuotaService quotas;

    public NutritionController(NutritionAdviceService nutrition, RecipeRepository recipes, UsageQuotaService quotas) {
        this.nutrition = nutrition;
        this.recipes = recipes;
        this.quotas = quotas;
    }

    /** 按菜谱 id 分析 */
    @GetMapping("/recipe/{id}")
    public ApiResponse<Map<String, Object>> byRecipe(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @PathVariable Long id) {
        Optional<Recipe> recipe = recipes.findById(id);
        if (recipe.isEmpty()) return ApiResponse.error(404, "菜谱不存在");
        return analyze(openid, recipe.get().getName(), recipe.get().getIngredients());
    }

    /** 按菜名+食材分析（今日三餐每餐用） */
    public record AnalyzeRequest(String name, String ingredients) { }

    @PostMapping("/analyze")
    public ApiResponse<Map<String, Object>> analyze(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @RequestBody AnalyzeRequest request) {
        if (request == null) return ApiResponse.error(400, "缺少菜谱信息");
        return analyze(openid, request.name(), request.ingredients());
    }

    private ApiResponse<Map<String, Object>> analyze(String openid, String name, String ingredientsText) {
        List<String> ingredients = ingredientsText == null || ingredientsText.isBlank()
                ? List.of()
                // 食材串可能是 JSON 数组文本或顿号分隔文本，两种都兼容
                : normalize(ingredientsText);
        // 缓存键含用户输入（换菜名即绕过缓存），必须配额兜底防刷 LLM；分析失败返还额度
        try {
            quotas.consume(openid, UsageQuotaService.Feature.LLM_LIGHT);
        } catch (IllegalStateException e) {
            return ApiResponse.error(429, e.getMessage());
        }
        Optional<Map<String, Object>> result = nutrition.analyze(name, ingredients)
                .map(advice -> Map.<String, Object>of("advice", advice, "estimated", true));
        if (result.isEmpty()) quotas.release(openid, UsageQuotaService.Feature.LLM_LIGHT);
        return result.map(ApiResponse::<Map<String, Object>>ok)
                .orElseGet(() -> ApiResponse.error(404, "营养分析暂不可用"));
    }

    /** 食材串归一化为列表：兼容 JSON 数组与顿号/逗号分隔两种格式 */
    private List<String> normalize(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("[")) {
            try {
                String[] items = trimmed.replaceAll("[\\[\\]\"]", "").split(",");
                return Arrays.stream(items).map(String::trim).filter(s -> !s.isEmpty()).toList();
            } catch (RuntimeException ignored) { /* 落到分隔符解析 */ }
        }
        return Arrays.stream(trimmed.split("[,，、;；\\n]"))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
