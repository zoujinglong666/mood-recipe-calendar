package com.moodrecipe.backend.agent;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 菜单质量校验器：把"这桌菜到底合不合格"变成可计算的分数和具体条目。
 *
 * 覆盖：忌口/过敏（硬）、辣度冲突（硬）、重复与近期重复（硬）、
 * 老人小孩适配（硬）、营养结构与荤素比例（软）、食材复用（软）、
 * 时长与难度均衡（软）、预算（软）。
 *
 * 只有硬问题会触发重新规划，软问题用于评分与提示。
 */
public final class MenuQualityScorer {

    public enum Severity { HARD, SOFT }

    public record Issue(String code, Severity severity, String message, String dish) {}

    public record Stats(int totalDays, int vegetableDays, int proteinDays, double meatVegRatio,
                        double reuseRate, double avgMinutes, int heavyDishes) {}

    public record MenuQuality(int score, List<Issue> issues, Stats stats) {
        public List<Issue> hard() {
            return issues.stream().filter(issue -> issue.severity() == Severity.HARD).toList();
        }

        public List<Issue> soft() {
            return issues.stream().filter(issue -> issue.severity() == Severity.SOFT).toList();
        }

        public boolean hasHard() {
            return !hard().isEmpty();
        }

        /** 供模型重新规划时参考的简短问题描述。 */
        public String violationBrief() {
            return issues.stream().limit(12)
                    .map(issue -> "[" + (issue.severity() == Severity.HARD ? "必须修正" : "建议改进") + "] "
                            + issue.code() + "：" + issue.message() + (issue.dish().isBlank() ? "" : "（" + issue.dish() + "）"))
                    .collect(Collectors.joining("；"));
        }
    }

    public record DishInput(String name, String role, List<String> ingredients, Integer cookingTime, String difficulty) {
        public String text() {
            return (name == null ? "" : name) + " " + String.join(" ", ingredients == null ? List.<String>of() : ingredients);
        }
    }

    public record DayInput(int weekday, List<DishInput> dishes) {}

    public record Constraints(Set<String> avoid,
                              Set<String> allergens,
                              String spiceLevel,
                              boolean elder,
                              boolean child,
                              String healthGoal,
                              String budget,
                              Set<String> recentDishes,
                              Set<String> avoidDishes,
                              Integer maxCookingMinutes,
                              boolean preferSimple) {

        public static Constraints empty() {
            return new Constraints(Set.of(), Set.of(), null, false, false, null, null, Set.of(), Set.of(), null, false);
        }

        /** 从可解释的用户档案推导校验约束，让"为什么不合格"能追溯到具体记忆。 */
        public static Constraints from(UserProfile profile) {
            if (profile == null) return empty();
            return new Constraints(
                    new LinkedHashSet<>(profile.avoidIngredients()),
                    new LinkedHashSet<>(profile.allergens()),
                    profile.spiceLevel(),
                    Boolean.TRUE.equals(profile.hasElder()),
                    Boolean.TRUE.equals(profile.hasChild()),
                    profile.healthGoal(),
                    profile.budget(),
                    new LinkedHashSet<>(profile.recentDishes()),
                    new LinkedHashSet<>(profile.avoidDishes()),
                    profile.maxCookingMinutes(),
                    profile.preferSimple());
        }
    }

    private static final List<String> PROTEIN_WORDS = List.of("鸡", "牛", "猪", "羊", "鱼", "虾", "蛋", "豆腐", "肉", "鸭", "排骨", "贝", "蛤", "瘦肉");
    private static final List<String> VEGETABLE_WORDS = List.of("青菜", "白菜", "西兰花", "菠菜", "生菜", "油麦", "空心菜", "菜心", "黄瓜",
            "茄子", "豆角", "土豆", "萝卜", "菌菇", "蘑菇", "木耳", "番茄", "西红柿", "冬瓜", "南瓜", "莴笋", "芦笋", "芹菜", "韭菜",
            "蒜苗", "娃娃菜", "芥蓝", "苋菜", "丝瓜", "苦瓜", "西葫芦", "秋葵", "山药", "莲藕", "豌豆", "玉米", "青椒", "彩椒");
    private static final List<String> SPICY_WORDS = List.of("辣", "麻婆", "剁椒", "水煮", "麻辣", "香辣", "干锅", "泡椒", "藤椒");
    private static final List<String> EXPENSIVE_WORDS = List.of("牛排", "羊排", "三文鱼", "鲍鱼", "海参", "大虾", "龙虾", "帝王蟹", "和牛");
    private static final List<String> ELDER_RISK_WORDS = List.of("炸", "酥脆", "脆", "干锅", "烧烤", "坚果", "牛筋", "脆骨");
    private static final List<String> CHILD_RISK_WORDS = List.of("酒", "醉", "芥末", "咖喱", "麻辣");

    public MenuQuality evaluate(List<DayInput> days, Constraints constraints) {
        List<Issue> issues = new ArrayList<>();
        if (days == null || days.isEmpty()) {
            issues.add(new Issue("EMPTY_MENU", Severity.HARD, "菜单为空", ""));
            return new MenuQuality(0, issues, new Stats(0, 0, 0, 0d, 0d, 0d, 0));
        }

        Constraints safe = constraints == null ? Constraints.empty() : constraints;
        // 辣度用的是业务值（不吃辣/微辣/能吃辣），必须映射成"能不能上辣菜"再校验
        boolean noSpicy = "不吃辣".equals(safe.spiceLevel()) || "NONE".equals(safe.spiceLevel());
        boolean hotOk = "能吃辣".equals(safe.spiceLevel()) || "HOT".equals(safe.spiceLevel())
                || "SPICY".equals(safe.spiceLevel());
        Set<String> seen = new LinkedHashSet<>();
        int vegetableDays = 0;
        int proteinDays = 0;
        int heavyDishes = 0;
        int totalMinutes = 0;
        int dishCount = 0;
        int meatDishes = 0;
        Map<String, Integer> ingredientUse = new LinkedHashMap<>();

        for (DayInput day : days) {
            List<DishInput> dishes = day.dishes() == null ? List.of() : day.dishes();
            boolean dayHasVegetable = false;
            boolean dayHasProtein = false;
            int dayMinutes = 0;
            for (DishInput dish : dishes) {
                dishCount++;
                String text = dish.text();
                String name = dish.name() == null ? "" : dish.name().trim();
                if (name.isBlank()) {
                    issues.add(new Issue("EMPTY_NAME", Severity.HARD, "菜名为空", ""));
                    continue;
                }
                // 硬校验：忌口与过敏
                for (String blocked : safe.avoid()) {
                    if (!blocked.isBlank() && text.contains(blocked)) {
                        issues.add(new Issue("AVOID_INGREDIENT", Severity.HARD, "含有忌口食材「" + blocked + "」", name));
                    }
                }
                for (String blocked : safe.allergens()) {
                    if (!blocked.isBlank() && text.contains(blocked)) {
                        issues.add(new Issue("ALLERGEN", Severity.HARD, "含有过敏原「" + blocked + "」", name));
                    }
                }
                // 硬校验：辣度
                if (noSpicy && containsAny(text, SPICY_WORDS)) {
                    issues.add(new Issue("SPICE_CONFLICT", Severity.HARD, "用户完全不吃辣", name));
                }
                // 硬校验：老人 / 小孩
                if (safe.elder() && containsAny(text, SPICY_WORDS) && !hotOk) {
                    issues.add(new Issue("ELDER_SPICY", Severity.HARD, "家有老人，不宜辛辣", name));
                }
                if (safe.elder() && containsAny(text, ELDER_RISK_WORDS)) {
                    issues.add(new Issue("ELDER_TEXTURE", Severity.SOFT, "家有老人，避免过硬或油炸做法", name));
                }
                if (safe.child() && containsAny(text, SPICY_WORDS) && !hotOk) {
                    issues.add(new Issue("CHILD_SPICY", Severity.HARD, "家中有小孩，不宜辛辣", name));
                }
                if (safe.child() && containsAny(text, CHILD_RISK_WORDS)) {
                    issues.add(new Issue("CHILD_RISK", Severity.HARD, "家中有小孩，不宜含酒或强刺激调味", name));
                }
                // 硬校验：重复与近期重复
                if (!seen.add(normalize(name))) {
                    issues.add(new Issue("DUPLICATE_DISH", Severity.HARD, "菜单内菜名重复", name));
                }
                if (safe.recentDishes().stream().anyMatch(recent -> !recent.isBlank() && normalize(recent).equals(normalize(name)))) {
                    issues.add(new Issue("REPEAT_RECENT", Severity.HARD, "最近刚吃过这道菜", name));
                }
                if (safe.avoidDishes().stream().anyMatch(bad -> !bad.isBlank() && normalize(bad).equals(normalize(name)))) {
                    issues.add(new Issue("REJECTED_DISH", Severity.HARD, "用户反馈过这道菜不想做/没做成", name));
                }
                // 软校验：时长与难度
                int minutes = dish.cookingTime() == null ? 30 : dish.cookingTime();
                dayMinutes += minutes;
                if (safe.maxCookingMinutes() != null && minutes > safe.maxCookingMinutes()) {
                    issues.add(new Issue("TOO_SLOW", Severity.SOFT, "单菜 " + minutes + " 分钟，超出用户能接受的上限", name));
                }
                boolean heavy = minutes > 45 || "难".equals(dish.difficulty()) || "较难".equals(dish.difficulty());
                if (heavy) heavyDishes++;
                if (safe.preferSimple() && heavy) {
                    issues.add(new Issue("PREFER_SIMPLE", Severity.SOFT, "用户反馈过不想做太难的菜", name));
                }
                if ("SAVE".equals(safe.budget()) && containsAny(text, EXPENSIVE_WORDS)) {
                    issues.add(new Issue("BUDGET_OVER", Severity.SOFT, "预算偏省，食材偏贵", name));
                }
                // 营养结构
                if (containsAny(text, VEGETABLE_WORDS)) dayHasVegetable = true;
                if (containsAny(text, PROTEIN_WORDS)) {
                    dayHasProtein = true;
                    meatDishes++;
                }
                for (String ingredient : dish.ingredients() == null ? List.<String>of() : dish.ingredients()) {
                    String key = normalizeIngredient(ingredient);
                    if (!key.isBlank()) ingredientUse.merge(key, 1, Integer::sum);
                }
            }
            totalMinutes += dayMinutes;
            if (dayHasVegetable) vegetableDays++;
            if (dayHasProtein) proteinDays++;
            if (!dayHasVegetable && !dishes.isEmpty()) {
                issues.add(new Issue("NO_VEGETABLE", Severity.SOFT, "这一天的菜单缺少蔬菜", "周" + day.weekday()));
            }
            if (!dayHasProtein && !dishes.isEmpty()) {
                issues.add(new Issue("NO_PROTEIN", Severity.SOFT, "这一天的菜单缺少优质蛋白", "周" + day.weekday()));
            }
            if (dayMinutes > 90) {
                issues.add(new Issue("DAY_TOO_LONG", Severity.SOFT, "这一天下厨总时长 " + dayMinutes + " 分钟偏长", "周" + day.weekday()));
            }
        }

        double reuseRate = ingredientUse.isEmpty() ? 0d
                : ingredientUse.values().stream().filter(count -> count >= 2).count() / (double) ingredientUse.size();
        if (reuseRate < 0.2) {
            issues.add(new Issue("LOW_REUSE", Severity.SOFT, "食材复用率低，买菜清单会比较零散", ""));
        }
        double meatVegRatio = dishCount == 0 ? 0d : meatDishes / (double) dishCount;
        if (dishCount >= 4 && meatVegRatio > 0.75) {
            issues.add(new Issue("TOO_MUCH_MEAT", Severity.SOFT, "荤菜占比过高，缺少清爽搭配", ""));
        }

        int score = 100;
        for (Issue issue : issues) {
            score -= issue.severity() == Severity.HARD ? 25 : 8;
        }
        score = Math.max(0, Math.min(100, score));
        Stats stats = new Stats(days.size(), vegetableDays, proteinDays,
                Math.round(meatVegRatio * 100d) / 100d,
                Math.round(reuseRate * 100d) / 100d,
                dishCount == 0 ? 0d : Math.round((double) totalMinutes / dishCount * 10d) / 10d,
                heavyDishes);
        return new MenuQuality(score, List.copyOf(issues), stats);
    }

    public static boolean containsAny(String text, List<String> words) {
        return words.stream().anyMatch(text::contains);
    }

    public static String normalize(String name) {
        return name == null ? "" : name.replaceAll("[\\s·・,，、_-]+", "").toLowerCase();
    }

    /** 把"番茄 2 个"归一成"番茄"，用于统计食材复用。 */
    public static String normalizeIngredient(String raw) {
        if (raw == null) return "";
        String value = raw.replaceAll("[0-9０-９]+\\s*(g|克|个|根|颗|块|勺|片|只|条|把|ml|毫升|适量|份)?", "")
                .replaceAll("[（(].*?[)）]", "")
                .replaceAll("[，,、；;].*", "")
                .trim();
        return value.length() > 8 ? value.substring(0, 8) : value;
    }
}
