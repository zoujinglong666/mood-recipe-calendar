package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.service.RecipeFavoriteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/** 想吃清单：收藏 / 取消收藏 / 列表。挂 /api/** 下由 SessionAuthInterceptor 保护。 */
@RestController
@RequestMapping("/api/recipes/favorites")
public class RecipeFavoriteController {
    private final RecipeFavoriteService favorites;

    @Autowired
    public RecipeFavoriteController(RecipeFavoriteService favorites) {
        this.favorites = favorites;
    }

    @GetMapping
    public ApiResponse<?> list(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        return ApiResponse.ok(favorites.list(openid));
    }

    @PostMapping("/{recipeId}")
    public ApiResponse<?> add(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @PathVariable Long recipeId) {
        if (!favorites.add(openid, recipeId)) return ApiResponse.error(404, "菜谱不存在");
        return ApiResponse.ok();
    }

    @DeleteMapping("/{recipeId}")
    public ApiResponse<?> remove(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @PathVariable Long recipeId) {
        favorites.remove(openid, recipeId);
        return ApiResponse.ok();
    }
}
