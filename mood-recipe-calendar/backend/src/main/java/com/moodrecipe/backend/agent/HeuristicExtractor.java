package com.moodrecipe.backend.agent;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 确定性兜底抽取：模型不可用时仍然能听懂"几人、几道、不吃辣"，并解析卡片点选。
 *
 * 它不再是主力，只作为模型抽取失败时的安全网，以及卡片点选的解析通道。
 */
public final class HeuristicExtractor {

    private static final Map<String, String> REGIONS = Map.of(
            "江西", "赣菜", "四川", "川菜", "湖南", "湘菜", "广东", "粤菜",
            "浙江", "江浙菜", "东北", "东北菜", "西北", "西北菜", "云南", "云贵菜");

    /** 城市 → 家乡菜系：让"我老家在抚州"也能落到赣菜，而不是只认省份名。 */
    private static final Map<String, String> CITY_TO_CUISINE = Map.ofEntries(
            Map.entry("抚州", "赣菜"), Map.entry("南昌", "赣菜"), Map.entry("九江", "赣菜"),
            Map.entry("赣州", "赣菜"), Map.entry("上饶", "赣菜"), Map.entry("宜春", "赣菜"),
            Map.entry("吉安", "赣菜"), Map.entry("景德镇", "赣菜"), Map.entry("萍乡", "赣菜"),
            Map.entry("新余", "赣菜"), Map.entry("鹰潭", "赣菜"),
            Map.entry("成都", "川菜"), Map.entry("重庆", "川菜"), Map.entry("绵阳", "川菜"),
            Map.entry("长沙", "湘菜"), Map.entry("株洲", "湘菜"), Map.entry("湘潭", "湘菜"),
            Map.entry("广州", "粤菜"), Map.entry("深圳", "粤菜"), Map.entry("佛山", "粤菜"),
            Map.entry("东莞", "粤菜"), Map.entry("珠海", "粤菜"),
            Map.entry("杭州", "江浙菜"), Map.entry("宁波", "江浙菜"), Map.entry("温州", "江浙菜"),
            Map.entry("南京", "江浙菜"), Map.entry("苏州", "江浙菜"), Map.entry("上海", "江浙菜"),
            Map.entry("哈尔滨", "东北菜"), Map.entry("沈阳", "东北菜"), Map.entry("长春", "东北菜"),
            Map.entry("大连", "东北菜"),
            Map.entry("西安", "西北菜"), Map.entry("兰州", "西北菜"), Map.entry("西宁", "西北菜"),
            Map.entry("银川", "西北菜"),
            Map.entry("昆明", "云贵菜"), Map.entry("贵阳", "云贵菜"));

    private HeuristicExtractor() {
    }

    /** 解析卡片点选回来的 value，例如 people=3 / days=0,1,2。 */
    public static DialogueState.AgentState applySelection(DialogueState.AgentState state,
                                                                     String input) {
        if (input == null || input.isBlank()) return state;
        String value = input.trim();
        if (value.startsWith("people=")) return state.withPeople(clamp(parseInt(value.substring(7)), 1, 50));
        if (value.startsWith("dishes=")) return state.withDishesPerDay(clamp(parseInt(value.substring(7)), 1, 20));
        if (value.startsWith("spice=")) return state.withSpiceLevel(value.substring(6));
        if (value.startsWith("goal=")) return state.withHealthGoal(value.substring(5));
        if (value.startsWith("budget=")) return state.withBudget(value.substring(7));
        if (value.equals("elder=yes")) return state.withHasElder(true);
        if (value.equals("child=yes")) return state.withHasChild(true);
        if (value.startsWith("household=")) {
            String household = value.substring(10);
            return state.withHousehold(household.contains("elder"), household.contains("child"));
        }
        if (value.equals("记住")) return state.withCuisineConfirmed(true);
        if (value.startsWith("days=")) {
            List<Integer> days = Arrays.stream(value.substring(5).split(","))
                    .map(HeuristicExtractor::parseInt).filter(Objects::nonNull)
                    .filter(day -> day >= 0 && day < 7).distinct().sorted().toList();
            return days.isEmpty() ? state : state.withCookingDays(days);
        }
        return state;
    }

    /** 从自然话里抽事实；模型可用时只作为补充，模型不可用时作为主力。 */
    public static List<AgentFact> facts(String input) {
        List<AgentFact> facts = new ArrayList<>();
        if (input == null || input.isBlank()) return facts;
        String text = input.trim();

        Integer people = number(text, "(?:个|位)?(?:人|口|客人)");
        if (people != null) facts.add(AgentFact.explicit("people", String.valueOf(people), "原话：" + text));
        Integer dishes = number(text, "道");
        if (dishes != null) facts.add(AgentFact.explicit("dishesPerDay", String.valueOf(dishes), "原话：" + text));

        boolean mealContext = contains(text, "客人", "宾客", "宴请", "聚餐", "家宴", "请客", "招待", "酒席", "生日", "节日");
        if (mealContext) {
            facts.add(AgentFact.explicit("mealContext", text.substring(0, Math.min(text.length(), 200)), "原话：" + text));
            if (text.contains("今天")) {
                facts.add(AgentFact.explicit("cookingDays",
                        String.valueOf(LocalDate.now().getDayOfWeek().getValue() - 1), "用户明确说今天"));
            }
        }

        boolean hasElder = contains(text, "老人", "长辈");
        boolean hasChild = contains(text, "小孩", "孩子", "宝宝");
        if (hasElder || hasChild) {
            String household = hasElder && hasChild ? "有老人和小孩" : hasElder ? "有老人" : "有小孩";
            facts.add(AgentFact.explicit("household", household, "原话：" + text));
        }

        if (contains(text, "不吃辣", "不能吃辣", "一点辣都不")) facts.add(AgentFact.explicit("spice", "不吃辣", "原话：" + text));
        else if (contains(text, "微辣", "少辣", "一点点辣")) facts.add(AgentFact.explicit("spice", "微辣", "原话：" + text));
        else if (contains(text, "能吃辣", "重辣", "无辣不欢")) facts.add(AgentFact.explicit("spice", "能吃辣", "原话：" + text));

        if (contains(text, "健身", "增肌")) facts.add(AgentFact.explicit("healthGoal", "FITNESS", "原话：" + text));
        else if (contains(text, "减脂", "变瘦", "轻一点", "清淡")) facts.add(AgentFact.explicit("healthGoal", "LEAN", "原话：" + text));
        if (contains(text, "省钱", "便宜", "省一点")) facts.add(AgentFact.explicit("budget", "SAVE", "原话：" + text));
        else if (contains(text, "丰盛")) facts.add(AgentFact.explicit("budget", "TREAT", "原话：" + text));

        String cuisine = cuisine(text);
        if (cuisine != null && !mealContext) {
            boolean byCity = CITY_TO_CUISINE.entrySet().stream().anyMatch(entry -> text.contains(entry.getKey()));
            facts.add(byCity
                    ? AgentFact.inferred("favoriteCuisine", cuisine, "从家乡城市推断：" + text)
                    : AgentFact.explicit("favoriteCuisine", cuisine, "原话：" + text));
        }
        return facts;
    }

    public static String cuisine(String text) {
        if (text == null) return null;
        for (Map.Entry<String, String> entry : CITY_TO_CUISINE.entrySet()) {
            if (text.contains(entry.getKey())) return entry.getValue();
        }
        for (Map.Entry<String, String> entry : REGIONS.entrySet()) {
            if (text.contains(entry.getKey()) || text.contains(entry.getValue())) return entry.getValue();
        }
        return null;
    }

    public static Set<String> cuisines() {
        return Set.copyOf(CITY_TO_CUISINE.values());
    }

    private static boolean contains(String text, String... words) {
        return Arrays.stream(words).anyMatch(text::contains);
    }

    private static Integer number(String text, String unit) {
        Matcher matcher = Pattern.compile("([0-9]{1,2}|[一二三四五六七八九十两]{1,3})\\s*" + unit).matcher(text);
        return matcher.find() ? parseNumber(matcher.group(1)) : null;
    }

    private static Integer parseNumber(String value) {
        Integer number = parseInt(value);
        if (number != null) return number;
        String normalized = value.replace('两', '二');
        String digits = "零一二三四五六七八九";
        int ten = normalized.indexOf('十');
        if (ten >= 0) {
            int tens = ten == 0 ? 1 : digits.indexOf(normalized.charAt(0));
            int ones = ten == normalized.length() - 1 ? 0 : digits.indexOf(normalized.charAt(ten + 1));
            return tens > 0 && ones >= 0 ? tens * 10 + ones : null;
        }
        return normalized.length() == 1 && digits.indexOf(normalized.charAt(0)) > 0
                ? digits.indexOf(normalized.charAt(0)) : null;
    }

    private static Integer parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Integer clamp(Integer value, int min, int max) {
        return value == null ? null : Math.max(min, Math.min(max, value));
    }
}
