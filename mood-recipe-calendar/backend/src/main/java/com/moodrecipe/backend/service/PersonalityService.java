package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.agent.LlmClient;
import com.moodrecipe.backend.agent.LlmRequest;
import com.moodrecipe.backend.agent.TimeoutTier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 干饭人格 / 做饭人格 · AI 语义判定（双测试目录）。
 *
 * 统一语义原则：两套测试的题目、选项、16 型目录全部以本服务为唯一事实来源——
 * LLM 只负责「读 12 个选择 → 从目录判型 → 写专属点评」，不允许自创人格名词；
 * LLM 不可用或输出越界时退回确定性计分（同目录）。
 */
@Service
public class PersonalityService {

    private static final Logger log = LoggerFactory.getLogger(PersonalityService.class);

    private static final Set<String> TEST_KEYS = Set.of("eat", "cook");
    private static final Set<String> LUCKY_MOODS = Set.of(
            "开心", "平静", "疲惫", "焦虑", "难过", "嘴馋", "低落", "想家", "期待", "满足", "得意", "害羞");

    private record Question(String text, String dim, List<String> options) {}

    private record TypeMeta(String code, String name, String emoji, String tagline) {}

    /** 一套测试：题目 / 类型目录 / 各维度两极句 / 幸运菜心情映射（按判定维度胜极字母）。 */
    private record TestDef(String displayName, List<Question> questions, Map<String, TypeMeta> types,
                           Map<String, List<String>> poleLines, List<String> dimNames,
                           List<String[]> poleLetters, Map<String, String> luckyByLetter, int luckyDimIndex) {}

    // ============ 干饭人格（eat）============

    private static final List<Question> EAT_QUESTIONS = List.of(
            new Question("楼下新开一家店，和常去的那家只能选一个？", "味觉灵魂",
                    List.of("排队也要试新店", "先看探店笔记再决定", "毫不犹豫老地方", "等风评稳定再说")),
            new Question("打开菜单第一眼，先找什么？", "味觉灵魂",
                    List.of("没吃过的菜名", "今日限定与创新档", "闭眼点的老三样", "人手一份的招牌菜")),
            new Question("刷到一道从没见过的做法，你的手是？", "味觉灵魂",
                    List.of("已经收藏三遍了", "立刻研究食材替代", "转发给会做的人", "看看就好，还是那几样香")),
            new Question("周五晚上理想中的一顿是？", "干饭场面",
                    List.of("一桌人抢菜才香", "组局涮火锅", "一个人吃到扶墙", "小锅配剧谁也别理我")),
            new Question("对火锅，你的真实态度是？", "干饭场面",
                    List.of("人多才涮得开", "鸳鸯锅也要坐大桌", "一人小火锅最治愈", "涮什么不重要，别跟我抢")),
            new Question("吃到好吃的第一反应是？", "干饭场面",
                    List.of("立刻发给饭搭子馋TA", "拍照配文发一圈", "默默多吃两口就好", "收进自己的私藏清单")),
            new Question("深夜十一点，冰箱里有蛋有面，你？", "烹饪姿态",
                    List.of("起锅烧油，说做就做", "翻出收藏的菜谱试一手", "外卖软件已经打开了", "泡面加蛋也很幸福")),
            new Question("刷到一款新厨具，你会？", "烹饪姿态",
                    List.of("手痒，感觉总能用上", "已经在比价了", "看看就好，锅还够用", "外卖都吃不过来还做什么饭")),
            new Question("做菜大翻车之后，你？", "烹饪姿态",
                    List.of("洗锅，下次再来", "认真复盘哪一步错了", "默默点了个外卖", "这厨子我是当不了了")),
            new Question("点奶茶的时候，糖度冰量？", "点菜哲学",
                    List.of("固定配方，永不更改", "按当天状态精确计算", "看心情换着来", "店员随便，都行")),
            new Question("一顿饭的荤素搭配，你有讲究吗？", "点菜哲学",
                    List.of("营养配比必须安排", "主食蛋白蔬菜缺一不可", "好吃就行，管那么多", "想吃什么什么有理")),
            new Question("被一家店伤过心之后，你？", "点菜哲学",
                    List.of("记进小本本永不回头", "差评写满三百字", "过阵子说不定就好了", "下次换道菜也许就惊艳")));

    private static final Map<String, TypeMeta> EAT_TYPES = Map.ofEntries(
            Map.entry("XRFP", new TypeMeta("XRFP", "探味灶主", "🧭", "菜单上没见过的，都要亲手试一遍")),
            Map.entry("XRFY", new TypeMeta("XRFY", "猎奇快炒侠", "⚡", "想到什么炒什么，锅气就是惊喜")),
            Map.entry("XRWP", new TypeMeta("XRWP", "探店研究员", "🔍", "没吃过的店，先做功课再进门")),
            Map.entry("XRWY", new TypeMeta("XRWY", "百味漂流家", "🛶", "外卖红点全点亮，全城都是你的菜单")),
            Map.entry("XSFP", new TypeMeta("XSFP", "独食私厨", "🍳", "一个人的饭，做起来最讲究")),
            Map.entry("XSFY", new TypeMeta("XSFY", "深夜实验厨", "🌙", "深夜的厨房，是猎奇者的实验室")),
            Map.entry("XSWP", new TypeMeta("XSWP", "独行美食家", "🎒", "一个人也要吃得清清楚楚")),
            Map.entry("XSWY", new TypeMeta("XSWY", "随缘尝鲜客", "🎲", "闭眼点单，开盲盒式干饭")),
            Map.entry("CRFP", new TypeMeta("CRFP", "老味道主理人", "🏠", "家常菜的天花板，由你亲手撑起")),
            Map.entry("CRFY", new TypeMeta("CRFY", "家传快手王", "🥘", "老三样在手，热闹桌上不慌")),
            Map.entry("CRWP", new TypeMeta("CRWP", "怀旧收藏家", "📻", "老店清单比通讯录还长")),
            Map.entry("CRWY", new TypeMeta("CRWY", "温柔守味人", "🍵", "熟悉的滋味，永远值得再点一次")),
            Map.entry("CSFP", new TypeMeta("CSFP", "一人食大厨", "🍜", "一个人的锅，煮最认真的饭")),
            Map.entry("CSFY", new TypeMeta("CSFY", "深夜泡面禅", "🛋️", "泡面加蛋，就是我的满汉全席")),
            Map.entry("CSWP", new TypeMeta("CSWP", "独食治愈家", "🥣", "点外卖也要给自己配好青菜")),
            Map.entry("CSWY", new TypeMeta("CSWY", "躺平干饭仙", "😴", "能躺着吃到，绝不起来做饭")));

    private static final Map<String, List<String>> EAT_POLE_LINES = Map.of(
            "味觉灵魂", List.of("你的舌头永远朝着没吃过的方向伸", "你的胃里有一张写满旧味道的地图"),
            "干饭场面", List.of("人多筷子才够快，抢菜是干饭的灵魂", "一个人的餐桌，是世界上最诚实的幸福"),
            "烹饪姿态", List.of("锅气是你的勋章，灶台是你的主场", "会点外卖，也是一种了不起的生活能力"),
            "点菜哲学", List.of("你的点菜清单，比论文还严谨", "随缘点菜，缘到了都是好菜"));

    // ============ 做饭人格（cook）============

    private static final List<Question> COOK_QUESTIONS = List.of(
            new Question("明天要请朋友来家里吃饭，今晚的你？", "备菜哲学",
                    List.of("列好菜单和采购单", "食材提前洗切分装", "明天醒来再说", "想到什么买什么")),
            new Question("你家冰箱里的食材，通常是？", "备菜哲学",
                    List.of("按菜单采购刚好够", "分类收纳像仓库", "有啥用啥", "经常翻出过期的")),
            new Question("开火前的灶台，是什么状态？", "备菜哲学",
                    List.of("食材调料一字排开", "按步骤预处理好", "边做边翻找", "锅热了才想起解冻")),
            new Question("做一道没做过的新菜，你会？", "口味掌控",
                    List.of("精确到克照谱执行", "先把视频完整看三遍", "谱只是参考，手比谱快", "凭感觉放调料")),
            new Question("菜谱里写的用量，你？", "口味掌控",
                    List.of("电子秤伺候", "勺子量杯安排上", "适量就是我的量", "抓一把就是一勺")),
            new Question("做到一半发现缺一味料，你？", "口味掌控",
                    List.of("出门也要买齐", "找替代方案严格对冲", "少就少吧不影响", "加点别的也许更香")),
            new Question("一道菜出锅之后，灶台？", "厨房秩序",
                    List.of("锅净台净马上擦", "边角料即时归位", "做完三道一起收拾", "战况惨烈但很快乐")),
            new Question("切菜产生的垃圾，你？", "厨房秩序",
                    List.of("顺手进垃圾桶", "台面永远只有一块砧板的活", "堆着最后一起处理", "在哪切就在哪放")),
            new Question("做饭时你家水槽的状态？", "厨房秩序",
                    List.of("基本干净随用随洗", "碗筷即时清洗", "泡着等着", "像刚打过仗")),
            new Question("做了一桌好菜，你最想？", "做饭初心",
                    List.of("发朋友圈等赞", "拍视频记录下来", "自己先吃为敬", "谁来都不给看")),
            new Question("做饭时你最享受的时刻？", "做饭初心",
                    List.of("别人夹第一筷子时的惊叹", "上桌那一刻的掌声", "独自试味的瞬间", "厨房里一个人的安静")),
            new Question("累了一天的晚上，还开火吗？", "做饭初心",
                    List.of("做道硬菜发出来犒劳自己", "精心摆盘拍张照", "煮碗面条暖暖的", "随便吃点，舒服就行")));

    private static final Map<String, TypeMeta> COOK_TYPES = Map.ofEntries(
            Map.entry("PRTG", new TypeMeta("PRTG", "厨房参谋长", "🎖️", "一场家宴，一次作战")),
            Map.entry("PRTH", new TypeMeta("PRTH", "家宴总指挥", "🏠", "菜单采购摆盘，三天前就绪")),
            Map.entry("PRBG", new TypeMeta("PRBG", "菜谱卫道士", "📕", "克普克量，一克不差")),
            Map.entry("PRBH", new TypeMeta("PRBH", "温柔执行者", "🌙", "照谱慢煮，煮给自己")),
            Map.entry("PRWG", new TypeMeta("PRWG", "豪放主帅", "🔥", "计划归计划，出锅照样浪")),
            Map.entry("PRWH", new TypeMeta("PRWH", "烟火战术家", "💥", "流程在手，战场放飞")),
            Map.entry("IRTG", new TypeMeta("IRTG", "临场抄谱员", "📖", "灵感来了，现学现卖")),
            Map.entry("IRTH", new TypeMeta("IRTH", "安分灵感家", "🫖", "小小发挥，不越雷池")),
            Map.entry("IRBG", new TypeMeta("IRBG", "即兴改造王", "🎸", "上锅三分钟，菜谱全推翻")),
            Map.entry("IRBH", new TypeMeta("IRBH", "随手煮意人", "🫰", "灵感到哪，饭到哪")),
            Map.entry("IRWG", new TypeMeta("IRWG", "野路子主厨", "🤘", "没有菜谱能活着离开我的锅")),
            Map.entry("IRWH", new TypeMeta("IRWH", "战场艺术家", "🎨", "锅气是我的画布")),
            Map.entry("PBTG", new TypeMeta("PBTG", "谋定后浪", "🌊", "看似随手，心中有谱")),
            Map.entry("PBTH", new TypeMeta("PBTH", "慢炖哲学家", "🫖", "慢一点，味道就深一点")),
            Map.entry("IBWG", new TypeMeta("IBWG", "自由风暴厨", "🌪️", "锅铲一响，规矩全忘")),
            Map.entry("IBWH", new TypeMeta("IBWH", "躺平烹饪仙", "😴", "煮熟就是胜利")));

    private static final Map<String, List<String>> COOK_POLE_LINES = Map.of(
            "备菜哲学", List.of("你的战斗在开火前就打赢了", "你的灵感只在开火后才上线"),
            "口味掌控", List.of("菜谱是你的合同，一条不改", "菜谱只是起点，锅才是你的画布"),
            "厨房秩序", List.of("你的灶台干净得像没人做过饭", "你的战场越乱，菜越香"),
            "做饭初心", List.of("掌声是你最好的调味料", "你的菜，是做给自己的拥抱"));

    // ============ 测试注册表 ============

    private static final Map<String, TestDef> TESTS = new HashMap<>();

    static {
        TESTS.put("eat", new TestDef("干饭人格", EAT_QUESTIONS, EAT_TYPES, EAT_POLE_LINES,
                List.of("味觉灵魂", "干饭场面", "烹饪姿态", "点菜哲学"),
                List.of(new String[]{"C", "X"}, new String[]{"R", "S"}, new String[]{"W", "F"}, new String[]{"Y", "P"}),
                Map.of("X", "期待", "C", "想家"), 0));
        TESTS.put("cook", new TestDef("做饭人格", COOK_QUESTIONS, COOK_TYPES, COOK_POLE_LINES,
                List.of("备菜哲学", "口味掌控", "厨房秩序", "做饭初心"),
                List.of(new String[]{"P", "I"}, new String[]{"R", "B"}, new String[]{"T", "W"}, new String[]{"G", "H"}),
                Map.of("G", "得意", "H", "满足"), 3));
    }

    private final LlmClient llm;
    private final ObjectMapper json;

    public PersonalityService(LlmClient llm, ObjectMapper json) {
        this.llm = llm;
        this.json = json;
    }

    /** AI 语义判定：LLM 判型 + 专属点评；任何失败退回确定性计分，接口永不 500。 */
    public PersonalityView analyze(String test, List<Integer> answers) {
        TestDef def = TESTS.get(test == null ? "" : test);
        if (def == null) throw new IllegalArgumentException("未知的人格测试");
        if (answers == null || answers.size() != def.questions().size()
                || answers.stream().anyMatch(a -> a == null || a < 0 || a > 3)) {
            throw new IllegalArgumentException("答案必须是 12 道、每道 0-3");
        }
        try {
            if (llm.isConfigured()) {
                PersonalityView view = analyzeWithLlm(def, answers);
                if (view != null) return view;
            }
        } catch (Exception e) {
            log.warn("人格 AI 判定失败（{}），退回确定性计分: {}", test, e.toString());
        }
        return deterministic(def, answers);
    }

    private PersonalityView analyzeWithLlm(TestDef def, List<Integer> answers) throws Exception {
        StringBuilder transcript = new StringBuilder();
        for (int i = 0; i < def.questions().size(); i++) {
            Question q = def.questions().get(i);
            transcript.append(i + 1).append(". ").append(q.text())
                    .append(" → 答：").append(q.options().get(answers.get(i))).append('\n');
        }
        String typeList = def.types().values().stream()
                .map(t -> t.code() + "=" + t.name() + "（" + t.tagline() + "）")
                .collect(Collectors.joining("；\n"));
        String prompt = """
                你是「%s」测试的判官。下面是一个人在 12 道场景题里的真实选择。
                请从给定的 16 型目录里选出最匹配的一型，并用锅仔的口吻写专属解读。

                16 型目录：
                %s

                他的选择：
                %s
                要求：
                1. typeCode 必须原样来自目录，不得自创；
                2. reading 90~150 字，口语、有温度，必须引用至少 2 个他上面的具体选择作为证据；
                3. luckyMood 必须从这个列表里选一个（开心、平静、疲惫、焦虑、难过、嘴馋、低落、想家、期待、满足、得意、害羞）；
                4. 只输出 JSON：{"typeCode":"...","reading":"...","luckyMood":"..."}，不要 Markdown。
                """.formatted(def.displayName(), typeList, transcript);
        var result = llm.complete(LlmRequest.json(
                "personality-analyze",
                "你是锅仔，一只温暖、务实、懂吃饭的小锅精灵。语气像老朋友，克制、不油腻、不自称AI。",
                prompt, 0.4, 600, TimeoutTier.STANDARD).withOpenid(null));
        if (result == null || !result.ok()) return null;
        String text = result.text() == null ? "" : result.text().replaceAll("^```(json)?|```$", "").trim();
        JsonNode node = json.readTree(text);
        String typeCode = node.path("typeCode").asText("");
        String reading = node.path("reading").asText("");
        String luckyMood = node.path("luckyMood").asText("");
        TypeMeta type = def.types().get(typeCode);
        if (type == null || reading.isBlank() || reading.length() > 400 || !LUCKY_MOODS.contains(luckyMood)) {
            return null;
        }
        return new PersonalityView(type.code(), type.name(), type.emoji(), type.tagline(), reading, luckyMood, true);
    }

    /** 确定性兜底：每题前两项 = A 极，后两项 = B 极；各维取多数极，拼两极句成解读。 */
    private PersonalityView deterministic(TestDef def, List<Integer> answers) {
        Map<String, Integer> dimIndex = new HashMap<>();
        for (int i = 0; i < def.dimNames().size(); i++) dimIndex.put(def.dimNames().get(i), i);
        int[] bCounts = new int[def.dimNames().size()];
        for (int i = 0; i < def.questions().size(); i++) {
            if (answers.get(i) >= 2) bCounts[dimIndex.get(def.questions().get(i).dim())]++;
        }
        StringBuilder code = new StringBuilder();
        StringBuilder reading = new StringBuilder();
        String winnerLuckyLetter = "";
        for (int d = 0; d < def.dimNames().size(); d++) {
            boolean toB = bCounts[d] >= 2;
            String letter = toB ? def.poleLetters().get(d)[1] : def.poleLetters().get(d)[0];
            code.append(letter);
            reading.append(def.poleLines().get(def.dimNames().get(d)).get(toB ? 1 : 0));
            if (d == def.luckyDimIndex()) winnerLuckyLetter = letter;
        }
        TypeMeta type = def.types().get(code.toString());
        if (type == null) type = def.types().values().iterator().next();
        reading.append("锅仔盖章：").append(type.tagline()).append("。");
        String luckyMood = def.luckyByLetter().getOrDefault(winnerLuckyLetter, "满足");
        return new PersonalityView(type.code(), type.name(), type.emoji(), type.tagline(), reading.toString(), luckyMood, false);
    }

    public record PersonalityView(String typeCode, String name, String emoji, String tagline,
                                  String reading, String luckyMood, boolean aiGenerated) {}
}
