package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.RecipeInteraction;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecipeInteractionRepository extends JpaRepository<RecipeInteraction, Long> {
    // [2026-10-20] 反馈/历史用于打分，写后短时陈旧不影响推荐质量，缓存避免每次 pick 重复查。
    @Cacheable(cacheNames = "userInteractions", key = "#openid")
    List<RecipeInteraction> findTop30ByOpenidOrderByCreatedAtDesc(String openid);
    // [2026-10-20] 供 FeedbackLearningService 聚合行为信号（只看表达偏好的互动，SHOWN 仅用于曝光/指标）。
    List<RecipeInteraction> findByOpenidAndActionInOrderByCreatedAtDesc(String openid, List<String> actions);
    // [2026-10-20] 指标与画像聚合用的计数查询，避免为统计而全表加载。
    long countByOpenidAndAction(String openid, String action);
    long countByAction(String action);
    List<RecipeInteraction> findByOpenidAndAction(String openid, String action);
    List<RecipeInteraction> findTop30ByOpenidAndActionOrderByCreatedAtDesc(String openid, String action);
    boolean existsByOpenidAndRecipeIdAndAction(String openid, Long recipeId, String action);
    void deleteByOpenidAndRecipeIdAndActionIn(String openid, Long recipeId, List<String> actions);
    void deleteByOpenid(String openid);

    /** 菜谱去重删除行时清理互动引用（曝光/点赞/做过）。 */
    void deleteByRecipeId(Long recipeId);
}
