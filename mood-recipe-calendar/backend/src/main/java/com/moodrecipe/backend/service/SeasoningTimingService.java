package com.moodrecipe.backend.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 调味时序知识库（代码内置，与 FoodSafetyService 同一模式）：
 * 回答「什么时候放调料」这一高频做菜问题。
 *
 * 三条底层原理支撑全部规则：
 * 1. 渗透压——盐渗透最快，早放把食材细胞里的水抽出来（肉柴菜塌，汤越炖越咸）；
 * 2. 挥发性——料酒/醋/香油的香气都靠挥发，时机不对等于白放；
 * 3. 热敏性——味精的谷氨酸钠超 120°C 久煮分解成无鲜味的焦谷氨酸钠，且酸性环境不溶解。
 *
 * 消费方：烹饪页「调味时机卡」（tips）、AI 菜谱/周菜单生成 prompt（promptRules）。
 * 只收公认的科学依据与后厨共识，不收「相克」类谣言。
 */
@Service
public class SeasoningTimingService {

    private record Entry(String keyword, String timing, String reason) {}

    /** 按匹配优先级排列：长关键词在前，避免「生抽」被「抽」类短词误抢（当前无冲突，保持习惯）。 */
    private static final List<Entry> ENTRIES = List.of(
            new Entry("盐", "出锅前 30 秒再放（炖菜最后 10 分钟）",
                    "盐渗透最快，早放会把食材细胞里的水抽出来——肉柴、菜塌、汤越炖越咸。腌肉除外：腌制阶段放盐属正常工艺"),
            new Entry("糖", "早放（炒制上色阶段）",
                    "糖渗透慢，需要时间进入食材上色出焦香；先糖后盐，互不打架"),
            new Entry("料酒", "锅温最高时沿锅边淋一圈",
                    "酒精瞬间挥发带走腥味留下香气；锅不热，料酒味会闷在菜里"),
            new Entry("醋", "出锅前沿锅边烹",
                    "醋香易挥发，早放只剩酸味没有醋香；凉拌菜最后拌入同理"),
            new Entry("生抽", "中段放（炝锅、料酒之后）",
                    "鲜味需要温度激发，但不宜久烧"),
            new Entry("老抽", "早放或炖煮中途",
                    "老抽管上色不管提鲜，颜色需要时间渗进食材"),
            new Entry("味精", "关火前放",
                    "超过 120°C 久煮，鲜味物质会分解成无味的焦谷氨酸钠；放了醋的菜别加味精（酸性不溶解）"),
            new Entry("鸡精", "关火前放",
                    "同理味精：高温久煮鲜味打折，出锅前补才有效"),
            new Entry("香油", "关火后再淋",
                    "香气全靠挥发，下锅早等于白放"),
            new Entry("麻油", "关火后再淋",
                    "香气全靠挥发，下锅早等于白放"),
            new Entry("蚝油", "出锅前加",
                    "久煮鲜味打折，临出锅才是它的舞台"),
            new Entry("水淀粉", "出锅前勾芡",
                    "早放糊锅底，久煮芡会澥"),
            new Entry("八角", "与食材同炖",
                    "硬质香料要久煮才出醇厚底香；别贪多，过量发苦"),
            new Entry("桂皮", "与食材同炖",
                    "同八角：久煮出底香，少量点缀即可"),
            new Entry("香叶", "炖煮中途放",
                    "香气属中段释放，下锅太早香味煮没了"),
            new Entry("蒜末", "分两次放：炝锅一半，出锅前撒一半",
                    "熟蒜香打底，生蒜香点睛——只放一次就浪费了一半"),
            new Entry("干辣椒", "热油小火炝锅",
                    "辣香溶于热油；火大易糊发苦"),
            new Entry("葱花", "分两头：炝锅用葱白，出锅撒葱花",
                    "葱白耐煎出香，葱花生香收尾"),
            new Entry("姜", "炝锅时与油同下",
                    "姜片耐高温，负责打底去腥"));

    /** 按食材清单 + 步骤文本匹配命中的调料，返回「什么时候放」提示（去重、按调料顺序）。 */
    public List<Tip> tips(List<String> ingredients, List<String> steps) {
        String combined = String.join("\n", ingredients == null ? List.<String>of() : ingredients)
                + "\n" + String.join("\n", steps == null ? List.<String>of() : steps);
        Map<String, Tip> hit = new LinkedHashMap<>();
        for (Entry entry : ENTRIES) {
            if (combined.contains(entry.keyword())) {
                hit.putIfAbsent(entry.keyword(), new Tip(entry.keyword(), entry.timing(), entry.reason()));
            }
        }
        return List.copyOf(hit.values());
    }

    /** 注入 AI 生成 prompt 的调味时机硬要求（菜谱步骤 / 周菜单排菜共用，单一事实来源）。 */
    public String promptRules() {
        return """
                调味时机硬要求（写步骤时必须体现，不要笼统写「调味即可」）：
                - 盐：出锅前 30 秒放（炖菜最后 10 分钟），早放食材脱水变柴；腌制阶段放盐除外
                - 糖：上色阶段早放；料酒：锅最热时沿锅边淋；醋：出锅前沿锅边烹
                - 生抽中段放、老抽上色阶段放；味精/鸡精关火前放（有醋的菜不放）
                - 香油关火后淋；水淀粉出锅前勾芡；八角/桂皮与食材同炖；蒜末分炝锅/出锅两次
                """;
    }

    public record Tip(String name, String timing, String reason) {}
}
