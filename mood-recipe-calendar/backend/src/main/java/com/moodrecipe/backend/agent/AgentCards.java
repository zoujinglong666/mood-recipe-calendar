package com.moodrecipe.backend.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 卡片目录：动作白名单、候选值白名单、默认卡片。
 *
 * 模型可以决定"此刻出什么卡、怎么写文案、给哪几个选项"。
 * 固定卡片只作为模型不可用或输出异常时的兜底，正常选项不再受预设白名单限制。
 */
public final class AgentCards {

    public static final Set<String> ACTIONS = Set.of(
            "ASK_PEOPLE", "ASK_HOUSEHOLD", "ASK_SPICE", "ASK_DAYS", "ASK_DISHES",
            "ASK_GOAL", "ASK_BUDGET", "ASK_CLARIFY", "CONFIRM_CUISINE", "READY");

    private static final Map<String, List<String>> CUISINE_DISHES = Map.of(
            "赣菜", List.of("宁都三杯鸡", "莲花血鸭", "藜蒿炒腊肉"),
            "川菜", List.of("宫保鸡丁", "鱼香肉丝", "麻婆豆腐"),
            "湘菜", List.of("小炒黄牛肉", "剁椒鱼头", "农家一碗香"),
            "粤菜", List.of("白切鸡", "豉汁蒸排骨", "白灼菜心"));

    private AgentCards() {
    }

    /** 反查一个选项值属于哪张卡，用于把"用户点了这个选项"归因到具体动作上。 */
    public static String actionOfValue(String value) {
        if (value == null || value.isBlank()) return null;
        String text = value.trim();
        return switch (text) {
            case "记住", "暂不记住" -> "CONFIRM_CUISINE";
            case "generate" -> "READY";
            default -> prefix(text);
        };
    }

    private static String prefix(String text) {
        if (text.startsWith("people=")) return "ASK_PEOPLE";
        if (text.equals("elder=yes") || text.equals("child=yes") || text.equals("pregnant=yes") || text.startsWith("household=")) {
            return "ASK_HOUSEHOLD";
        }
        if (text.startsWith("spice=")) return "ASK_SPICE";
        if (text.startsWith("days=")) return "ASK_DAYS";
        if (text.startsWith("dishes=")) return "ASK_DISHES";
        if (text.startsWith("goal=")) return "ASK_GOAL";
        if (text.startsWith("budget=")) return "ASK_BUDGET";
        return null;
    }

    /** 最后降级：仅在模型不可用或输出完全无法解析时使用，正常路径的卡片一律由模型现写。 */
    public static DialogueState.Card defaultCard(String action, DialogueState.AgentState state) {
        DialogueState.Card card = switch (action == null ? "" : action) {
            case "ASK_PEOPLE" -> options("一起吃饭的人数", "也可以直接输入具体人数",
                    "people=1", "1 人", "people=2", "2 人", "people=3", "3 人", "people=4", "4 人",
                    "people=6", "6 人", "people=8", "8 人");
            case "ASK_HOUSEHOLD" -> options("家里这顿有谁一起吃？", "可多选，孕妇请直接告诉锅仔，方便避开不合适的食材",
                    "household=elder", "有老人", "household=child", "有小孩", "household=pregnant", "有孕妇", "household=adult", "都是成人");
            case "ASK_SPICE" -> options("家里平时能吃多辣？", "我会贯穿整周菜单",
                    "spice=不吃辣", "不吃辣", "spice=微辣", "微辣", "spice=能吃辣", "能吃辣");
            case "ASK_DAYS" -> options("哪几天开火？", "可一次选择多个日期",
                    "days=0,1,2,3,4,5,6", "周一到周日", "days=0,1,2,3,4", "工作日", "days=5,6", "周末");
            case "ASK_DISHES" -> dishOptions(state);
            case "ASK_GOAL" -> options("这阵子想怎么吃？", "锅仔会调整搭配和做法",
                    "goal=BALANCED", "均衡吃", "goal=FITNESS", "练得好", "goal=LEAN", "轻一点");
            case "ASK_BUDGET" -> options("预算想怎么安排？", "会影响食材和复用方式",
                    "budget=SAVE", "省一点", "budget=DAILY", "日常吃", "budget=TREAT", "丰盛些");
            case "ASK_CLARIFY" -> new DialogueState.Card("OPTIONS", "我想确认一下", "直接告诉锅仔你的想法",
                    List.of(new DialogueState.Option("自己输入", "other")));
            case "CONFIRM_CUISINE" -> new DialogueState.Card("CUISINE",
                    (state.favoriteCuisine() == null ? "这个" : state.favoriteCuisine()) + "风味要记住吗？",
                    String.join(" · ", CUISINE_DISHES.getOrDefault(state.favoriteCuisine(), List.of())),
                    List.of(new DialogueState.Option("记住" + state.favoriteCuisine(), "记住"),
                            new DialogueState.Option("这次尝尝", "暂不记住")));
            default -> new DialogueState.Card("READY", "锅仔已经收齐信息",
                    "现在生成菜单、买菜清单和做法", List.of(new DialogueState.Option("开始安排", "generate")));
        };
        if ("READY".equals(action)) return card;
        List<DialogueState.Option> options = new ArrayList<>(card.options());
        if (options.stream().noneMatch(option -> "other".equals(option.value()))) {
            options.add(new DialogueState.Option("自己输入", "other"));
        }
        return new DialogueState.Card(card.type(), card.title(), card.description(), List.copyOf(options));
    }

    /**
     * 模型给的卡片只做格式修剪，不再按预设白名单打回——选项内容交给模型按语境现写。
     * 修剪后没有有效选项（空、或只剩"自己输入"）时返回 null，由调用方决定
     * 让模型重新生成还是走最后降级。类型/标题不合规时归一或截断，尽量保留模型的原意。
     */
    public static DialogueState.Card accept(String action,
                                            DialogueState.AgentState state,
                                            String type, String title, String description,
                                            List<DialogueState.Option> options) {
        List<DialogueState.Option> meaningful = new ArrayList<>();
        DialogueState.Option other = null;
        if (options != null) {
            for (DialogueState.Option option : options) {
                if (option == null) continue;
                String label = trimDisplayable(option.label(), 40);
                String value = trimDisplayable(option.value(), 80);
                if (label == null || value == null) continue;
                if ("other".equals(value)) {
                    other = new DialogueState.Option(label, value);
                    continue;
                }
                if (meaningful.stream().noneMatch(existing -> existing.value().equals(value))) {
                    meaningful.add(new DialogueState.Option(label, value));
                }
            }
        }
        if (meaningful.isEmpty()) return null;
        List<DialogueState.Option> safeOptions = new ArrayList<>(meaningful.subList(0, Math.min(meaningful.size(), 5)));
        String safeType = switch (action == null ? "" : action) {
            case "CONFIRM_CUISINE" -> "CUISINE";
            case "READY" -> "READY";
            default -> "OPTIONS";
        };
        if (!"READY".equals(action)) {
            safeOptions.add(other != null ? other : new DialogueState.Option("自己输入", "other"));
        }
        String safeTitle = trimDisplayable(title, 60);
        if (safeTitle == null) safeTitle = trimDisplayable(description, 60);
        if (safeTitle == null) return null;
        String safeDescription = trimDisplayable(description, 160);
        return new DialogueState.Card(safeType, safeTitle, safeDescription == null ? "" : safeDescription,
                List.copyOf(safeOptions));
    }

    /** 可展示文本：去首尾空白、截断超长、剔除控制字符；空或含坏字符返回 null。 */
    private static String trimDisplayable(String value, int maxLength) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.length() > maxLength) trimmed = trimmed.substring(0, maxLength);
        for (int offset = 0; offset < trimmed.length();) {
            int codePoint = trimmed.codePointAt(offset);
            if (Character.isISOControl(codePoint) || codePoint == 0xfffd) {
                return null;
            }
            offset += Character.charCount(codePoint);
        }
        return trimmed;
    }

    private static DialogueState.Card options(String title, String description, String... pairs) {
        List<DialogueState.Option> options = new ArrayList<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            options.add(new DialogueState.Option(pairs[i + 1], pairs[i]));
        }
        return new DialogueState.Card("OPTIONS", title, description, options);
    }

    private static DialogueState.Card dishOptions(DialogueState.AgentState state) {
        int people = state.people() == null ? 2 : state.people();
        if (people >= 8) {
            return options("这桌准备几道菜？", "按宴席规模给你几个合适档位",
                    "dishes=6", "6 道", "dishes=8", "8 道", "dishes=9", "9 道", "dishes=10", "10 道");
        }
        if (people >= 5) {
            return options("这桌准备几道菜？", "锅仔会搭配主菜、蔬菜和汤",
                    "dishes=4", "4 道", "dishes=6", "6 道", "dishes=8", "8 道");
        }
        return options("每天想吃几道？", "锅仔会按人数搭配主菜和配菜",
                "dishes=1", "1 道", "dishes=2", "2 道");
    }

    public static Map<String, List<String>> cuisineDishes() {
        return new LinkedHashMap<>(CUISINE_DISHES);
    }
}
