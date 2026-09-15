package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.RecipeInteraction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecipeInteractionRepository extends JpaRepository<RecipeInteraction, Long> {
    List<RecipeInteraction> findTop30ByOpenidOrderByCreatedAtDesc(String openid);
    List<RecipeInteraction> findByOpenidAndAction(String openid, String action);
    boolean existsByOpenidAndRecipeIdAndAction(String openid, Long recipeId, String action);
    void deleteByOpenidAndRecipeIdAndActionIn(String openid, Long recipeId, List<String> actions);
    void deleteByOpenid(String openid);
}
