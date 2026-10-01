package com.moodrecipe.backend.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 食材保质期常识库（确定性规则）。
 *
 * 设计原则：**绝不让大模型决定日期**。模型只负责「看图说出这是什么食材」，
 * 保质期一律由这张本地规则表按食材名给出基准天数（冷藏），再由调用方
 * 以「购买日 + 天数」计算到期日，结果可复现、可审计、不会出现模型幻觉日期。
 *
 * 命中策略：关键词包含匹配，取最长命中的关键词，保证「西红柿」不会误命中「柿」。
 * 未命中时回退到按类别（蔬果/肉蛋/乳品…）的保守默认天数。
 */
@Service
public class FoodShelfLifeCatalog {

    /** 类别 → 默认天数（冷藏，未精确命中时使用） */
    private static final Map<String, Integer> CATEGORY_DEFAULT = Map.of(
            "VEGETABLE", 5,
            "FRUIT", 7,
            "MEAT", 3,
            "SEAFOOD", 2,
            "DAIRY", 7,
            "EGG", 30,
            "GRAIN", 180,
            "CONDIMENT", 180,
            "LEFTOVER", 2,
            "OTHER", 5
    );

    /** 关键词 → [类别, 冷藏天数]，按关键词长度降序匹配 */
    private static final Map<String, Entry> TABLE = new LinkedHashMap<>();

    private record Entry(String category, int days) {}

    static {
        // 蔬菜
        put("番茄", "VEGETABLE", 7); put("西红柿", "VEGETABLE", 7);
        put("黄瓜", "VEGETABLE", 7); put("青瓜", "VEGETABLE", 7);
        put("白菜", "VEGETABLE", 10); put("生菜", "VEGETABLE", 5);
        put("菠菜", "VEGETABLE", 4); put("油菜", "VEGETABLE", 4);
        put("芹菜", "VEGETABLE", 7); put("韭菜", "VEGETABLE", 4);
        put("胡萝卜", "VEGETABLE", 14); put("萝卜", "VEGETABLE", 14);
        put("土豆", "VEGETABLE", 21); put("马铃薯", "VEGETABLE", 21);
        put("洋葱", "VEGETABLE", 21); put("南瓜", "VEGETABLE", 14);
        put("冬瓜", "VEGETABLE", 12); put("丝瓜", "VEGETABLE", 7);
        put("茄子", "VEGETABLE", 5); put("青椒", "VEGETABLE", 7);
        put("辣椒", "VEGETABLE", 7); put("西兰花", "VEGETABLE", 5);
        put("菜花", "VEGETABLE", 5); put("花菜", "VEGETABLE", 5);
        put("蘑菇", "VEGETABLE", 4); put("香菇", "VEGETABLE", 5);
        put("金针菇", "VEGETABLE", 5); put("木耳", "VEGETABLE", 30);
        put("豆芽", "VEGETABLE", 3); put("玉米", "VEGETABLE", 5);
        put("山药", "VEGETABLE", 14); put("莲藕", "VEGETABLE", 7);
        put("豆腐", "VEGETABLE", 3); put("豆干", "VEGETABLE", 7);
        put("葱", "VEGETABLE", 7); put("姜", "VEGETABLE", 21); put("蒜", "VEGETABLE", 21);
        put("香菜", "VEGETABLE", 4); put("莴笋", "VEGETABLE", 7);
        put("包菜", "VEGETABLE", 10); put("卷心菜", "VEGETABLE", 10);

        // 水果
        put("苹果", "FRUIT", 21); put("梨", "FRUIT", 14);
        put("香蕉", "FRUIT", 5); put("葡萄", "FRUIT", 7);
        put("草莓", "FRUIT", 3); put("蓝莓", "FRUIT", 7);
        put("橙子", "FRUIT", 21); put("橘子", "FRUIT", 14);
        put("柠檬", "FRUIT", 21); put("西瓜", "FRUIT", 5);
        put("桃", "FRUIT", 5); put("猕猴桃", "FRUIT", 10);
        put("芒果", "FRUIT", 5); put("火龙果", "FRUIT", 7);
        put("圣女果", "FRUIT", 7);

        // 肉禽
        put("牛肉", "MEAT", 3); put("猪肉", "MEAT", 3);
        put("五花肉", "MEAT", 3); put("排骨", "MEAT", 3);
        put("鸡肉", "MEAT", 2); put("鸡胸", "MEAT", 2); put("鸡腿", "MEAT", 2);
        put("鸭肉", "MEAT", 2); put("羊肉", "MEAT", 3);
        put("香肠", "MEAT", 14); put("培根", "MEAT", 7); put("火腿", "MEAT", 14);

        // 水产
        put("鱼", "SEAFOOD", 2); put("虾", "SEAFOOD", 2); put("蟹", "SEAFOOD", 1);
        put("贝", "SEAFOOD", 2); put("蛤蜊", "SEAFOOD", 2); put("鱿鱼", "SEAFOOD", 2);

        // 蛋奶
        put("鸡蛋", "EGG", 30); put("鸭蛋", "EGG", 30); put("鹌鹑蛋", "EGG", 30);
        put("牛奶", "DAIRY", 7); put("酸奶", "DAIRY", 14); put("奶酪", "DAIRY", 30);
        put("黄油", "DAIRY", 60);

        // 主食/干货
        put("米饭", "GRAIN", 2); put("面条", "GRAIN", 3); put("馒头", "GRAIN", 3);
        put("面包", "GRAIN", 5); put("面粉", "GRAIN", 180); put("大米", "GRAIN", 365);
        put("饺子", "GRAIN", 30); put("汤圆", "GRAIN", 180);

        // 调味/其他
        put("酱油", "CONDIMENT", 365); put("醋", "CONDIMENT", 365);
        put("料酒", "CONDIMENT", 365); put("食用油", "CONDIMENT", 365);
        put("剩菜", "LEFTOVER", 2); put("剩饭", "LEFTOVER", 2);
    }

    private static void put(String keyword, String category, int days) {
        TABLE.put(keyword, new Entry(category, days));
    }

    /** 命中结果：类别 + 基准天数 + 是否精确命中（用于前端提示置信度） */
    public record ShelfLife(String category, int days, boolean matched) {}

    /** 按食材名给出冷藏保质期基准天数。 */
    public ShelfLife lookup(String name) {
        String key = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (key.isBlank()) return new ShelfLife("OTHER", CATEGORY_DEFAULT.get("OTHER"), false);

        // 取最长命中的关键词，避免「西红柿」被「柿」这类短词误匹配
        String best = null;
        for (String keyword : TABLE.keySet()) {
            if (key.contains(keyword.toLowerCase(Locale.ROOT))
                    && (best == null || keyword.length() > best.length())) {
                best = keyword;
            }
        }
        if (best != null) {
            Entry entry = TABLE.get(best);
            return new ShelfLife(entry.category(), entry.days(), true);
        }
        return new ShelfLife("OTHER", CATEGORY_DEFAULT.get("OTHER"), false);
    }

    /** 类别默认天数（供识别服务在类别明确但名称未知时使用）。 */
    public int defaultDays(String category) {
        return CATEGORY_DEFAULT.getOrDefault(category == null ? "OTHER" : category.toUpperCase(Locale.ROOT),
                CATEGORY_DEFAULT.get("OTHER"));
    }

    /** 暴露给提示词，让模型只从已知类别里选，避免自由发挥。 */
    public List<String> categories() {
        return List.copyOf(CATEGORY_DEFAULT.keySet());
    }
}
