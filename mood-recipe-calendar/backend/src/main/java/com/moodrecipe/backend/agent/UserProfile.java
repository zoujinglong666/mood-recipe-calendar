package com.moodrecipe.backend.agent;

import java.util.List;
import java.util.Map;

/**
 * 可检索、可解释的用户档案。
 *
 * 与过去"平铺字符串摘要"的区别：每个字段都有来源，每条记忆都带证据，
 * 智能体可以说清楚"为什么今天调用这条记忆"。
 */
public record UserProfile(String openid,
                          Integer people,
                          Integer dishesPerDay,
                          List<Integer> cookingDays,
                          String spiceLevel,
                          Boolean hasElder,
                          Boolean hasChild,
                          String healthGoal,
                          String budget,
                          List<String> favoriteCuisines,
                          List<String> avoidIngredients,
                          List<String> allergens,
                          List<String> recentDishes,
                          List<String> lovedDishes,
                          List<String> rejectedDishes,
                          List<String> avoidDishes,
                          List<String> skipQuestions,
                          Map<String, Double> cuisineAffinity,
                          Integer maxCookingMinutes,
                          boolean preferSimple,
                          int recordDays,
                          List<MemoryItem> memory) {

    public static UserProfile empty(String openid) {
        return new UserProfile(openid, null, null, List.of(), null, null, null,
                null, null, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), Map.of(), null, false, 0, List.of());
    }

    /** 给提示词用的紧凑摘要，字段为空则省略，避免无意义的噪声。 */
    public String summary() {
        StringBuilder text = new StringBuilder();
        append(text, "忌口", avoidIngredients);
        append(text, "过敏", allergens);
        append(text, "偏爱菜系", favoriteCuisines);
        if (spiceLevel != null && !spiceLevel.isBlank()) text.append("辣度=").append(spiceLevel).append("；");
        if (hasElder != null && hasElder) text.append("有老人；");
        if (hasChild != null && hasChild) text.append("有小孩；");
        if (people != null) text.append("用餐人数=").append(people).append("；");
        if (dishesPerDay != null) text.append("每天菜数=").append(dishesPerDay).append("；");
        if (healthGoal != null && !healthGoal.isBlank()) text.append("健康目标=").append(healthGoal).append("；");
        if (budget != null && !budget.isBlank()) text.append("预算=").append(budget).append("；");
        if (maxCookingMinutes != null) text.append("单菜时长上限=").append(maxCookingMinutes).append("分钟；");
        if (preferSimple) text.append("偏好省事做法；");
        append(text, "最近做过", limit(recentDishes, 8));
        append(text, "明确喜欢的菜", limit(lovedDishes, 6));
        append(text, "已拒绝的菜", limit(rejectedDishes, 6));
        append(text, "反馈过不想做的菜", limit(avoidDishes, 6));
        if (!cuisineAffinity.isEmpty()) {
            text.append("菜系亲和=").append(cuisineAffinity.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(3)
                    .map(entry -> entry.getKey() + entry.getValue())
                    .reduce((a, b) -> a + "、" + b).orElse("")).append("；");
        }
        if (recordDays > 0) text.append("近30天记录").append(recordDays).append("天；");
        return text.isEmpty() ? "暂无可用档案" : text.toString();
    }

    private static void append(StringBuilder text, String label, List<String> values) {
        if (values == null || values.isEmpty()) return;
        text.append(label).append("=").append(String.join("、", limit(values, 8))).append("；");
    }

    private static List<String> limit(List<String> values, int max) {
        if (values == null) return List.of();
        return values.stream().limit(max).toList();
    }
}
