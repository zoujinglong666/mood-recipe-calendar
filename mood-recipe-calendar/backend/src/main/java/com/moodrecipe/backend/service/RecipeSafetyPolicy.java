package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.Recipe;

import java.util.List;

/** AI 菜谱入库前的最低安全边界；命中时拒绝结果并触发重试或降级。 */
final class RecipeSafetyPolicy {
    private static final List<String> HIGH_RISK = List.of(
            "河豚", "野生菌", "生腌", "醉虾", "醉蟹", "刺身", "生食", "半熟鸡蛋", "溏心蛋");
    private static final List<String> MEDICAL_CLAIMS = List.of(
            "治疗", "治愈", "预防癌", "抗癌", "降血压", "降血糖", "替代药物", "停药");

    private RecipeSafetyPolicy() { }

    static boolean isSafe(Recipe recipe) {
        String text = String.join(" ", value(recipe.getName()), value(recipe.getDescription()),
                value(recipe.getIngredients()), value(recipe.getSteps()));
        return HIGH_RISK.stream().noneMatch(text::contains)
                && MEDICAL_CLAIMS.stream().noneMatch(text::contains);
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
