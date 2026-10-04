package com.moodrecipe.backend.agent;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 输出门禁（Harness Engineering 的检测机制）。
 *
 * 模型面向用户的文本在出服务前过三道确定性检查——不靠模型自觉，靠机制：
 * 1. 内部枚举外泄（SAVE / ASK_PEOPLE 等）→ 确定性替换为中文；
 * 2. 控制字符 / 乱码 → 剔除；
 * 3. 超过两句 → 截断。
 *
 * 每次拦截都计入全局违规台账；同一类违规反复出现（达到阈值），把对应纪律条目
 * 注入之后的 decide 提示词——"每当犯一次错，就从机制上杜绝同类问题"，
 * 而不是每次都在对话里临时纠正。台账只存内存：重启清零后重新累积，它是自适应护栏，不是审计报表。
 */
@Service
public class AgentOutputGate {

    /** 同类违规达到该次数后，纪律条目自动进入提示词。 */
    public static final int DISCIPLINE_THRESHOLD = 3;

    /** 违规类型 → 注入提示词的纪律条目。 */
    private static final Map<String, String> DISCIPLINE_RULES = Map.of(
            "reply.enum", "- 回复里禁止出现内部枚举（如 SAVE、DAILY、ASK_ 开头的动作名），必须用中文表达；",
            "reply.dirty", "- 回复里禁止出现乱码、控制字符或无法展示的字符；",
            "reply.too_long", "- 回复严格控制在两句话、60 字以内；",
            "action.invalid", "- 动作必须严格从给定的动作白名单中选择，不要自创动作名；",
            "card.empty", "- card 必须给出至少一个有效的可点选项，不能只给“自己输入”；");

    private static final Map<String, String> ENUM_TEXTS = Map.ofEntries(
            Map.entry("budget", "预算"), Map.entry("healthGoal", "饮食目标"), Map.entry("mealContext", "用餐场景"),
            Map.entry("SAVE", "省钱"), Map.entry("DAILY", "日常"), Map.entry("TREAT", "丰盛"),
            Map.entry("FITNESS", "均衡"), Map.entry("LEAN", "清淡"), Map.entry("BALANCED", "均衡"),
            Map.entry("ASK_PEOPLE", "人数"), Map.entry("ASK_HOUSEHOLD", "家庭情况"), Map.entry("ASK_SPICE", "辣度"),
            Map.entry("ASK_DAYS", "做饭日期"), Map.entry("ASK_DISHES", "菜数"), Map.entry("ASK_GOAL", "饮食目标"),
            Map.entry("ASK_BUDGET", "预算"), Map.entry("ASK_CLARIFY", "确认"), Map.entry("CONFIRM_CUISINE", "菜系确认"),
            Map.entry("READY", "准备好了"));

    private final Map<String, Integer> violations = new ConcurrentHashMap<>();

    public record GateResult(String text, List<String> violationTypes) {
    }

    /** 对用户可见文本做确定性修复；发生拦截的类型同时计入台账。 */
    public GateResult fix(String reply) {
        if (reply == null || reply.isBlank()) return new GateResult(reply == null ? "" : reply, List.of());
        String text = reply;
        List<String> types = new ArrayList<>();
        String deEnummed = deEnum(text);
        if (!deEnummed.equals(text)) {
            types.add("reply.enum");
            text = deEnummed;
        }
        String cleaned = stripDirty(text);
        if (!cleaned.equals(text)) {
            types.add("reply.dirty");
            text = cleaned;
        }
        String trimmed = trimToTwoSentences(text);
        if (!trimmed.equals(text)) {
            types.add("reply.too_long");
            text = trimmed;
        }
        types.forEach(this::record);
        return new GateResult(text, List.copyOf(types));
    }

    /** 记录一次违规（供 DialogueAgent 的校验点上报：动作越界、卡片报废等）。 */
    public void record(String violationType) {
        if (violationType == null || violationType.isBlank()) return;
        violations.merge(violationType, 1, Integer::sum);
    }

    /** 反复出现的违规 → 对应纪律条目，由调用方注入 decide 提示词。 */
    public String disciplineText() {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, String> rule : DISCIPLINE_RULES.entrySet()) {
            Integer count = violations.get(rule.getKey());
            if (count != null && count >= DISCIPLINE_THRESHOLD) {
                text.append(rule.getValue()).append('\n');
            }
        }
        return text.toString().trim();
    }

    /** 内部枚举 → 中文。只命中独立词（词边界），避免误伤普通文本。 */
    private String deEnum(String text) {
        String result = text;
        for (Map.Entry<String, String> entry : ENUM_TEXTS.entrySet()) {
            result = result.replaceAll("\\b" + entry.getKey() + "\\b", entry.getValue());
        }
        return result;
    }

    /** 剔除控制字符与乱码占位符。 */
    private String stripDirty(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            if (!Character.isISOControl(codePoint) && codePoint != 0xfffd) {
                builder.appendCodePoint(codePoint);
            }
            offset += Character.charCount(codePoint);
        }
        return builder.toString();
    }

    /** 超过两句则按句子边界截断到两句，保留原语义的开头。 */
    private String trimToTwoSentences(String text) {
        String value = text.trim();
        int boundary = 0;
        int sentences = 0;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '。' || current == '！' || current == '？' || current == '；' || current == '\n') {
                sentences++;
                boundary = index + 1;
                if (sentences >= 2) break;
            }
        }
        if (sentences < 2 || boundary >= value.length()) return value;
        return value.substring(0, boundary).trim();
    }
}
