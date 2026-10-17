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
 * 视觉质检回环（坏图不出门）：生成 → COS 转存 → 视觉模型质检（生肉/混装/插画感），
 * 不合格则把质检意见回灌给生图模型重拍一次，复检仍不合格则放弃回写（宁缺毋滥）。
 * 质检未配置或调用失败时自动降级为「直通」，行为与接入前完全一致。
 *
 * 失败只记日志不打扰用户：除本服务外，今日卡/三餐卡的懒补图与早餐池启动补拍都会兜底。
 */
@Service
public class RecipeCoverBackfillService {

    private static final Logger log = LoggerFactory.getLogger(RecipeCoverBackfillService.class);

    private final AgnesRecipeImageService images;
    private final CosImageStorageService cosImageStorage;
    private final RecipeRepository recipes;
    private final ImageQualityGateService qualityGate;

    public RecipeCoverBackfillService(AgnesRecipeImageService images, CosImageStorageService cosImageStorage,
                                      RecipeRepository recipes, ImageQualityGateService qualityGate) {
        this.images = images;
        this.cosImageStorage = cosImageStorage;
        this.recipes = recipes;
        this.qualityGate = qualityGate;
    }

    /** 异步补拍封面：菜谱已落库（有 id）后调用；并发重复调用由 hasRealImage 幂等拦截。 */
    public void schedule(Long recipeId) {
        schedule(recipeId, false);
    }

    /**
     * force=false：仅补缺图（hasRealImage 幂等拦截）。
     * force=true：管理端「重新配图」——已有真实图也重拍并覆盖（旧图不保留），
     * 必须先驱逐 Agnes 生图的按名缓存，否则同进程会直接命中旧封面 URL，重拍变成空转。
     */
    public void schedule(Long recipeId, boolean force) {
        if (recipeId == null) return;
        if (force) images.evictCoverCache(recipes.findById(recipeId).map(Recipe::getName).orElse(null));
        Thread worker = new Thread(() -> {
            try {
                Recipe recipe = recipes.findById(recipeId).orElse(null);
                if (recipe == null || (!force && hasRealImage(recipe))) return;
                obtainCover(recipe)
                        .ifPresentOrElse(url -> {
                            recipe.setImage(url);
                            recipes.save(recipe);
                            log.info("推荐封面异步补拍完成 recipeId={} name={} force={}", recipeId, recipe.getName(), force);
                        }, () -> log.warn("封面未通过质检或生成失败，放弃回写 recipeId={} name={}", recipeId, recipe.getName()));
            } catch (Exception ex) {
                log.warn("推荐封面异步补拍失败 recipeId={}: {}", recipeId, ex.toString());
            }
        }, "recipe-cover-" + recipeId);
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * 生成 → 转存 → 质检 →（不合格）带意见重拍一次 → 复检。
     * 两次质检都不过则返回 empty（宁缺毋滥，坏图绝不回写）；质检降级时第一次生成即通过。
     * 包级可见便于单测。
     */
    Optional<String> obtainCover(Recipe recipe) {
        boolean combo = AgnesRecipeImageService.comboParts(recipe.getName()).size() >= 2;
        Optional<String> cosUrl = images.generateCover(recipe)
                .flatMap(url -> cosImageStorage.transferFromUrl(url, "recipes/"));
        if (cosUrl.isEmpty()) return Optional.empty();

        ImageQualityGateService.Verdict verdict = qualityGate.review(cosUrl.get(), recipe.getName(), combo);
        if (verdict.pass()) return cosUrl;

        // 第一版不合格：把质检意见回灌重拍一次（绕过按名缓存，否则命中旧图修改意见等于没加）
        log.info("封面质检不合格，带意见重拍 name={} reason={}", recipe.getName(), verdict.reason());
        Optional<String> second = images.generateCover(recipe, verdict.reason(), false)
                .flatMap(url -> cosImageStorage.transferFromUrl(url, "recipes/"));
        if (second.isEmpty()) return Optional.empty();

        ImageQualityGateService.Verdict recheck = qualityGate.review(second.get(), recipe.getName(), combo);
        if (recheck.pass()) return second;

        log.warn("封面复检仍不合格，放弃回写 name={} reason={}", recipe.getName(), recheck.reason());
        return Optional.empty();
    }

    /** doubaocdn 短链已过期（403），与 static 插画一样视为无图。公开供管理端「配图」入口复用同一口径。 */
    public boolean hasRealImage(Recipe recipe) {
        String img = recipe.getImage();
        return img != null && !img.isBlank() && !img.startsWith("/static/") && !img.contains("doubaocdn.com");
    }
}
