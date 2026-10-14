package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.repository.RecipeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 推荐封面异步补拍：文生图（30-90s）+ COS 转存是今日推荐慢的主因，
 * 已移出请求路径——推荐在文本就绪后立即返回，本服务在后台补拍封面并回写菜谱表。
 *
 * 失败只记日志不打扰用户：除本服务外，今日卡/三餐卡的懒补图与早餐池启动补拍都会兜底。
 */
@Service
public class RecipeCoverBackfillService {

    private static final Logger log = LoggerFactory.getLogger(RecipeCoverBackfillService.class);

    private final AgnesRecipeImageService images;
    private final CosImageStorageService cosImageStorage;
    private final RecipeRepository recipes;

    public RecipeCoverBackfillService(AgnesRecipeImageService images, CosImageStorageService cosImageStorage,
                                      RecipeRepository recipes) {
        this.images = images;
        this.cosImageStorage = cosImageStorage;
        this.recipes = recipes;
    }

    /** 异步补拍封面：菜谱已落库（有 id）后调用；并发重复调用由 hasRealImage 幂等拦截。 */
    public void schedule(Long recipeId) {
        if (recipeId == null) return;
        Thread worker = new Thread(() -> {
            try {
                Recipe recipe = recipes.findById(recipeId).orElse(null);
                if (recipe == null || hasRealImage(recipe)) return;
                Optional<String> cover = images.generateCover(recipe)
                        .flatMap(url -> cosImageStorage.transferFromUrl(url, "recipes/"));
                cover.ifPresent(url -> {
                    recipe.setImage(url);
                    recipes.save(recipe);
                    log.info("推荐封面异步补拍完成 recipeId={} name={}", recipeId, recipe.getName());
                });
            } catch (Exception ex) {
                log.warn("推荐封面异步补拍失败 recipeId={}: {}", recipeId, ex.toString());
            }
        }, "recipe-cover-" + recipeId);
        worker.setDaemon(true);
        worker.start();
    }

    /** doubaocdn 短链已过期（403），与 static 插画一样视为无图。公开供管理端「配图」入口复用同一口径。 */
    public boolean hasRealImage(Recipe recipe) {
        String img = recipe.getImage();
        return img != null && !img.isBlank() && !img.startsWith("/static/") && !img.contains("doubaocdn.com");
    }
}
