package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.NutritionKnowledgePack;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.entity.UserFoodPreference;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 一日三餐结果的最后一道硬校验；任一问题都会阻止结果进入前端。 */
@Service
public class DailyMealPlanValidator {

    private static final List<CoreIngredient> CORE_INGREDIENTS = List.of(
            new CoreIngredient("鸡蛋", "蛋"),
            new CoreIngredient("鸡", "鸡肉"),
            new CoreIngredient("牛", "牛肉"),
            new CoreIngredient("猪", "猪肉"),
            new CoreIngredient("虾", "虾类"),
            new CoreIngredient("鱼", "鱼类"),
            new CoreIngredient("豆腐", "豆腐"),
            new CoreIngredient("米", "米饭"),
            new CoreIngredient("面", "面食"),
            new CoreIngredient("粉", "粉类"),
            new CoreIngredient("馒头", "馒头"),
            new CoreIngredient("面包", "面包"),
            new CoreIngredient("土豆", "土豆"),
            new CoreIngredient("玉米", "玉米"));

    private final AllergenNormalizationService allergens;

    public DailyMealPlanValidator(AllergenNormalizationService allergens) {
        this.allergens = allergens;
    }

    public ValidationResult validate(List<Recipe> meals, UserFoodPreference preference,
                                     List<NutritionKnowledgePack> packs) {
        List<String> reasons = new ArrayList<>();
        if (meals == null || meals.size() != 3) reasons.add("一日计划必须包含完整三餐");
        if (packs == null || packs.isEmpty() || packs.stream().anyMatch(pack -> pack == null || !pack.isEligible())) {
            reasons.add("知识包缺少可商用授权或未启用");
        }
        if (meals == null) return new ValidationResult(false, List.copyOf(reasons));

        List<String> blocked = preference == null ? List.of()
                : allergens.normalize(preference.getAvoidIngredients(), preference.getAllergens());
        Set<String> names = new HashSet<>();
        Set<String> coreIngredients = new HashSet<>();
        for (Recipe meal : meals) {
            if (meal == null) {
                reasons.add("菜谱为空");
                continue;
            }
            String name = safe(meal.getName());
            String ingredients = safe(meal.getIngredients());
            String steps = safe(meal.getSteps());
            if (!displayable(name, 40)) reasons.add("菜名不可展示");
            if (!displayable(ingredients, 500) || !displayable(steps, 1500)) reasons.add("菜谱内容不可展示");
            if (!RecipeSafetyPolicy.isSafe(meal)) reasons.add("菜谱不满足安全限制：" + name);
            if (!names.add(normalize(name))) reasons.add("三餐菜名重复：" + name);
            String text = (name + " " + ingredients).toLowerCase(Locale.ROOT);
            for (String term : blocked) {
                if (!term.isBlank() && text.contains(term.toLowerCase(Locale.ROOT))) {
                    reasons.add("命中用户过敏或忌口：" + term);
                    break;
                }
            }
            String core = coreIngredient(text);
            if (core != null && !coreIngredients.add(core)) reasons.add("跨餐核心食材重复：" + core);
        }
        return new ValidationResult(reasons.isEmpty(), List.copyOf(reasons));
    }

    private String coreIngredient(String text) {
        return CORE_INGREDIENTS.stream()
                .filter(item -> text.contains(item.keyword()))
                .map(CoreIngredient::label)
                .findFirst().orElse(null);
    }

    private boolean displayable(String value, int maxLength) {
        if (value.isBlank() || value.length() > maxLength) return false;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            if (codePoint == 0xfffd || Character.isISOControl(codePoint)
                    || (codePoint >= 0x80 && codePoint <= 0xff && codePoint != 0x00b7)) return false;
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    private String normalize(String value) {
        return value.replaceAll("[\\s·，,。.!！?？]", "").toLowerCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public record ValidationResult(boolean valid, List<String> reasons) { }

    private record CoreIngredient(String keyword, String label) { }
}
