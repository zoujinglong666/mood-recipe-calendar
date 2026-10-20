package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.repository.RecipeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 菜谱池缓存：菜谱表含大 TEXT（ingredients/steps），被多个规划服务在请求路径里反复 findAll / findAiWithImages
 * （每日菜单、周计划、锅仔推荐、搜索工具每次都会全表物化，是请求慢的根因之一）。
 * 这里以单例 + 60s TTL 缓存整表，把每个请求内的多次全表扫描收敛为进程级共享的少量加载。
 * 菜谱写入频率低，60s 陈旧对推荐/规划结果无实质影响；加载失败时保留旧缓存，避免请求因缓存异常失败。
 *
 * 刷新策略（stale-while-revalidate）：已有旧缓存但 TTL 过期时，请求立即用旧数据返回，
 * 后台异步刷新——否则 TTL 过期瞬间撞上的请求（如换一道）会全部堆积在 synchronized 上，
 * 等两次全表加载完成，表现就是「骨架屏转一分钟然后换菜超时」。只有冷启动（无任何缓存）才同步加载。
 */
@Component
public class RecipePool {

    private static final Logger log = LoggerFactory.getLogger(RecipePool.class);
    private static final long TTL_MS = 60_000;
    /** 后台刷新失败后的重试冷却：避免旧缓存期间每个请求都触发一次注定失败的全表查询。 */
    private static final long RELOAD_RETRY_COOLDOWN_MS = 15_000;

    private final RecipeRepository recipes;
    private volatile List<Recipe> all;
    private volatile List<Recipe> aiWithImages;
    private volatile long loadedAt = 0;
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    public RecipePool(RecipeRepository recipes) {
        this.recipes = recipes;
    }

    public List<Recipe> all() {
        ensureLoaded();
        return all != null ? all : List.of();
    }

    public List<Recipe> aiWithImages() {
        ensureLoaded();
        return aiWithImages != null ? aiWithImages : List.of();
    }

    private boolean needReload() {
        return all == null || System.currentTimeMillis() - loadedAt > TTL_MS;
    }

    private void ensureLoaded() {
        if (!needReload()) return;
        // 冷启动（从未加载过）：同步加载，第一次请求必须拿到数据
        if (all == null) {
            synchronized (this) {
                if (all == null) reload();
            }
            return;
        }
        // 热缓存过期：立即返回旧数据，后台单飞刷新，请求路径零阻塞
        if (refreshing.compareAndSet(false, true)) {
            CompletableFuture.runAsync(() -> {
                try {
                    reload();
                } finally {
                    refreshing.set(false);
                }
            });
        }
    }

    private synchronized void reload() {
        if (all != null && System.currentTimeMillis() - loadedAt <= TTL_MS) return;
        try {
            List<Recipe> loaded = RecipeTextGuard.clean(recipes.findAll());
            all = loaded == null ? List.of() : List.copyOf(loaded);
            // 直接复用 DB 层 findAiWithImages 的过滤（source=AI 且有图、非 /static/ 占位），
            // 与改造前各服务调用 findAiWithImages 的语义一致，也避免重复全表后在内存再过滤。
            List<Recipe> ai = RecipeTextGuard.clean(recipes.findAiWithImages());
            aiWithImages = ai == null ? List.of() : List.copyOf(ai);
            loadedAt = System.currentTimeMillis();
        } catch (Exception e) {
            log.warn("菜谱池加载失败（保留旧缓存）: {}", e.getMessage());
            // 首加载失败用空集合兜底并标记时间，避免无限重试打爆数据库；已有旧缓存则继续复用
            if (all == null) {
                all = List.of();
                aiWithImages = List.of();
                loadedAt = System.currentTimeMillis();
            } else {
                // 旧缓存场景：把 loadedAt 拨回「冷却期内」，防止每个请求都触发一次后台重试
                loadedAt = System.currentTimeMillis() - (TTL_MS - RELOAD_RETRY_COOLDOWN_MS);
            }
        }
    }
}
