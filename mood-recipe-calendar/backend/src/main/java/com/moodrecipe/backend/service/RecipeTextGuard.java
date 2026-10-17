package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.Recipe;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 菜谱文本乱码防线：拦截「中文被字符集转换吞成字面问号」的损坏数据，绝不外露给用户。
 *
 * 背景：生产库曾因以 latin1 客户端连接导入 SQL，中文字符在入库时被替换成 0x3F（'?'），
 * 数据层面已不可恢复（fix-mojibake.sql 只能救双编码型乱码，救不回 '?'）。
 * 中文菜谱的菜名/简介正常不应含 ASCII '?' 或替换符 U+FFFD，
 * 因此「含 '?' / U+FFFD」即视为乱码特征（含 emoji 转 '?' 的历史脏数据）。
 *
 * 使用位置：RecipePool（推荐/周计划/搜索的统一数据源）与 RecipeController 各出口。
 * 根治仍需重灌数据：mysql --default-character-set=utf8mb4 重新导入菜谱种子。
 */
public final class RecipeTextGuard {

    private RecipeTextGuard() {
    }

    /** 乱码特征：文本包含 '?' 或 U+FFFD（正常中文菜谱文案不会出现）。 */
    public static boolean corrupted(String text) {
        if (text == null) return false;
        return text.indexOf('?') >= 0 || text.indexOf('\uFFFD') >= 0;
    }

    /** 菜名或简介损坏即整道菜不可展示（用户会直接看到问号文案）。 */
    public static boolean broken(Recipe recipe) {
        return recipe == null
                || corrupted(recipe.getName())
                || corrupted(recipe.getDescription());
    }

    /** 过滤损坏菜谱，返回新的干净列表（入参不变）。 */
    public static List<Recipe> clean(List<Recipe> recipes) {
        if (recipes == null || recipes.isEmpty()) return recipes;
        return recipes.stream().filter(r -> !broken(r)).collect(Collectors.toList());
    }
}
