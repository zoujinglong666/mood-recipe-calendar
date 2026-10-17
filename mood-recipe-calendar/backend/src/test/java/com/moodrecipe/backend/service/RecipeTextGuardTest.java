package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.Recipe;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 乱码防线语义：中文被字符集事故吞成字面 '?' 的菜谱绝不外露。
 */
class RecipeTextGuardTest {

    private Recipe recipe(String name, String description) {
        Recipe r = new Recipe();
        r.setName(name);
        r.setDescription(description);
        return r;
    }

    @Test
    void normalChineseTextIsClean() {
        assertFalse(RecipeTextGuard.corrupted(null));
        assertFalse(RecipeTextGuard.corrupted(""));
        assertFalse(RecipeTextGuard.corrupted("番茄炒蛋"));
        assertFalse(RecipeTextGuard.corrupted("酸酸甜甜，15 分钟端上桌"));
    }

    @Test
    void questionMarkCorruptionIsFlagged() {
        assertTrue(RecipeTextGuard.corrupted("????"));
        assertTrue(RecipeTextGuard.corrupted("?????????????????"));
        // 部分损坏（个别字被吞）同样拦截，防止「鱼?豆腐」这类半乱码漏网
        assertTrue(RecipeTextGuard.corrupted("鱼?豆腐"));
        // U+FFFD 替换符也是乱码特征
        assertTrue(RecipeTextGuard.corrupted("番茄\ufffd炒蛋"));
    }

    @Test
    void brokenRecipeChecksNameAndDescription() {
        assertTrue(RecipeTextGuard.broken(null));
        assertTrue(RecipeTextGuard.broken(recipe("????", "正常的简介")));
        assertTrue(RecipeTextGuard.broken(recipe("番茄炒蛋", "?????????????????")));
        assertFalse(RecipeTextGuard.broken(recipe("番茄炒蛋", "酸酸甜甜，15 分钟端上桌")));
    }

    @Test
    void cleanFiltersOnlyBrokenRecipes() {
        Recipe good = recipe("番茄炒蛋", "家常快手菜");
        Recipe bad = recipe("????", "?????????");
        List<Recipe> result = RecipeTextGuard.clean(List.of(good, bad));
        assertEquals(1, result.size());
        assertEquals("番茄炒蛋", result.get(0).getName());
    }
}
