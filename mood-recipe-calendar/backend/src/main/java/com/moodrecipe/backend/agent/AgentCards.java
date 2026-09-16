package com.moodrecipe.backend.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * 卡片目录：动作白名单、候选值白名单、默认卡片。
 *
 * 模型可以决定"此刻出什么卡、怎么写文案、给哪几个选项"，
 * 但选项的 value 必须落在白名单里，否则服务端用默认卡兜底。
 */
public final class AgentCards {

    public static final Set<String> ACTIONS = Set.of(
            "ASK_PEOPLE", "ASK_HOUSEHOLD", "ASK_SPICE", "ASK_DAYS", "ASK_DISHES",
            "ASK_GOAL", "ASK_BUDGET", "CONFIRM_CUISINE", "READY");

    private static final Map<String, List<String>> CUISINE_DISHES = Map.of(
            "赣菜", List.of("宁都三杯鸡", "莲花血鸭", "藜蒿炒腊肉"),
            "川菜", List.of("宫保鸡丁", "鱼香肉丝", "麻婆豆腐"),
            "湘菜", List.of("小炒黄牛肉", "剁椒鱼头", "农家一碗香"),
            "粤菜", List.of("白切鸡", "豉汁蒸排骨", "白灼菜心"));

    private AgentCards() {
    }

    public static List<String> allowedValues(String action) {
        return switch (action == null ? "" : action) {
            case "ASK_PEOPLE" -> IntStream.rangeClosed(1, 50).mapToObj(value -> "people=" + value).toList();
            case "ASK_HOUSEHOLD" -> List.of("elder=yes", "child=yes", "household=none");
            case "ASK_SPICE" -> List.of("spice=不吃辣", "spice=微辣", "spice=能吃辣");
            case "ASK_DAYS" -> List.of("days=0,1,2,3,4,5,6", "days=0,1,2,3,4", "days=5,6");
            case "ASK_DISHES" -> IntStream.rangeClosed(1, 20).mapToObj(value -> "dishes=" + value).toList();
            case "ASK_GOAL" -> List.of("goal=BALANCED", "goal=FITNESS", "goal=LEAN");
            case "ASK_BUDGET" -> List.of("budget=SAVE", "budget=DAILY", "budget=TREAT");
            case "CONFIRM_CUISINE" -> List.of("记住", "暂不记住");
            case "READY" -> List.of("generate");
            default -> List.of();
        };
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
        if (text.equals("elder=yes") || text.equals("child=yes") || text.equals("household=none")) {
            return "ASK_HOUSEHOLD";
        }
        if (text.startsWith("spice=")) return "ASK_SPICE";
        if (text.startsWith("days=")) return "ASK_DAYS";
        if (text.startsWith("dishes=")) return "ASK_DISHES";
        if (text.startsWith("goal=")) return "ASK_GOAL";
        if (text.startsWith("budget=")) return "ASK_BUDGET";
        return null;
    }

    public static DialogueState.Card defaultCard(String action, DialogueState.AgentState state) {
        return switch (action == null ? "" : action) {
            case "ASK_PEOPLE" -> options("一起吃饭的人数", "也可以直接输入具体人数",
                    "people=1", "1 人", "people=2", "2 人", "people=3", "3 人", "people=4", "4 人",
                    "people=6", "6 人", "people=8", "8 人");
            case "ASK_HOUSEHOLD" -> options("这周要照顾谁？", "会影响口感、盐度和食材处理",
                    "elder=yes", "有老人", "child=yes", "有小孩", "household=none", "都是成人");
            case "ASK_SPICE" -> options("家里平时能吃多辣？", "我会贯穿整周菜单",
                    "spice=不吃辣", "不吃辣", "spice=微辣", "微辣", "spice=能吃辣", "能吃辣");
            case "ASK_DAYS" -> options("哪几天开火？", "可一次选择多个日期",
                    "days=0,1,2,3,4,5,6", "周一到周日", "days=0,1,2,3,4", "工作日", "days=5,6", "周末");
            case "ASK_DISHES" -> dishOptions(state);
            case "ASK_GOAL" -> options("这阵子想怎么吃？", "锅仔会调整搭配和做法",
                    "goal=BALANCED", "均衡吃", "goal=FITNESS", "练得好", "goal=LEAN", "轻一点");
            case "ASK_BUDGET" -> options("预算想怎么安排？", "会影响食材和复用方式",
                    "budget=SAVE", "省一点", "budget=DAILY", "日常吃", "budget=TREAT", "丰盛些");
            case "CONFIRM_CUISINE" -> new DialogueState.Card("CUISINE",
                    (state.favoriteCuisine() == null ? "这个" : state.favoriteCuisine()) + "风味要记住吗？",
                    String.join(" · ", CUISINE_DISHES.getOrDefault(state.favoriteCuisine(), List.of())),
                    List.of(new DialogueState.Option("记住" + state.favoriteCuisine(), "记住"),
                            new DialogueState.Option("这次尝尝", "暂不记住")));
            default -> new DialogueState.Card("READY", "锅仔已经收齐信息",
                    "现在生成菜单、买菜清单和做法", List.of(new DialogueState.Option("开始安排", "generate")));
        };
    }

    /** 模型给的卡片只有在类型和选项值都合法时才被采纳，否则退回默认卡。 */
    public static DialogueState.Card accept(String action,
                                                       DialogueState.AgentState state,
                                                       String type, String title, String description,
                                                       List<DialogueState.Option> options) {
        List<String> allowed = allowedValues(action);
        if (options == null || options.isEmpty() || allowed.isEmpty()) {
            return defaultCard(action, state);
        }
        if (type == null || !(type.equals("OPTIONS") || type.equals("CUISINE") || type.equals("READY"))) {
            return defaultCard(action, state);
        }
        for (DialogueState.Option option : options) {
            if (option == null || option.value() == null || !allowed.contains(option.value())) {
                return defaultCard(action, state);
            }
        }
        if (title == null || title.isBlank()) return defaultCard(action, state);
        String safeDescription = description == null ? "" : description;
        return new DialogueState.Card(type, title, safeDescription,
                options.stream().limit(4).toList());
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
                "dishes=1", "1 道", "dishes=2", "2 道", "dishes=3", "3 道");
    }

    public static Map<String, List<String>> cuisineDishes() {
        return new LinkedHashMap<>(CUISINE_DISHES);
    }
}
