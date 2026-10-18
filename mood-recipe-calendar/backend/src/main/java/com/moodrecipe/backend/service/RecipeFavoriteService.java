package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.entity.RecipeFavorite;
import com.moodrecipe.backend.repository.RecipeFavoriteRepository;
import com.moodrecipe.backend.repository.RecipeRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RecipeFavoriteService {
    private final RecipeFavoriteRepository favorites;
    private final RecipeRepository recipes;

    public RecipeFavoriteService(RecipeFavoriteRepository favorites, RecipeRepository recipes) {
        this.favorites = favorites;
        this.recipes = recipes;
    }

    /** 想吃清单：按收藏时间倒序，附带菜谱摘要；菜谱被删的收藏自动过滤。 */
    public List<FavoriteView> list(String openid) {
        List<RecipeFavorite> favs = favorites.findTop200ByOpenidOrderByCreatedAtDesc(openid);
        if (favs.isEmpty()) return List.of();
        Map<Long, Recipe> recipeMap = recipes.findAllById(
                        favs.stream().map(RecipeFavorite::getRecipeId).toList())
                .stream()
                .collect(Collectors.toMap(Recipe::getId, Function.identity(), (a, b) -> a));
        return favs.stream()
                .map(f -> recipeMap.get(f.getRecipeId()))
                .filter(Objects::nonNull)
                .map(this::view)
                .toList();
    }

    /** 收藏；幂等（重复收藏返回 true）。菜谱不存在返回 false。 */
    public boolean add(String openid, Long recipeId) {
        if (!recipes.existsById(recipeId)) return false;
        if (favorites.existsByOpenidAndRecipeId(openid, recipeId)) return true;
        RecipeFavorite fav = new RecipeFavorite();
        fav.setOpenid(openid);
        fav.setRecipeId(recipeId);
        try {
            favorites.save(fav);
        }
        catch (DataIntegrityViolationException e) {
            // 并发双击/弱网重试下唯一键拦截第二笔：视为已收藏，不把冲突抛成 500
            return true;
        }
        return true;
    }

    /**
     * 取消收藏。派生删除（deleteByXxx）不会像 save() 那样自带事务，
     * 必须显式开启，否则 EntityManager 报「No EntityManager with actual transaction」500。
     * add() 刻意不加事务：save() 自带独立事务，让唯一键冲突在本方法内被捕获实现幂等。
     */
    @Transactional
    public void remove(String openid, Long recipeId) {
        favorites.deleteByOpenidAndRecipeId(openid, recipeId);
    }

    private FavoriteView view(Recipe r) {
        return new FavoriteView(r.getId(), r.getName(), r.getImage(), r.getCookingTime(),
                r.getDifficulty(), r.getDescription(), r.getSource());
    }

    public record FavoriteView(Long recipeId, String name, String image, Integer cookingTime,
                               String difficulty, String description, String source) {}
}
