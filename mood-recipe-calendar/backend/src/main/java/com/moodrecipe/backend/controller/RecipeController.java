package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.repository.RecipeRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recipes")
public class RecipeController {

    private final RecipeRepository repository;

    public RecipeController(RecipeRepository repository) {
        this.repository = repository;
    }

    /** 全部菜谱 */
    @GetMapping
    public ApiResponse<List<Recipe>> list() {
        return ApiResponse.ok(repository.findAll());
    }

    /** 按心情推荐（随机一道） */
    @GetMapping("/recommend")
    public ApiResponse<Recipe> recommend(@RequestParam(defaultValue = "平静") String mood) {
        Recipe recipe = repository.findRandomByMood(mood);
        if (recipe == null) {
            // 没有匹配的心情，随机返回一道
            List<Recipe> all = repository.findAll();
            if (!all.isEmpty()) {
                recipe = all.get((int) (Math.random() * all.size()));
            }
        }
        return ApiResponse.ok(recipe);
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
}
