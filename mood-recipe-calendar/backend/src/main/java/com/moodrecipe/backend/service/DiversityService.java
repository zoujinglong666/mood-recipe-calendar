package com.moodrecipe.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.entity.WeeklyMealPlan;
import com.moodrecipe.backend.repository.WeeklyMealPlanRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * [2026-10-28] 饮食多样性观察（对齐《中国居民膳食指南》：每天 12 种 / 每周 25 种食物类别）。
 *
 * 数据源：用户最近两份周菜单 planJson（菜品名+食材文本），用 FoodCategoryMapper 启发式映射，
 * 零 LLM 成本、可复算。观察红线：只做「计数 + 分布 + 一句建议」，不做达标诊断、
 * 不出现「缺乏/不足」等医疗表述——类别识别是下界，宁可低估不虚高。
 */
@Service
public class DiversityService {

    private final WeeklyMealPlanRepository plans;
    private final ObjectMapper json;

    public DiversityService(WeeklyMealPlanRepository plans, ObjectMapper json) {
        this.plans = plans;
        this.json = json;
    }

    // ==== 输出结构 ====

    /** 每天的类别覆盖。 */
    public record DayDiversity(String day, List<String> categories, int distinctCount) { }

    /** 类别分布：本周出现天数、占类别·天总数的百分比、与上一份计划的环比（百分点，可空）。 */
    public record CategoryShare(String category, int daysPresent, double sharePercent, Double deltaPercent) { }

    /** 本周重复出现的菜。 */
    public record RepeatedDish(String name, int count) { }

    /**
     * 周多样性视图。
     * weeklyTargetMet：weeklyDistinct >= 25；avgDailyDistinct：日均类别数。
     * insights：≤3 条可读观察句（「建议关注」语气，不诊断）。
     */
    public record DiversityView(Long planId, int dailyTarget, int weeklyTarget,
                                List<DayDiversity> days, int weeklyDistinct, boolean weeklyTargetMet,
                                double avgDailyDistinct, List<CategoryShare> distribution,
                                List<RepeatedDish> repeatedDishes, List<String> insights) { }

    /**
     * 基于用户最近一份周菜单计算多样性，并与上一份做分布环比。
     * 没有周计划时返回 empty（控制器转 404）。
     */
    public Optional<DiversityView> weekly(String openid) {
        List<WeeklyMealPlan> recent = plans.findTop20ByOpenidOrderByCreatedAtDesc(openid);
        if (recent.isEmpty()) return Optional.empty();
        WeeklyMealPlan current = recent.get(0);
        List<WeeklyMealPlanService.PlanDay> currentDays = readDays(current);
        if (currentDays == null || currentDays.isEmpty()) return Optional.empty();

        List<WeeklyMealPlanService.PlanDay> previousDays = recent.size() > 1 ? readDays(recent.get(1)) : List.of();
        Map<String, Double> previousShares = previousDays.isEmpty()
                ? Map.of() : sharesByCategory(previousDays);

        // 每天类别覆盖
        List<DayDiversity> days = new ArrayList<>();
        Set<String> weeklyDistinct = new LinkedHashSet<>();
        Map<String, Integer> dishCount = new HashMap<>();
        for (WeeklyMealPlanService.PlanDay day : currentDays) {
            Set<String> categories = categoriesOfDay(day);
            weeklyDistinct.addAll(categories);
            days.add(new DayDiversity(safe(day.day()), List.copyOf(categories), categories.size()));
            for (String dish : dishNames(day)) {
                dishCount.merge(dish, 1, Integer::sum);
            }
        }

        // 类别分布（按出现天数）+ 环比
        Map<String, Integer> present = new TreeMap<>();
        for (DayDiversity day : days) {
            for (String category : day.categories()) {
                present.merge(category, 1, Integer::sum);
            }
        }
        int totalSlots = present.values().stream().mapToInt(Integer::intValue).sum();
        List<CategoryShare> distribution = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : present.entrySet()) {
            double share = totalSlots == 0 ? 0 : round1(entry.getValue() * 100.0 / totalSlots);
            Double delta = previousShares.isEmpty() ? null
                    : round1(share - previousShares.getOrDefault(entry.getKey(), 0.0));
            distribution.add(new CategoryShare(entry.getKey(), entry.getValue(), share, delta));
        }
        distribution.sort(Comparator.comparingDouble(CategoryShare::sharePercent).reversed());

        // 重复菜（>=2 次，取前 3）
        List<RepeatedDish> repeated = dishCount.entrySet().stream()
                .filter(entry -> entry.getValue() >= 2)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .map(entry -> new RepeatedDish(entry.getKey(), entry.getValue()))
                .toList();

        double avgDaily = days.isEmpty() ? 0
                : round1(days.stream().mapToInt(DayDiversity::distinctCount).average().orElse(0));
        List<String> insights = buildInsights(weeklyDistinct.size(), avgDaily, present);

        return Optional.of(new DiversityView(current.getId(),
                FoodCategoryMapper.DAILY_TARGET, FoodCategoryMapper.WEEKLY_TARGET,
                days, weeklyDistinct.size(), weeklyDistinct.size() >= FoodCategoryMapper.WEEKLY_TARGET,
                avgDaily, distribution, repeated, insights));
    }

    // ==== 内部 ====

    private List<WeeklyMealPlanService.PlanDay> readDays(WeeklyMealPlan plan) {
        try {
            return json.readValue(plan.getPlanJson(), new TypeReference<>() { });
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            return List.of();
        }
    }

    private Set<String> categoriesOfDay(WeeklyMealPlanService.PlanDay day) {
        Set<String> all = new LinkedHashSet<>();
        all.addAll(FoodCategoryMapper.categoriesOf(day.dishName(), join(day.ingredients())));
        if (day.dishes() != null) {
            for (WeeklyMealPlanService.PlanDish dish : day.dishes()) {
                if (dish == null) continue;
                all.addAll(FoodCategoryMapper.categoriesOf(dish.name(), join(dish.ingredients())));
            }
        }
        return all;
    }

    /** 分布按「类别·天」占比算（当前周），供环比对比用。 */
    private Map<String, Double> sharesByCategory(List<WeeklyMealPlanService.PlanDay> days) {
        Map<String, Integer> present = new TreeMap<>();
        for (WeeklyMealPlanService.PlanDay day : days) {
            for (String category : categoriesOfDay(day)) {
                present.merge(category, 1, Integer::sum);
            }
        }
        int total = present.values().stream().mapToInt(Integer::intValue).sum();
        if (total == 0) return Map.of();
        Map<String, Double> shares = new HashMap<>();
        present.forEach((category, count) -> shares.put(category, round1(count * 100.0 / total)));
        return shares;
    }

    private List<String> dishNames(WeeklyMealPlanService.PlanDay day) {
        List<String> names = new ArrayList<>();
        if (day.dishName() != null && !day.dishName().isBlank()) names.add(day.dishName().trim());
        if (day.dishes() != null) {
            for (WeeklyMealPlanService.PlanDish dish : day.dishes()) {
                if (dish != null && dish.name() != null && !dish.name().isBlank()) names.add(dish.name().trim());
            }
        }
        return names;
    }

    /** ≤3 条观察句：多样性总量 → 日均 → 最薄弱的常驻类别。只「建议关注」，不诊断。 */
    private List<String> buildInsights(int weeklyDistinct, double avgDaily, Map<String, Integer> present) {
        List<String> lines = new ArrayList<>();
        if (weeklyDistinct >= FoodCategoryMapper.WEEKLY_TARGET) {
            lines.add("本周共出现 " + weeklyDistinct + " 种食物类别，达到膳食指南建议的 25 种，搭配很丰富。");
        } else {
            lines.add("本周共出现 " + weeklyDistinct + " 种食物类别，距离膳食指南建议的 25 种"
                    + "还差 " + (FoodCategoryMapper.WEEKLY_TARGET - weeklyDistinct) + " 种。");
        }
        if (avgDaily < FoodCategoryMapper.DAILY_TARGET) {
            lines.add("平均每天 " + avgDaily + " 种（建议 ≥" + FoodCategoryMapper.DAILY_TARGET
                    + " 种），可以每餐多加一样小菜或杂粮。");
        }
        // 关注名单里出现天数最少的（≤2 天才提，避免噪音）
        List<String> watchlist = List.of("奶类", "水产", "杂粮薯类", "豆制品", "坚果种子");
        String weakest = null;
        int weakestDays = Integer.MAX_VALUE;
        for (String category : watchlist) {
            int daysPresent = present.getOrDefault(category, 0);
            if (daysPresent <= 2 && daysPresent < weakestDays) {
                weakest = category;
                weakestDays = daysPresent;
            }
        }
        if (weakest != null) {
            lines.add("「" + weakest + "」本周只出现 " + weakestDays + " 天，建议关注一下。");
        }
        return lines.size() > 3 ? lines.subList(0, 3) : lines;
    }

    private static String join(List<String> items) {
        return items == null ? "" : String.join(" ", items);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
