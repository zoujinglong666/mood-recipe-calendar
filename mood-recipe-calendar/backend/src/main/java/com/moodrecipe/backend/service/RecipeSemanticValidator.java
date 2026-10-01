package com.moodrecipe.backend.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Deterministic checks for recipe meaning after JSON/schema validation. */
public final class RecipeSemanticValidator {
    private static final Pattern DUPLICATE_ACTION = Pattern.compile(
            "(撒入|撒|加入|放入|放|倒入|调入)([^，。；、\\s]{1,12})和\\2");

    private RecipeSemanticValidator() {
    }

    public static List<String> issues(List<String> ingredients, List<String> steps) {
        Set<String> reasons = new LinkedHashSet<>();
        if (ingredients == null || ingredients.isEmpty()) reasons.add("缺少食材");
        if (steps == null || steps.isEmpty()) reasons.add("缺少做法步骤");
        if (steps != null) {
            Set<String> uniqueSteps = new LinkedHashSet<>();
            for (String step : steps) {
                if (step == null || step.isBlank()) {
                    reasons.add("存在空步骤");
                    continue;
                }
                String value = step.trim();
                if (value.contains("切朵") || value.contains("切成朵")) reasons.add("出现不清晰的切朵表达");
                if (DUPLICATE_ACTION.matcher(value).find()) reasons.add("步骤重复添加同一食材");
                if (!uniqueSteps.add(value)) reasons.add("存在重复步骤");
            }
        }
        return List.copyOf(reasons);
    }

    public static boolean isValid(List<String> ingredients, List<String> steps) {
        return issues(ingredients, steps).isEmpty();
    }
}
