package com.moodrecipe.backend.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 食品安全知识库：单食材的挑选技巧/烹调警示/慎食人群，以及高风险搭配组合。
 *
 * 内容原则：只收录有公认科学依据的条目（如草酸、皂苷、龙葵素、秋水仙碱、米酵菌酸等），
 * 不收录「食物相克」类缺乏依据的民间说法（如螃蟹×柿子等仅保留鞣酸+蛋白的消化不良风险提示）。
 * 供三处消费：
 * 1. 采购清单：PICK 挑选技巧，帮用户挑出好食材（土豆不买发芽的）；
 * 2. 菜谱/烹饪页：COOK 烹调警示（菠菜焯水去草酸）+ AVOID 慎食人群；
 * 3. 推荐链路：COMBO 搭配风险注入规划提示词与自审校验，防止一周菜单里出现同餐高风险组合。
 */
@Service
public class FoodSafetyService {

    public record FoodTip(String name, String type, String text) { }

    /** 单食材知识条目：关键词命中即返回全部对应提示 */
    private record Entry(String keyword, String pick, String cook, String avoid) {
        static Entry pickOnly(String keyword, String pick) { return new Entry(keyword, pick, null, null); }
    }

    /** 挑选技巧：按食材关键词命中，供采购清单展示 */
    private static final List<Entry> PICK_TIPS = List.of(
            Entry.pickOnly("土豆", "不买发芽、表皮变绿或皱皮的——龙葵素加热也难破坏；选结实、无破损、芽眼浅的"),
            Entry.pickOnly("红薯", "不买表皮有黑斑或发芽的；选硬实、无霉点的"),
            Entry.pickOnly("西红柿", "自然成熟的圆润有弹性、蒂部翠绿；催熟的通体红硬、切开瓤少汁水少"),
            Entry.pickOnly("黄瓜", "选带花带刺、瓜条直、手感硬挺的；蔫软或异常粗大的不买"),
            Entry.pickOnly("豆芽", "选根须自然、无刺鼻氨味的；无根豆芽慎买"),
            Entry.pickOnly("韭菜", "选根部切口平齐新鲜的；窄叶韭菜香味更浓；多冲洗去农残"),
            Entry.pickOnly("菠菜", "选叶色深绿、根部带红的；发黄有水的不要"),
            Entry.pickOnly("白菜", "选包心紧实、叶片无黑点的"),
            Entry.pickOnly("萝卜", "选表皮光滑、掂着沉手的；糠心的轻飘飘"),
            Entry.pickOnly("南瓜", "选表皮坚硬、瓜梗干枯的，更甜耐存"),
            Entry.pickOnly("冬瓜", "选表皮白霜完整、无凹陷斑的"),
            Entry.pickOnly("茄子", "选萼片下露白、手感有弹性的；老茄子发硬无光"),
            Entry.pickOnly("辣椒", "选表皮光亮硬挺的；软塌起皱的不新鲜"),
            Entry.pickOnly("香菇", "选伞盖内卷、菌褶白或淡黄的；伞盖全开偏老"),
            Entry.pickOnly("金针菇", "选颜色自然乳白/淡黄的；异常雪白易碎的慎买"),
            Entry.pickOnly("木耳", "干木耳选朵大背黑腹灰的；泡发后发黏软烂的丢弃"),
            Entry.pickOnly("猪肉", "选淡红色有光泽、按压回弹快的；发暗渗水或切面发黏的不买"),
            Entry.pickOnly("牛肉", "选鲜红有光泽、脂肪洁白的；颜色发暗发黏不买"),
            Entry.pickOnly("排骨", "选粉红色、骨头断面无异味的；颜色发白发绿不买"),
            Entry.pickOnly("鸡肉", "选表皮有光泽、肉质紧实有弹性的；发黏异味不买"),
            Entry.pickOnly("鱼", "选眼睛清亮凸起、鳃色鲜红、按压回弹、鳞片完整的"),
            Entry.pickOnly("虾", "选外壳有光泽、头身连接紧密、无异味的；发黑掉头不买"),
            Entry.pickOnly("螃蟹", "只买鲜活的：掂着沉、捏蟹脚硬实、翻过来能快速翻身；死蟹绝不能吃"),
            Entry.pickOnly("鸡蛋", "选壳面粗糙、摇一摇没有晃动水声的；有异味裂纹不买"),
            Entry.pickOnly("豆腐", "选无酸味、切口平整细腻的；发黏发酸不买"),
            Entry.pickOnly("牛奶", "看保质期与储存条件；胀包结块不买"),
            Entry.pickOnly("香蕉", "选棱角圆钝、微黄的，催熟的棱角锐利"),
            Entry.pickOnly("芒果", "选果皮无黑斑、香气自然的"),
            Entry.pickOnly("柚子", "选同样大小掂着更重、按压不塌陷的，水分足"));

    /** 烹调警示与慎食人群：含草酸、皂苷、龙葵素、秋水仙碱、米酵菌酸等公认风险 */
    private static final List<Entry> COOK_AVOID_TIPS = List.of(
            new Entry("菠菜", null,
                    "草酸含量高：沸水焯 30 秒去除大部分草酸再烹调，避免涩口与草酸钙结石风险",
                    "肾结石（草酸钙结石）患者、高尿酸人群少吃；补钙人群与钙片/牛奶间隔 2 小时以上吃"),
            new Entry("苋菜", null,
                    "草酸含量高：先沸水焯烫再烹调", "肾结石患者少吃"),
            new Entry("空心菜", null,
                    "草酸偏高：焯水后再炒口感更好", "肾结石患者少吃"),
            new Entry("四季豆", null,
                    "含皂苷与植物血凝素：必须彻底煮熟焖透（颜色变深、无生豆腥味）才能吃，否则会恶心呕吐中毒",
                    "没有熟透的四季豆任何人都不可以吃"),
            new Entry("豆角", null,
                    "同四季豆：必须彻底煮熟焖透再吃", "未煮熟的豆角人人不能吃"),
            new Entry("扁豆", null,
                    "同四季豆：必须彻底煮熟焖透再吃", "未煮熟的扁豆人人不能吃"),
            new Entry("鲜黄花菜", null,
                    "含秋水仙碱：必须焯水后浸泡，再彻底炒熟；干制黄花菜更安全",
                    "不熟练处理时建议直接用干黄花菜"),
            new Entry("土豆", null,
                    "发芽、表皮变绿说明龙葵素超标：切掉芽眼也不能吃，整个丢弃；削皮后泡水防氧化变黑",
                    "发绿发芽的土豆任何人不能吃"),
            new Entry("豆浆", null,
                    "必须彻底煮沸再持续煮 5 分钟（「假沸」85℃ 会误导），否则皂苷未破坏易致呕吐",
                    "未煮沸的豆浆人人不能喝"),
            new Entry("木耳", null,
                    "泡发时间别超过 2 小时或放冰箱冷藏泡：久泡可能滋生椰毒假单胞菌产生米酵菌酸（剧毒）",
                    "泡发过久、发黏异味的木耳任何人不能吃"),
            new Entry("皮蛋", null,
                    "选无铅工艺产品；开壳后尽快吃完", "孕妇、婴幼儿少吃；高血压人群注意钠摄入"),
            new Entry("螃蟹", null,
                    "必须蒸透煮熟（15 分钟以上）杀灭寄生虫与细菌；蟹胃蟹腮蟹心去除",
                    "孕妇、痛风、腹泻人群慎吃；死蟹不能吃"),
            new Entry("生腌", null,
                    "淡水水产生食有寄生虫与肝吸虫风险：一律彻底做熟", "孕妇、儿童、免疫力低下者不吃生食水产"),
            new Entry("柿子", null,
                    "不空腹、不大量吃；皮和涩柿鞣酸高要去皮", "胃酸过多、糖尿病人群少吃；不与螃蟹虾鱼牛奶同餐大量吃"),
            new Entry("山楂", null,
                    "空腹大量吃易形成胃石", "胃酸过多、胃溃疡人群少吃"),
            new Entry("鸡蛋", null,
                    "溏心蛋有沙门氏菌风险：孕妇幼儿建议吃全熟蛋", "对鸡蛋过敏者不吃"),
            new Entry("芽", null,
                    "家庭自发豆芽注意容器与水质清洁，久放不吃", null),
            new Entry("腌菜", null,
                    "腌制 2~15 天亚硝酸盐较高：满 20 天后再吃更安全；作配菜少量", "高血压人群少吃（钠含量高）"),
            new Entry("浓汤", null,
                    "久煮的乳白色浓汤（猪骨/老火汤）脂肪与嘌呤都高：营养在肉里不在汤里，吃肉弃汤更稳",
                    "痛风、高尿酸、高血脂人群少喝"),
            new Entry("坚果", null,
                    "整粒坚果有呛噎风险：给幼儿碾碎或磨粉再吃；每天一小把（约 10 克原果）为宜，油炸盐焗款减量",
                    "对坚果过敏者严格避开；5 岁以下儿童不给整粒坚果"),
            new Entry("内脏", null,
                    "动物内脏铁与维生素 A 富集但要限量：每月 2~3 次、每次 25 克左右，烹前浸泡焯水去血水",
                    "痛风、高胆固醇人群少吃；孕妇每周不超过一次（维生素 A 过量风险）"));

    /**
     * 高风险搭配：同一天菜单里同时出现时给出风险提示。
     * 只收录有真实生理机制的组合（鞣酸+蛋白形成胃石/影响消化；草酸+钙影响吸收），
     * 不收录「相克」谣言（如豆浆×鸡蛋、虾×维C）。
     */
    private record Combo(String a, String b, String reason) { }
    private static final List<Combo> RISKY_COMBOS = List.of(
            new Combo("柿子", "螃蟹", "柿子鞣酸与蟹肉高蛋白同餐大量食用易形成胃石、腹胀腹痛"),
            new Combo("柿子", "虾", "鞣酸与高蛋白同餐大量食用影响消化，易形成胃石"),
            new Combo("柿子", "牛奶", "鞣酸遇大量蛋白易结块影响消化，错开 2 小时吃"),
            new Combo("山楂", "螃蟹", "鞣酸与高蛋白同餐大量食用易形成胃石"),
            new Combo("浓茶", "螃蟹", "茶中鞣酸影响蛋白消化吸收，错开吃"),
            new Combo("菠菜", "豆腐", "草酸与钙结合影响吸收：菠菜先焯水即可同烹（并非中毒）"));

    /** 食材名 → 命中的挑选技巧 */
    public List<FoodTip> pickTips(List<String> names) {
        if (names == null || names.isEmpty()) return List.of();
        Set<String> seen = new LinkedHashSet<>();
        List<FoodTip> tips = new ArrayList<>();
        for (String raw : names) {
            String name = raw == null ? "" : raw.trim();
            if (name.isEmpty()) continue;
            for (Entry entry : PICK_TIPS) {
                if ((name.contains(entry.keyword()) || entry.keyword().contains(name)) && seen.add("P:" + entry.keyword())) {
                    tips.add(new FoodTip(name, "PICK", entry.pick()));
                }
            }
        }
        return tips;
    }

    /** 食材名 → 烹调警示（COOK）与慎食人群（AVOID），供菜谱/烹饪页展示 */
    public List<FoodTip> cookingTips(List<String> names) {
        if (names == null || names.isEmpty()) return List.of();
        Set<String> seen = new LinkedHashSet<>();
        List<FoodTip> tips = new ArrayList<>();
        for (String raw : names) {
            String name = raw == null ? "" : raw.trim();
            if (name.isEmpty()) continue;
            for (Entry entry : COOK_AVOID_TIPS) {
                if (!name.contains(entry.keyword()) && !entry.keyword().contains(name)) continue;
                if (entry.cook() != null && seen.add("C:" + entry.keyword())) {
                    tips.add(new FoodTip(name, "COOK", entry.cook()));
                }
                if (entry.avoid() != null && seen.add("A:" + entry.keyword())) {
                    tips.add(new FoodTip(name, "AVOID", entry.avoid()));
                }
            }
        }
        return tips;
    }

    /**
     * 搭配风险校验：输入一组菜谱的食材文本（名称+食材列表拼接），
     * 返回命中的高风险组合及原因；推荐链路用于拦截/自审，采购侧用于同日提示。
     */
    public List<String> riskyCombos(List<String> ingredientTexts) {
        if (ingredientTexts == null || ingredientTexts.isEmpty()) return List.of();
        // 全部食材拼成文本集合，逐个组合判断双方是否各自出现
        String all = String.join("|", ingredientTexts);
        List<String> hits = new ArrayList<>();
        for (Combo combo : RISKY_COMBOS) {
            if (all.contains(combo.a()) && all.contains(combo.b())) {
                hits.add(combo.a() + "×" + combo.b() + "：" + combo.reason());
            }
        }
        return hits;
    }

    /** 给规划提示词的红线文案：模型生成一周菜单时硬性规避（单一事实来源，与 RISKY_COMBOS 同步） */
    public String comboRulesForPrompt() {
        StringBuilder sb = new StringBuilder();
        for (Combo combo : RISKY_COMBOS) {
            sb.append("- ").append(combo.a()).append("与").append(combo.b())
                    .append("：").append(combo.reason()).append("，不要安排在同一天，")
                    .append(combo.a().equals("菠菜") && combo.b().equals("豆腐") ? "同菜必须先焯水" : "也不要做成同一道菜").append("。\n");
        }
        return sb.toString();
    }
}
