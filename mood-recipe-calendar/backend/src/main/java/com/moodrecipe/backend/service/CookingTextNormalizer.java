package com.moodrecipe.backend.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small, deterministic cleanup for model-generated cooking text. */
public final class CookingTextNormalizer {
    private static final Pattern DUPLICATE_ACTION = Pattern.compile(
            "(撒入|撒|加入|放入|放|倒入|调入)([^，。；、\\s]{1,12})和\\2");
    private static final Pattern ACTION_PREFIX = Pattern.compile(
            "^(撒入|撒|加入|放入|放|倒入|投入|调入|切成|切|洗净|洗好|准备好的)\\s*");

    private CookingTextNormalizer() {
    }

    public static List<String> normalizeIngredients(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        Map<String, String> unique = new LinkedHashMap<>();
        for (String raw : values) {
            String value = clean(raw);
            if (value.isBlank()) continue;
            String key = ingredientKey(value);
            String previous = unique.get(key);
            if (previous == null || (value.length() > previous.length() && !value.startsWith("撒"))) {
                unique.put(key, value);
            }
        }
        return List.copyOf(unique.values());
    }

    public static List<String> normalizeSteps(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        List<String> result = new ArrayList<>();
        for (String raw : values) {
            String value = clean(raw)
                    .replace("切成朵", "掰成小朵")
                    .replace("切朵", "掰成小朵")
                    .replace("切朵状", "掰成小朵");
            String previous;
            do {
                previous = value;
                Matcher matcher = DUPLICATE_ACTION.matcher(value);
                value = matcher.replaceAll("$1$2");
            } while (!previous.equals(value));
            if (!value.isBlank()) result.add(value);
        }
        return List.copyOf(result);
    }

    private static String ingredientKey(String value) {
        String key = ACTION_PREFIX.matcher(value).replaceFirst("");
        return key.replaceAll("[：:，,。；;、\\s]", "").toLowerCase(Locale.ROOT);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }
}
