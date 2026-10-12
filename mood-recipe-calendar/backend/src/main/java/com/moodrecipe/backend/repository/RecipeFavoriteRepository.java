package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.RecipeFavorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecipeFavoriteRepository extends JpaRepository<RecipeFavorite, Long> {

    /** 想吃清单：最近收藏的在前。 */
    List<RecipeFavorite> findTop200ByOpenidOrderByCreatedAtDesc(String openid);

    boolean existsByOpenidAndRecipeId(String openid, Long recipeId);

    void deleteByOpenidAndRecipeId(String openid, Long recipeId);

    /** 账号注销时清理。 */
    void deleteByOpenid(String openid);
}
