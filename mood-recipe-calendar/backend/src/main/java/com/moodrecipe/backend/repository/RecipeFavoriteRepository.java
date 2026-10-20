package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.RecipeFavorite;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecipeFavoriteRepository extends JpaRepository<RecipeFavorite, Long> {

    /** 想吃清单：最近收藏的在前。 */
    // [2026-10-20] 收藏用于加分，写极少，缓存可削减推荐路径查询量。
    @Cacheable(cacheNames = "userFavorites", key = "#openid")
    List<RecipeFavorite> findTop200ByOpenidOrderByCreatedAtDesc(String openid);

    boolean existsByOpenidAndRecipeId(String openid, Long recipeId);

    void deleteByOpenidAndRecipeId(String openid, Long recipeId);

    /** 账号注销时清理。 */
    void deleteByOpenid(String openid);

    /** 菜谱去重删除行时清理收藏引用。 */
    void deleteByRecipeId(Long recipeId);
}
