package com.moodrecipe.backend.service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * [2026-10-28] 食材文本 → 食物类别映射（「饮食多样性观察」的基础件）。
 *
 * 类别体系对齐《中国居民膳食指南》的食物大类（16 细类），用于计算
 * 「每天 12 种 / 每周 25 种」多样性指标与类别分布环比。
 * 纯启发式关键词命中（与 DailyMealPlanService.CATEGORY_KEYWORDS 同思路，但粒度更细、
 * 修正了「西」「豆」「蛋」等单字误命中），可审计、零 LLM 成本。
 *
 * 已知局限（有意接受）：合成名（如「蟹味棒」）、复合词（「蟹味菇」）先做白名单剥离；
 * 命中数是下界——识别不出的食材不计入，只会低估多样性，不会虚高。
 */
public final class FoodCategoryMapper {

    private FoodCategoryMapper() { }

    /** 细类 → 关键词（有序：先命中先计，同类内一票即覆盖）。 */
    private static final Map<String, String[]> CATEGORIES = new LinkedHashMap<>();

    static {
        CATEGORIES.put("米面主食", new String[]{"米饭", "米粥", "粥", "面条", "面", "米粉", "米线", "馒头", "花卷", "包子", "饺子", "馄饨", "面包", "披萨", "烙饼", "烧饼", "油条", "年糕", "汤圆", "糯米", "意面", "乌冬"});
        CATEGORIES.put("杂粮薯类", new String[]{"燕麦", "荞麦", "糙米", "小米", "玉米", "红薯", "紫薯", "土豆", "山药", "芋头", "藜麦", "薏米", "高粱", "杂粮", "全麦", "南瓜粥"});
        CATEGORIES.put("叶菜", new String[]{"菠菜", "青菜", "生菜", "油麦菜", "白菜", "芹菜", "茼蒿", "苋菜", "空心菜", "荠菜", "韭菜", "油菜", "菜心", "莴笋", "芦笋", "菜花", "西蓝花", "西兰花", "豆苗", "地瓜叶"});
        CATEGORIES.put("瓜茄类", new String[]{"番茄", "西红柿", "茄子", "黄瓜", "冬瓜", "南瓜", "丝瓜", "苦瓜", "西葫芦", "青椒", "彩椒", "辣椒", "秋葵", "瓠瓜", "四季豆", "豇豆", "豆角"});
        CATEGORIES.put("根茎菌藻", new String[]{"萝卜", "莲藕", "藕", "竹笋", "笋", "香菇", "蘑菇", "金针菇", "杏鲍菇", "平菇", "木耳", "银耳", "海带", "紫菜", "茶树菇", "口蘑", "竹荪", "姜", "蒜", "洋葱", "葱"});
        CATEGORIES.put("水果", new String[]{"苹果", "香蕉", "橙", "橘", "柑", "草莓", "蓝莓", "桃", "梨", "葡萄", "芒果", "猕猴桃", "西瓜", "荔枝", "龙眼", "柚", "枣", "李子", "杏", "石榴", "柿子", "哈密瓜", "菠萝", "凤梨", "樱桃", "椰子", "火龙果", "柠檬"});
        CATEGORIES.put("畜禽肉", new String[]{"猪肉", "牛肉", "羊肉", "鸡肉", "鸭肉", "鹅肉", "排骨", "牛排", "鸡排", "鸡腿", "鸡翅", "鸡胸", "里脊", "五花", "腊肉", "香肠", "火腿", "培根", "肉末", "肉末", "肉丝", "肉片", "叉烧", "牛肉", "鸽子", "鸭脖"});
        CATEGORIES.put("动物内脏", new String[]{"猪肝", "肝", "腰花", "猪肚", "肚丝", "鸡心", "鸭肠", "毛肚", "鸡胗", "鸭胗"});
        CATEGORIES.put("水产", new String[]{"鱼", "虾", "蟹", "贝", "鱿鱼", "带鱼", "三文鱼", "鳕鱼", "黄鳝", "鳝", "蛤", "蛏", "牡蛎", "生蚝", "鲍鱼", "龙虾", "海螺", "章鱼", "鱼丸", "虾滑", "虾仁"});
        CATEGORIES.put("蛋类", new String[]{"鸡蛋", "鸭蛋", "鹌鹑蛋", "咸蛋", "皮蛋", "蛋羹", "煎蛋", "炒蛋", "蒸蛋", "荷包蛋", "蛋液", "蛋黄", "蛋白"});
        CATEGORIES.put("奶类", new String[]{"牛奶", "酸奶", "奶酪", "芝士", "奶油", "乳酪", "奶粉", "纯奶", "鲜奶"});
        CATEGORIES.put("豆制品", new String[]{"豆腐", "豆干", "豆浆", "豆皮", "腐竹", "腐乳", "毛豆", "豆豉", "纳豆", "豆芽", "豆花", "豆奶", "千张", "素鸡", "油豆腐"});
        CATEGORIES.put("坚果种子", new String[]{"核桃", "花生", "杏仁", "腰果", "芝麻", "瓜子", "松子", "开心果", "夏威夷果", "巴旦木", "榛子", "亚麻籽", "奇亚籽"});
        CATEGORIES.put("油脂", new String[]{"橄榄油", "香油", "芝麻油", "黄油", "猪油", "辣椒油", "花椒油", "亚麻籽油", "椰子油", "食用油"});
    }

    /** 合成词白名单剥离：先去掉，防止单字关键词误命中（如「蟹味菇」→「蟹」误判水产）。 */
    private static final String[] FALSE_POSITIVES = {"蟹味菇", "蟹黄", "素蟹粉"};

    /**
     * 返回命中的食物类别集合（可能为空——识别不出就不计入，只低估不虚高）。
     *
     * @param name        菜名（可空）
     * @param ingredients 食材文本（可空，逗号/顿号分隔）
     */
    public static Set<String> categoriesOf(String name, String ingredients) {
        String text = normalize(name) + " " + normalize(ingredients);
        if (text.isBlank()) return Set.of();
        Set<String> hit = new LinkedHashSet<>();
        for (Map.Entry<String, String[]> entry : CATEGORIES.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (text.contains(keyword)) {
                    hit.add(entry.getKey());
                    break;
                }
            }
        }
        return hit;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return "";
        for (String fp : FALSE_POSITIVES) {
            value = value.replace(fp, "");
        }
        return value.trim();
    }

    /** 膳食指南多样性目标：每天 12 种食物类别及以上、每周 25 种及以上。 */
    public static final int DAILY_TARGET = 12;
    public static final int WEEKLY_TARGET = 25;

    /** 供展示层取类别全集（保序）。 */
    public static Set<String> allCategories() {
        return Set.copyOf(CATEGORIES.keySet());
    }
}
