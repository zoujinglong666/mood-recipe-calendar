package com.moodrecipe.backend.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 食材同义词归一化（对标 FridgeApp 的 matching/ 倒排索引思路）。
 *
 * 作用：把「番茄=西红柿=洋柿子」「葱花=小葱=香葱」这类口语/别称归一到同一标准词，
 * - 搜索扩展：用户查「西红柿」也能召回菜名/食材里写「番茄」的菜，提升菜谱检索召回率；
 * - 识别归一：把「葱花」「小葱」都识别成「葱」，提升食材复用统计与买菜清单合并准确率。
 *
 * 数据以「标准词 + 别名组」静态维护（80+ 别名），无需依赖 LLM，确定性、零成本、可离线。
 * 后续若要扩展到数据库驱动，只需把 GROUPS 换成从配置/表加载即可。
 */
@Service
public class IngredientSynonymService {

    /** 每组：[标准词, 别名1, 别名2, ...] */
    private static final List<List<String>> GROUPS = List.of(
            List.of("番茄", "西红柿", "洋柿子", "蕃茄", "洋番茄"),
            List.of("土豆", "马铃薯", "洋芋", "土豆仔", "山药蛋"),
            List.of("茄子", "矮瓜", "紫茄", "落苏"),
            List.of("黄瓜", "青瓜", "胡瓜", "刺瓜"),
            List.of("玉米", "粟米", "包谷", "甜玉米", "玉蜀黍"),
            List.of("青椒", "甜椒", "灯笼椒", "菜椒", "柿子椒"),
            List.of("洋葱", "葱头", "圆葱", "玉葱"),
            List.of("西兰花", "绿菜花", "西蓝花", "青花菜"),
            List.of("胡萝卜", "红萝卜", "胡萝蔔", "甘荀"),
            List.of("菠菜", "菠薐菜", "鹦鹉菜"),
            List.of("芹菜", "西芹", "旱芹", "香芹"),
            List.of("生菜", "莴苣", "叶用莴苣", "油麦菜"),
            List.of("白菜", "大白菜", "黄芽白", "结球白菜"),
            List.of("卷心菜", "包菜", "圆白菜", "高丽菜", "甘蓝", "椰菜"),
            List.of("葱", "葱花", "小葱", "香葱", "青葱", "芫荽葱"),
            List.of("蒜", "大蒜", "蒜头", "大蒜头", "蒜瓣"),
            List.of("姜", "生姜", "嫩姜", "老姜", "黄姜"),
            List.of("香菜", "芫荽", "芫茜", "胡荽"),
            List.of("韭菜", "起阳草", "扁菜"),
            List.of("南瓜", "金瓜", "倭瓜", "番瓜"),
            List.of("莲藕", "藕", "莲菜"),
            List.of("山药", "淮山", "怀山", "薯蓣"),
            List.of("芋头", "芋艿", "毛芋", "里芋"),
            List.of("红薯", "地瓜", "番薯", "甘薯", "山芋"),
            List.of("花生", "落花生", "长生果", "土豆花生"),
            List.of("芝麻", "白芝麻", "黑芝麻", "胡麻"),
            List.of("辣椒", "尖椒", "小米辣", "朝天椒", "辣椒圈"),
            List.of("香菇", "冬菇", "香信", "花菇"),
            List.of("木耳", "黑木耳", "云耳", "黑菜"),
            List.of("金针菇", "金菇", "朴菰"),
            List.of("豆腐", "嫩豆腐", "老豆腐", "水豆腐", "板豆腐"),
            List.of("鸡蛋", "鸡仔蛋", "土鸡蛋", "鸡卵", "洋鸡蛋"),
            List.of("牛肉", "牛腩", "牛柳", "牛里脊", "牛上脑"),
            List.of("猪肉", "猪瘦肉", "五花肉", "猪腩", "豚肉", "猪绞肉"),
            List.of("鸡肉", "鸡胸肉", "鸡腿肉", "鸡柳", "鸡丁"),
            List.of("虾", "虾仁", "海虾", "基围虾", "对虾", "明虾"),
            List.of("鱼", "鱼肉", "鲜鱼", "淡水鱼", "海鱼"),
            List.of("排骨", "猪排骨", "肋排", "小排"),
            List.of("面粉", "中筋面粉", "白面", "麦粉"),
            List.of("米饭", "白饭", "大米饭", "粳米饭"),
            List.of("面条", "面", "挂面", "鲜面", "碱面", "意面"),
            List.of("馒头", "馍", "饽饽", "白面馒头"),
            List.of("饺子", "水饺", "饺", "饺子和"),
            List.of("醋", "米醋", "陈醋", "香醋", "白醋"),
            List.of("酱油", "生抽", "老抽", "豉油", "酱青"),
            List.of("白糖", "白砂糖", "砂糖", "绵白糖"),
            List.of("料酒", "黄酒", "米酒", "料理酒"),
            List.of("牛奶", "牛乳", "鲜奶", "纯牛奶"),
            List.of("酸奶", "酸乳", "优格", "发酵乳"),
            List.of("汤圆", "元宵", "汤团"),
            List.of("油条", "油炸鬼", "馃子"),
            List.of("虾皮", "虾米", "海米", "虾干"),
            List.of("粉丝", "粉条", "细粉", "冬粉"),
            List.of("年糕", "粳米年糕", "糍粑"),
            List.of("蛤蜊", "花蛤", "文蛤", "蚬子"),
            List.of("蟹", "螃蟹", "大闸蟹", "毛蟹"),
            List.of("紫菜", "海苔", "坛紫菜"),
            List.of("丝瓜", "胜瓜", "蛮瓜"),
            List.of("苦瓜", "凉瓜", "癞瓜"),
            List.of("冬瓜", "白瓜", "枕瓜"),
            List.of("茭白", "茭笋", "高笋"),
            List.of("竹笋", "笋", "春笋", "冬笋"),
            List.of("毛豆", "青豆", "黄豆荚", "菜用大豆"),
            List.of("豌豆", "青豌豆", "荷兰豆", "雪豆"),
            List.of("扁豆", "四季豆", "芸豆", "眉豆"),
            List.of("山楂", "红果", "山里红"),
            List.of("桂圆", "龙眼", "圆肉"),
            List.of("荔枝", "离枝", "丹荔"),
            List.of("草莓", "士多啤梨", "凤梨草莓"),
            List.of("菠萝", "凤梨", "黄梨"),
            List.of("猕猴桃", "奇异果", "茅梨"),
            List.of("酸奶油", "酸忌廉"),
            List.of("番茄酱", "西红柿酱", "茄汁", "ketchup")
    );

    /** 别名/标准词 -> 标准词（任一写法都能归一到标准词） */
    private final Map<String, String> canonicalMap = new LinkedHashMap<>();
    /** 标准词 -> 整组所有写法（含标准词自身），用于搜索扩展 */
    private final Map<String, Set<String>> groupMap = new LinkedHashMap<>();

    public IngredientSynonymService() {
        for (List<String> group : GROUPS) {
            if (group.isEmpty()) continue;
            String canonical = group.get(0);
            Set<String> variants = new LinkedHashSet<>(group);
            groupMap.put(canonical, variants);
            for (String term : group) {
                // 多个组若出现重叠词，标准词优先；否则以先出现的组为准
                canonicalMap.computeIfAbsent(term, ignored -> canonical);
            }
        }
    }

    /** 该词是否在同义词表里（无论标准词还是别名）。 */
    public boolean known(String term) {
        return term != null && canonicalMap.containsKey(term.trim());
    }

    /**
     * 把任意写法归一为标准词。未知写法原样返回（已去首尾空白）。
     * 用于食材识别、买菜清单合并：让「番茄」「西红柿」都变成「番茄」。
     */
    public String canonical(String term) {
        if (term == null) return "";
        String trimmed = term.trim();
        return canonicalMap.getOrDefault(trimmed, trimmed);
    }

    /**
     * 返回某查询词对应的全部写法（含标准词与所有别名）。
     * 查询词本身不在同义词表时返回空集，调用方应回退到精确匹配。
     */
    public Set<String> variants(String query) {
        if (query == null || query.isBlank()) return Set.of();
        String canonical = canonicalMap.get(query.trim());
        if (canonical == null) return Set.of();
        return groupMap.getOrDefault(canonical, Set.of(canonical));
    }

    /**
     * 食材/菜谱文本是否命中查询词（含同义词扩展）。
     * 命中条件：文本包含 query 本身，或包含 query 同义词组里的任一写法。
     * 查询词不在同义词表时退化为精确子串匹配，行为与原逻辑一致。
     */
    public boolean matchesText(String text, String query) {
        if (text == null || query == null || query.isBlank()) return false;
        if (text.contains(query)) return true;
        for (String variant : variants(query)) {
            if (text.contains(variant)) return true;
        }
        return false;
    }

    /** 调试用：返回同义词组数量与别名总数。 */
    public int groupCount() {
        return groupMap.size();
    }

    public int aliasCount() {
        return canonicalMap.size();
    }

    /** 测试用例友善：列出全部标准词。 */
    public List<String> canonicals() {
        return new ArrayList<>(groupMap.keySet());
    }
}
