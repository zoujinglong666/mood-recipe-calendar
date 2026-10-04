package com.moodrecipe.backend.agent;

import com.moodrecipe.backend.config.AppClock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.service.AgentConversationService;
import com.moodrecipe.backend.entity.UserFoodPreference;
import com.moodrecipe.backend.repository.UserFoodPreferenceRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 备餐对话智能体。
 *
 * 与旧状态机的区别：
 * 1. 理解由模型负责，正则只做兜底 —— "我老家在抚州"也能落到赣菜并给出推断依据；
 * 2. 追问顺序不是写死的问卷，而是按"对菜单的影响程度"动态排序，已知的一律不再问；
 * 3. 能发现信息冲突（有小孩却要重辣、一个人做三道菜）并优先解决；
 * 4. 弹出什么卡片由模型决定，但选项必须落在白名单里；
 * 5. 每一轮说得出"这次用了哪些记忆、为什么降级"。
 */
@Service
public class DialogueAgent {

    private static final ZoneId CHINA_ZONE = AppClock.ZONE;

    private static final Set<String> SPICE_VALUES = Set.of("不吃辣", "微辣", "能吃辣");
    private static final Set<String> GOAL_VALUES = Set.of("FITNESS", "LEAN", "BALANCED");
    private static final Set<String> BUDGET_VALUES = Set.of("SAVE", "DAILY", "TREAT");

    public record Gap(String action, int weight, String reason) {}

    public record Conflict(String message, String suggestAction) {}

    private final LlmClient llm;
    private final AgentMemoryStore store;
    private final UserFoodPreferenceRepository preferences;
    private final AgentLearningService learning;
    private final AgentReflectionService reflection;
    private final AgentContextCompactor compactor;
    private final AgentOutputGate outputGate;
    private final AgentLoop agentLoop;
    private final ObjectMapper json;

    public DialogueAgent(LlmClient llm, AgentMemoryStore store, UserFoodPreferenceRepository preferences,
                         AgentLearningService learning, AgentReflectionService reflection,
                         AgentContextCompactor compactor, AgentOutputGate outputGate, AgentLoop agentLoop,
                         ObjectMapper json) {
        this.llm = llm;
        this.store = store;
        this.preferences = preferences;
        this.learning = learning;
        this.reflection = reflection;
        this.compactor = compactor;
        this.outputGate = outputGate;
        this.agentLoop = agentLoop;
        this.json = json;
    }

    /**
     * 单循环可用工具：模型按需自主选择，不再由代码分阶段预取。
     * 档案/记录/冲突是备餐事实；菜谱与联网是知识问答用；冰箱库存供"有什么能做"类问题。
     */
    private static final Set<String> ORCHESTRATOR_TOOLS = Set.of(
            "recall_user_profile", "check_recent_history", "check_dietary_conflicts",
            "search_recipes", "web_search", "get_fridge_inventory");
    private static final Set<String> FRIDGE_TOOLS = Set.of("get_fridge_inventory", "search_recipes");

    public DialogueState.Turn turn(String openid, String message, DialogueState.AgentState clientState) {
        return turn(openid, message, clientState, null, null);
    }

    public DialogueState.Turn turn(String openid, String message, DialogueState.AgentState clientState,
                                   String previousAction) {
        return turn(openid, message, clientState, previousAction, null);
    }

    /** 带对话历史的完整轮次：模型能看到最近往来，才能理解指代、省略，并自查重复提问。 */
    public DialogueState.Turn turn(String openid, String message, DialogueState.AgentState clientState,
                                   String previousAction, List<AgentConversationService.TranscriptMessage> history) {
        List<String> degraded = new ArrayList<>();
        String historyText = historyText(openid, history);
        String lessonsText = reflection.lessons(openid);
        UserProfile profile = store.profile(openid, AgentMemoryStore.Scene.DIALOGUE);
        String input = message == null ? "" : message.trim();
        DialogueState.AgentState incoming = clientState == null ? DialogueState.AgentState.empty() : clientState;
        List<AgentFact> heuristicFacts = HeuristicExtractor.facts(input);
        boolean startsIndependentMeal = hasMealContext(heuristicFacts)
                && (incoming.mealContext() == null || incoming.mealContext().isBlank());
        boolean independentMeal = startsIndependentMeal
                || (incoming.mealContext() != null && !incoming.mealContext().isBlank());
        DialogueState.AgentState state = startsIndependentMeal ? DialogueState.AgentState.empty()
                : independentMeal ? incoming : mergeProfile(profile, incoming);

        if (isMedicalRequest(input)) {
            List<String> skipped = independentMeal ? List.of() : profile.skipQuestions();
            state = applySkippedDefaults(state, skipped);
            String action = nextAction(state, gaps(state, skipped));
            DialogueState.Card card = AgentCards.defaultCard(action, state);
            String reply = "我不能根据疾病给出诊断、治疗或停药建议。"
                    + "如有确诊疾病、严重过敏、孕期或婴幼儿饮食，请先咨询医生或注册营养师。"
                    + "我们回到日常菜单：" + card.title() + "？";
            return new DialogueState.Turn(reply, action, state, card, "医疗请求使用固定安全边界",
                    List.of(), List.of(), List.of());
        }

        state = applyFridgeSelection(openid, input, state);
        FridgeAnswer fridgeAnswer = answerFridgeIfAsked(openid, input, state, degraded);
        if (fridgeAnswer != null) {
            return new DialogueState.Turn(fridgeAnswer.reply(), "READY", state, fridgeAnswer.card(),
                    "用户询问冰箱事实或基于冰箱推荐，已按库存回答", List.of(), List.of(), degraded);
        }

        // Minimal loop（pi 核心哲学）：一次循环内，模型自主完成「理解 → 按需查证 → 抽取事实 → 决策动作」，
        // 代码不再编排 classify → planningTools → understand → decide 的阶段顺序。
        Orchestrated orchestrated = orchestrate(openid, input, state, profile, independentMeal,
                historyText, lessonsText, outputGate.disciplineText(), degraded);

        // 知识问答：模型在循环里已调用工具并给出答案，直接返回，不再进入问卷流程。
        if ("ASK_KNOWLEDGE".equals(orchestrated.intent()) && !orchestrated.reply().isBlank()) {
            state = HeuristicExtractor.applySelection(state, input);
            String action = nextAction(state, gaps(state, List.of()));
            DialogueState.Card card = dynamicCard(action, state, input,
                    "用户刚问了一个知识问题并已得到回答，现在引导用户继续安排这一周的备餐");
            if (card == null) card = AgentCards.defaultCard(action, state);
            return new DialogueState.Turn(orchestrated.reply(), action, state, card,
                    "用户问的是知识问题，已基于菜谱库/联网作答", List.of(), List.of(), degraded);
        }

        state = HeuristicExtractor.applySelection(state, input);
        List<AgentFact> facts = mergeFacts(heuristicFacts, orchestrated.facts());
        if (facts.stream().anyMatch(fact -> "mealContext".equals(fact.key()))) {
            facts = facts.stream().filter(fact -> !"favoriteCuisine".equals(fact.key())).toList();
        }
        DialogueState.AgentState understood = applyFacts(state, facts);
        // 本轮是否真的听到了新信息：状态有实质更新才允许跳过"确认卡"，否则
        // 用户明明回答了、锅仔却说"没确认到新信息"，对话就死循环了。
        boolean learnedSomething = !understood.equals(state);
        state = understood;
        if (!independentMeal) persistFacts(openid, facts, input);

        List<String> skipQuestions = independentMeal ? List.of() : profile.skipQuestions();
        state = applySkippedDefaults(state, skipQuestions);
        List<Gap> gaps = gaps(state, skipQuestions);
        List<Conflict> conflicts = new ArrayList<>(conflicts(state));
        conflicts.addAll(parseConflicts(orchestrated.conflicts()));

        if (isCuisineAnswer(input)) {
            state = state.withCuisineConfirmed(true);
        }
        if ("CONFIRM_CUISINE".equals(nextAction(state, gaps)) && acceptsCuisine(input)) {
            state = state.withCuisineConfirmed(true);
            rememberCuisine(openid, state.favoriteCuisine());
        }

        String action = validateAction(orchestrated.action(), state, learnedSomething, gaps, conflicts, orchestrated.unclear());
        // 违规台账（Harness）：模型提了白名单外的动作 → 记录，反复出现自动升级为提示词纪律
        if (!action.equals(orchestrated.action()) && !orchestrated.action().isBlank()) {
            outputGate.record("action.invalid");
        }
        boolean repeatedQuestion = previousAction != null && previousAction.equals(action)
                && action.startsWith("ASK_") && !input.isBlank()
                && state.equals(incoming);
        if (repeatedQuestion) action = "ASK_CLARIFY";
        // 追问和准备执行都必须返回卡片：前者承载选择，后者承载唯一的生成入口。
        boolean needsCard = input.isBlank() || !conflicts.isEmpty() || !orchestrated.unclear().isEmpty()
                || action.startsWith("ASK_") || "CONFIRM_CUISINE".equals(action) || "READY".equals(action);
        // ASK_CLARIFY 且模型没写卡片描述时，把真实待确认点（例句/冲突）写进卡片，
        // 避免"选最接近的答案"配一个只有"自己输入"的空卡。
        String cardDescription = orchestrated.cardDescription();
        if ("ASK_CLARIFY".equals(action) && cardDescription.isBlank()) {
            if (!orchestrated.unclear().isEmpty()) cardDescription = orchestrated.unclear().get(0);
            else if (!conflicts.isEmpty()) cardDescription = conflicts.get(0).message();
        }
        DialogueState.Card card = needsCard ? AgentCards.accept(action, state,
                orchestrated.cardType(), orchestrated.cardTitle(), cardDescription, orchestrated.cardOptions()) : null;
        // 模型没给卡或卡里只剩"自己输入"时，让模型按当前语境现写一张贴上下文的卡；
        // 仍然失败才落到预设卡（最后降级，保证对话永远有可点的东西）。
        if (needsCard && (card == null
                || card.options().stream().allMatch(option -> "other".equals(option.value())))) {
            if (card == null) {
                // 模型给的卡片没能通过格式修剪（无有效选项）→ 记入违规台账
                outputGate.record("card.empty");
            }
            card = dynamicCard(action, state, input, questionHint(card, action, gaps, orchestrated.unclear()));
        }
        if (needsCard && card == null) {
            card = AgentCards.defaultCard("ASK_CLARIFY".equals(action) ? nextAction(state, gaps) : action, state);
            if ("ASK_CLARIFY".equals(action) && !cardDescription.isBlank()) {
                card = new DialogueState.Card(card.type(), "我想确认一下", cardDescription, card.options());
            }
        }
        // 一个轮次只展示一张决策卡；多个 unclear 合并进当前主问题，避免用户看到重复卡片。
        List<DialogueState.Card> cards = card == null ? List.of() : List.of(card);

        String selectedAction = action;
        String askReason = orchestrated.askReason().isBlank()
                ? gaps.stream().filter(gap -> gap.action().equals(selectedAction)).map(Gap::reason).findFirst().orElse("")
                : orchestrated.askReason();
        String reply = repeatedQuestion
                ? "我还没从刚才的话里确认到新的信息。请直接告诉我具体答案，也可以点“自己输入”。"
                : (orchestrated.reply().isBlank() ? fallbackReply(action, conflicts) : orchestrated.reply());
        reply = sanitizeReply(correctTodayWeekday(reply));

        List<String> memoryUsed = independentMeal ? List.of() : profile.memory().stream()
                .map(item -> item.key() + "：" + item.reason()).limit(6).toList();
        if (!independentMeal) store.reinforce(openid, profile.memory().stream().map(MemoryItem::key).toList());
        learning.recordCardShown(openid, action);
        String answeredAction = AgentCards.actionOfValue(input);
        if (answeredAction != null) {
            learning.recordCardAnswered(openid, answeredAction, true);
        }

        List<String> conflictTexts = conflicts.stream().map(Conflict::message).toList();
        List<String> followups = orchestrated.followups() == null ? List.of() : orchestrated.followups();
        return new DialogueState.Turn(reply, action, state, card, askReason,
                memoryUsed, conflictTexts, degraded, cards, followups);
    }

    private String sanitizeReply(String reply) {
        if (reply == null) return "";
        // 输出门禁（Harness 检测机制）：枚举外泄/乱码/超长先做确定性修复并计入违规台账
        String gated = outputGate.fix(reply).text();
        return gated.replaceAll("只吃一人", "只有你一人用餐")
                .replaceAll("只吃([0-9]+)人", "按$1人用餐")
                .replace("'other'", "“自己输入”")
                .replace("\"other\"", "“自己输入”");
    }

    // ---------- Minimal loop（单循环编排） ----------

    /**
     * 单循环结果：一次 orchestrate 输出的完整决策。
     * 字段级容错解析——某个字段解析失败不影响其余字段，整体失败才落 Orchestrated.empty()。
     */
    private record Orchestrated(String intent, String reply, List<AgentFact> facts, List<String> conflicts,
                                List<String> unclear, String action, String askReason, String cardType,
                                String cardTitle, String cardDescription, List<DialogueState.Option> cardOptions,
                                List<String> followups) {
        static Orchestrated empty() {
            return new Orchestrated("MEAL_INFO", "", List.of(), List.of(), List.of(),
                    "", "", "", "", "", List.of(), List.of());
        }
    }

    /**
     * Minimal loop（pi 核心哲学）：一次 agent 循环里，模型在一个上下文中自主完成
     * 「理解用户 → 按需调用工具查证 → 抽取事实 → 判定意图 → 决策动作与卡片」。
     * 代码只提供上下文、工具能力与边界（白名单/纪律），不再预取工具、不再分阶段调用。
     * 任何失败返回 Orchestrated.empty()，由 turn 的本地兜底链（heuristic + 默认卡）接管。
     */
    private Orchestrated orchestrate(String openid, String input, DialogueState.AgentState state,
                                     UserProfile profile, boolean independentMeal,
                                     String historyText, String lessonsText, String disciplineText,
                                     List<String> degraded) {
        if (input.isBlank()) return Orchestrated.empty();
        if (!llm.isConfigured()) {
            degraded.add("单循环降级：使用本地规则（模型未配置）");
            return Orchestrated.empty();
        }
        List<String> skipped = independentMeal ? List.of() : profile.skipQuestions();
        AgentLoop.Outcome outcome = agentLoop.run(new AgentLoop.RunSpec(
                "agent-orchestrate",
                AgentPrompts.orchestrate(input, jsonValue(state),
                        independentMeal ? "本次为独立宴请，不引用长期记忆" : profileText(profile),
                        gapsText(gaps(state, skipped)),
                        conflictsText(conflicts(state)),
                        String.join("、", allowedActions(state, gaps(state, skipped))),
                        historyText, lessonsText, disciplineText),
                ORCHESTRATOR_TOOLS,
                4,
                0.3,
                1200,
                TimeoutTier.STANDARD,
                new ToolContext(openid, "orchestrate-" + System.currentTimeMillis(), json)));
        if (!outcome.completed() || outcome.finalAnswer() == null || outcome.finalAnswer().isBlank()) {
            degraded.add("单循环未完成：" + (outcome.failure() == null || outcome.failure().isBlank()
                    ? "模型未给出结论" : outcome.failure()));
            return Orchestrated.empty();
        }
        try {
            JsonNode root = json.readTree(stripFence(outcome.finalAnswer()));
            if (!root.isObject()) throw new IllegalArgumentException("不是 JSON 对象");
            List<AgentFact> facts = new ArrayList<>();
            for (JsonNode item : root.path("facts")) {
                String key = item.path("key").asText("").trim();
                String value = item.path("value").asText("").trim();
                if (key.isEmpty() || value.isEmpty()) continue;
                double confidence = Math.max(0d, Math.min(item.path("confidence").asDouble(0.6), 1d));
                facts.add(new AgentFact(key, value, confidence, item.path("explicit").asBoolean(false),
                        AgentPrompts.budget(item.path("evidence").asText(""), 120)));
            }
            List<String> conflicts = new ArrayList<>();
            for (JsonNode item : root.path("conflicts")) {
                String text = item.asText("").trim();
                if (!text.isEmpty()) conflicts.add(text);
            }
            List<String> unclear = new ArrayList<>();
            for (JsonNode item : root.path("unclear")) {
                String text = item.asText("").trim();
                if (!text.isEmpty()) unclear.add(AgentPrompts.budget(text, 120));
            }
            List<DialogueState.Option> options = new ArrayList<>();
            JsonNode card = root.path("card");
            for (JsonNode item : card.path("options")) {
                String label = item.path("label").asText("").trim();
                String value = item.path("value").asText("").trim();
                if (label.isEmpty() || value.isEmpty()) continue;
                options.add(new DialogueState.Option(label, value));
            }
            List<String> followups = new ArrayList<>();
            for (JsonNode item : root.path("followups")) {
                String text = item.asText("").trim();
                if (!text.isEmpty()) followups.add(AgentPrompts.budget(text, 40));
            }
            String intent = root.path("intent").asText("MEAL_INFO").trim();
            return new Orchestrated("ASK_KNOWLEDGE".equals(intent) ? "ASK_KNOWLEDGE" : "MEAL_INFO",
                    root.path("reply").asText("").trim(), List.copyOf(facts), List.copyOf(conflicts),
                    List.copyOf(unclear), root.path("action").asText("").trim(),
                    root.path("askReason").asText("").trim(),
                    card.path("type").asText("").trim(), card.path("title").asText("").trim(),
                    card.path("description").asText("").trim(), List.copyOf(options), List.copyOf(followups));
        } catch (Exception ex) {
            degraded.add("单循环降级：模型输出无法解析为结构化结果");
            return Orchestrated.empty();
        }
    }

    private record FridgeAnswer(String reply, DialogueState.Card card) {}

    private FridgeAnswer answerFridgeIfAsked(String openid, String input,
                                             DialogueState.AgentState state, List<String> degraded) {
        if (!isFridgeQuestion(input)) return null;
        ToolContext context = new ToolContext(openid, "fridge-" + System.currentTimeMillis(), json);
        ToolResult inventory = agentLoop.executeTool("get_fridge_inventory", "{}", context);
        if (inventory.failed()) {
            degraded.add("冰箱库存查询失败");
            return new FridgeAnswer("我暂时没读到你的冰箱库存，请稍后再试。", null);
        }
        String inventoryText = inventory.outputJson();
        List<FridgeFood> foods = fridgeFoods(inventoryText);
        boolean hasExpired = foods.stream().anyMatch(food -> "EXPIRED".equals(food.status()));
        String expiryNotice = expiredNotice(foods);
        if (!isFridgeRecipeQuestion(input)) {
            return new FridgeAnswer(expiryNotice + formatFridgeInventory(inventoryText), fridgeCard(foods));
        }
        if (hasExpired) {
            return new FridgeAnswer(expiryNotice + "先不要用这些食材做菜，处理完过期食材后我再帮你推荐。", null);
        }
        AgentLoop.Outcome outcome = agentLoop.run(new AgentLoop.RunSpec(
                "agent-fridge-recipe",
                "用户问：" + AgentPrompts.budget(input, 300)
                        + "\n只能基于 get_fridge_inventory 返回的库存推荐菜谱；库存没有的食材只能标为需要补买，不能假装已有。"
                        + "先调用 get_fridge_inventory，再用 search_recipes 检索真实菜谱。回答三句话以内，简体中文。",
                FRIDGE_TOOLS, 3, 0.2, 900, TimeoutTier.STANDARD, context));
        if (outcome.completed() && outcome.finalAnswer() != null && !outcome.finalAnswer().isBlank()) {
            return new FridgeAnswer(sanitizeReply(outcome.finalAnswer()), null);
        }
        degraded.add("冰箱菜谱推荐未完成");
        return new FridgeAnswer(formatFridgeInventory(inventoryText) + "你可以继续问我：这些食材能做什么。", fridgeCard(foods));
    }

    private DialogueState.AgentState applyFridgeSelection(String openid, String input, DialogueState.AgentState state) {
        if (!"fridge_priority=soon".equals(input)) return state;
        ToolResult result = agentLoop.executeTool("get_fridge_inventory", "{}",
                new ToolContext(openid, "fridge-priority-" + System.currentTimeMillis(), json));
        if (result.failed()) return state;
        List<String> soon = fridgeFoods(result.outputJson()).stream()
                .filter(food -> "SOON".equals(food.status()))
                .map(FridgeFood::name).distinct().toList();
        return soon.isEmpty() ? state : state.withRequestedIngredients(soon);
    }

    private DialogueState.Card fridgeCard(List<FridgeFood> foods) {
        List<DialogueState.Option> options = foods.stream()
                .filter(food -> !"EXPIRED".equals(food.status()))
                .map(food -> new DialogueState.Option(
                        food.name() + ("SOON".equals(food.status()) ? " · 临期" : ""),
                        "fridge_item=" + food.name()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (foods.stream().anyMatch(food -> "SOON".equals(food.status()))) {
            options.add(new DialogueState.Option("不选食材，优先消耗临期", "fridge_priority=soon"));
        }
        return options.isEmpty() ? null : new DialogueState.Card("FRIDGE_INVENTORY", "这次想用哪些冰箱食材？",
                "可以多选；不选具体食材时，锅仔会优先安排临期食材。", options);
    }

    private record FridgeFood(String name, String status, String quantity, String unit) {}

    private List<FridgeFood> fridgeFoods(String content) {
        try {
            JsonNode items = json.readTree(content == null ? "{}" : content).path("items");
            List<FridgeFood> foods = new ArrayList<>();
            if (!items.isArray()) return foods;
            for (JsonNode item : items) {
                String name = item.path("name").asText("").trim();
                if (!name.isBlank()) foods.add(new FridgeFood(name, item.path("status").asText("NO_DATE"),
                        item.path("quantity").asText(""), item.path("unit").asText("")));
            }
            return foods;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String expiredNotice(List<FridgeFood> foods) {
        List<String> expired = foods.stream().filter(food -> "EXPIRED".equals(food.status()))
                .map(FridgeFood::name).distinct().toList();
        return expired.isEmpty() ? "" : "这些食材已经过期，请先丢弃：" + String.join("、", expired) + "。";
    }

    private boolean isFridgeQuestion(String input) {
        return input != null && input.contains("冰箱")
                && (input.contains("有什么") || input.contains("有啥") || input.contains("库存")
                || input.contains("还有什么") || input.contains("还有哪些") || input.contains("冰箱里有")
                || input.contains("查看冰箱") || input.contains("看看冰箱")
                || input.contains("能做") || input.contains("推荐"));
    }

    private boolean isFridgeRecipeQuestion(String input) {
        return input != null && (input.contains("能做") || input.contains("做什么")
                || input.contains("推荐") || input.contains("菜谱"));
    }

    private String formatFridgeInventory(String content) {
        try {
            JsonNode root = json.readTree(content == null ? "{}" : content);
            JsonNode items = root.path("items");
            if (!items.isArray() || items.isEmpty()) return "你的冰箱里暂时没有已记录的食材。";
            List<String> names = new ArrayList<>();
            for (JsonNode item : items) {
                String name = item.path("name").asText("").trim();
                String quantity = item.path("quantity").asText("").trim();
                String unit = item.path("unit").asText("").trim();
                if (!name.isBlank()) names.add(name + (quantity.isBlank() ? "" : " " + quantity + unit));
            }
            return names.isEmpty() ? "你的冰箱里暂时没有已记录的食材。" : "你冰箱里目前有：" + String.join("、", names) + "。";
        } catch (Exception ignored) {
            return "我读到了冰箱库存，但暂时没能整理成清单，请稍后再试。";
        }
    }

    private static List<AgentFact> mergeFacts(List<AgentFact> heuristic, List<AgentFact> fromModel) {
        Map<String, AgentFact> merged = new LinkedHashMap<>();
        for (AgentFact fact : heuristic) merged.put(fact.key(), fact);
        for (AgentFact fact : fromModel) {
            AgentFact existing = merged.get(fact.key());
            if (existing == null || fact.confidence() >= existing.confidence()) merged.put(fact.key(), fact);
        }
        return List.copyOf(merged.values());
    }

    private DialogueState.AgentState applyFacts(DialogueState.AgentState state,
                                                           List<AgentFact> facts) {
        for (AgentFact fact : facts) {
            String value = fact.value() == null ? "" : fact.value().trim();
            switch (fact.key()) {
                case "people" -> {
                    Integer parsed = parseInt(value);
                    if (parsed != null) state = state.withPeople(Math.max(1, Math.min(50, parsed)));
                }
                case "dishesPerDay" -> {
                    Integer parsed = parseInt(value);
                    if (parsed != null) state = state.withDishesPerDay(Math.max(1, Math.min(20, parsed)));
                }
                case "cookingDays" -> {
                    List<Integer> days = Arrays.stream(value.replaceAll("[^0-9,]", "").split(","))
                            .map(this::parseInt).filter(Objects::nonNull)
                            .filter(day -> day >= 0 && day < 7).distinct().sorted().toList();
                    if (!days.isEmpty()) state = state.withCookingDays(days);
                }
                case "spice" -> {
                    if (SPICE_VALUES.contains(value)) state = state.withSpiceLevel(value);
                }
                case "household" -> {
                    if (value.contains("老人")) state = state.withHasElder(true);
                    if (value.contains("小孩") || value.contains("孩子")) state = state.withHasChild(true);
                    if (value.contains("孕妇") || value.contains("孕期") || value.contains("怀孕")) {
                        state = state.withMealContext(appendContext(state.mealContext(), "家有孕妇"));
                    }
                    if (value.contains("都是成人") || value.contains("成人") || value.contains("没有")) state = state.withHousehold(false, false);
                }
                case "healthGoal" -> {
                    if (GOAL_VALUES.contains(value)) state = state.withHealthGoal(value);
                }
                case "budget" -> {
                    if (BUDGET_VALUES.contains(value)) state = state.withBudget(value);
                }
                case "favoriteCuisine" -> {
                    if (HeuristicExtractor.cuisines().contains(value)
                            || AgentCards.cuisineDishes().containsKey(value)) {
                        state = state.withFavoriteCuisine(value);
                    }
                }
                case "mealContext" -> state = state.withMealContext(AgentPrompts.budget(value, 200));
                case "requestedIngredients" -> state = state.withRequestedIngredients(
                        Arrays.stream(value.split("[,，、]"))
                                .map(String::trim).filter(item -> !item.isBlank()).distinct().toList());
                default -> {
                    // 未知字段一律忽略，避免污染状态
                }
            }
        }
        return state;
    }

    // ---------- 缺口与冲突 ----------

    /** 学过的事实：某个问题问了三次都没人回答，就别再问了，直接用默认值。 */
    private DialogueState.AgentState applySkippedDefaults(DialogueState.AgentState state,
                                                                     List<String> skipQuestions) {
        if (skipQuestions == null || skipQuestions.isEmpty()) return state;
        DialogueState.AgentState merged = state;
        if (skipQuestions.contains("ASK_PEOPLE") && merged.people() == null) merged = merged.withPeople(2);
        if (skipQuestions.contains("ASK_HOUSEHOLD") && merged.hasElder() == null && merged.hasChild() == null) {
            merged = merged.withHousehold(false, false);
        }
        if (skipQuestions.contains("ASK_SPICE") && merged.spiceLevel() == null) merged = merged.withSpiceLevel("微辣");
        if (skipQuestions.contains("ASK_DAYS") && (merged.cookingDays() == null || merged.cookingDays().isEmpty())) {
            merged = merged.withCookingDays(List.of(0, 1, 2, 3, 4));
        }
        if (skipQuestions.contains("ASK_DISHES") && merged.dishesPerDay() == null) {
            merged = merged.withDishesPerDay(2);
        }
        if (skipQuestions.contains("ASK_GOAL") && merged.healthGoal() == null) {
            merged = merged.withHealthGoal("BALANCED");
        }
        if (skipQuestions.contains("ASK_BUDGET") && merged.budget() == null) merged = merged.withBudget("DAILY");
        return merged;
    }

    private List<Gap> gaps(DialogueState.AgentState state, List<String> skipQuestions) {
        Set<String> skipped = skipQuestions == null ? Set.of() : Set.of(skipQuestions.toArray(new String[0]));
        List<Gap> gaps = new ArrayList<>();
        if (state.people() == null && !skipped.contains("ASK_PEOPLE")) {
            gaps.add(new Gap("ASK_PEOPLE", 100, "分量全靠人数决定"));
        }
        if (state.hasElder() == null && state.hasChild() == null && !skipped.contains("ASK_HOUSEHOLD")) {
            int weight = state.people() != null && state.people() >= 2 ? 92 : 62;
            gaps.add(new Gap("ASK_HOUSEHOLD", weight, "要照顾老人或小孩时，口感和盐度都得改"));
        }
        if (state.spiceLevel() == null && !skipped.contains("ASK_SPICE")) {
            gaps.add(new Gap("ASK_SPICE", 88, "辣度会贯穿整周菜单"));
        }
        if ((state.cookingDays() == null || state.cookingDays().isEmpty()) && !skipped.contains("ASK_DAYS")) {
            gaps.add(new Gap("ASK_DAYS", 84, "不知道哪几天开火，排不出一周"));
        }
        if (state.dishesPerDay() == null && !skipped.contains("ASK_DISHES")) {
            boolean manyPeople = state.people() != null && state.people() >= 3;
            gaps.add(new Gap("ASK_DISHES", manyPeople ? 72 : 45,
                    manyPeople ? "人多时菜数直接决定够不够吃" : "菜数影响搭配和备菜量"));
        }
        if (state.healthGoal() == null && !skipped.contains("ASK_GOAL")) {
            gaps.add(new Gap("ASK_GOAL", 35, "影响油盐和荤素搭配"));
        }
        if (state.budget() == null && !skipped.contains("ASK_BUDGET")) {
            gaps.add(new Gap("ASK_BUDGET", 30, "影响食材档次和复用方式"));
        }
        return gaps.stream().sorted(Comparator.comparingInt(Gap::weight).reversed()).toList();
    }

    /** 判断已收集的信息之间是否互相矛盾；冲突优先于继续收集。 */
    private List<Conflict> conflicts(DialogueState.AgentState state) {
        List<Conflict> conflicts = new ArrayList<>();
        boolean hot = "能吃辣".equals(state.spiceLevel());
        if (Boolean.TRUE.equals(state.hasChild()) && hot) {
            conflicts.add(new Conflict("家里有小孩，但你说能吃辣，辣味菜得分开做或降档", "ASK_SPICE"));
        }
        if (Boolean.TRUE.equals(state.hasElder()) && hot) {
            conflicts.add(new Conflict("家里有老人，但你说能吃辣，重辣菜对老人不友好", "ASK_SPICE"));
        }
        if (state.people() != null && state.dishesPerDay() != null
                && state.people() == 1 && state.dishesPerDay() >= 3) {
            conflicts.add(new Conflict("一个人做三道菜大概率会剩，建议降到两道", "ASK_DISHES"));
        }
        if (state.people() != null && state.dishesPerDay() != null
                && state.people() >= 4 && state.dishesPerDay() == 1) {
            conflicts.add(new Conflict(state.people() + " 个人只做一道菜可能不够吃", "ASK_DISHES"));
        }
        if ("LEAN".equals(state.healthGoal()) && "TREAT".equals(state.budget())) {
            conflicts.add(new Conflict("想吃得轻一点又要丰盛，优先安排低油高蛋白的硬菜", "ASK_BUDGET"));
        }
        if (Boolean.TRUE.equals(state.hasElder()) && Boolean.TRUE.equals(state.hasChild())
                && state.dishesPerDay() != null && state.dishesPerDay() < 2) {
            conflicts.add(new Conflict("同时照顾老人和小孩，一天一道菜兼顾不了两种口感", "ASK_DISHES"));
        }
        return conflicts;
    }

    private List<Conflict> parseConflicts(List<String> texts) {
        List<Conflict> conflicts = new ArrayList<>();
        for (String text : texts) {
            if (text == null || text.isBlank()) continue;
            String action = text.contains("辣") ? "ASK_SPICE"
                    : (text.contains("剩") || text.contains("道菜") || text.contains("不够")) ? "ASK_DISHES"
                    : (text.contains("预算") || text.contains("丰盛")) ? "ASK_BUDGET"
                    : text.contains("人") ? "ASK_PEOPLE" : "";
            conflicts.add(new Conflict(text.trim(), action));
        }
        return conflicts;
    }

    /** 让模型按当前语境现写一张选择卡；任何失败都返回 null，由调用方决定降级。 */
    private DialogueState.Card dynamicCard(String action, DialogueState.AgentState state,
                                           String input, String questionHint) {
        if (!llm.isConfigured() || questionHint == null || questionHint.isBlank()) return null;
        try {
            String prompt = AgentPrompts.dynamicCard(input, jsonValue(state), AgentPrompts.budget(questionHint, 200));
            LlmResult result = llm.complete(LlmRequest.json("agent-card", AgentPrompts.system(),
                    prompt, 0.4, 500, TimeoutTier.FAST));
            if (!result.ok()) return null;
            JsonNode root = json.readTree(stripFence(result.text()));
            if (!root.isObject()) return null;
            List<DialogueState.Option> options = new ArrayList<>();
            for (JsonNode item : root.path("options")) {
                String label = item.path("label").asText("").trim();
                String value = item.path("value").asText("").trim();
                if (label.isEmpty() || value.isEmpty()) continue;
                options.add(new DialogueState.Option(label, value));
            }
            return AgentCards.accept(action, state, "OPTIONS",
                    root.path("title").asText("").trim(),
                    root.path("description").asText("").trim(), options);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 卡片缺上下文时，把"此刻该问什么"讲给模型：优先已接受的卡文案，其次 unclear，再其次缺口原因。 */
    private String questionHint(DialogueState.Card accepted, String action,
                                List<Gap> gaps, List<String> unclear) {
        if (accepted != null) {
            String hint = accepted.title() + (accepted.description().isBlank() ? "" : "：" + accepted.description());
            if (!hint.isBlank()) return hint;
        }
        if (unclear != null && !unclear.isEmpty()) return unclear.get(0);
        return gaps.stream()
                .filter(gap -> gap.action().equals(action))
                .map(gap -> gap.action() + "：" + gap.reason())
                .findFirst()
                .orElseGet(() -> gaps.isEmpty()
                        ? "引导用户继续安排这一周的备餐"
                        : gaps.get(0).action() + "：" + gaps.get(0).reason());
    }

    private List<String> allowedActions(DialogueState.AgentState state, List<Gap> gaps) {
        List<String> allowed = new ArrayList<>(gaps.stream().map(Gap::action).toList());
        if (state.favoriteCuisine() != null && !state.cuisineConfirmed()) allowed.add("CONFIRM_CUISINE");
        if (gaps.isEmpty()) allowed.add("READY");
        return allowed;
    }

    private String validateAction(String proposed, DialogueState.AgentState state, boolean learnedSomething,
                                  List<Gap> gaps, List<Conflict> conflicts, List<String> unclear) {
        // 本轮状态有实质更新（用户给出了新信息）时，不再被 unclear 劫持成确认卡；
        // 只有"说了但什么都没听出来"才进入澄清，避免反复追问同一个问题。
        if (unclear != null && !unclear.isEmpty() && !learnedSomething) return "ASK_CLARIFY";
        List<String> allowed = allowedActions(state, gaps);
        if (!conflicts.isEmpty() && "READY".equals(proposed) && !gaps.isEmpty()) {
            String suggested = conflicts.get(0).suggestAction();
            if (!suggested.isBlank() && allowed.contains(suggested)) return suggested;
        }
        if (proposed != null && allowed.contains(proposed)) return proposed;
        return nextAction(state, gaps);
    }

    private String nextAction(DialogueState.AgentState state, List<Gap> gaps) {
        if (state.favoriteCuisine() != null && !state.cuisineConfirmed()) return "CONFIRM_CUISINE";
        if (!gaps.isEmpty()) return gaps.get(0).action();
        return "READY";
    }

    // ---------- 记忆 ----------

    /** 本次性表达的标记词：命中说明用户在说"这一次/这一周"，而不是改长期口味。 */
    private static final List<String> SESSION_MARKERS = List.of(
            "这次", "这回", "今天", "今晚", "这顿", "本次", "这周", "这一周", "这几天", "暂时");
    /** 会与长期偏好冲突的字段才需要分层；人数/天数等天然是场景值。 */
    private static final Set<String> SESSION_KEYS = Set.of("spice", "budget", "healthGoal");

    private void persistFacts(String openid, List<AgentFact> facts, String input) {
        String text = input == null ? "" : input.trim();
        for (AgentFact fact : facts) {
            if (!fact.worthRemembering()) continue;
            if ("mealContext".equals(fact.key())) continue;
            if ("favoriteCuisine".equals(fact.key())
                    && !HeuristicExtractor.cuisines().contains(fact.value())
                    && !AgentCards.cuisineDishes().containsKey(fact.value())) {
                continue;
            }
            // 优先级：安全 > 本次明确要求 > 长期偏好。"这次不要辣"不能永久覆盖"能吃辣"——
            // 本次约束由 agent_conversations.state_json 承载（跨轮持久、会话结束自然失效），
            // 长期记忆只存长期表达，避免单 key 记忆被临时值顶掉后无法回退。
            if (SESSION_KEYS.contains(fact.key()) && SESSION_MARKERS.stream().anyMatch(text::contains)) {
                continue;
            }
            // 枚举字段落库前必须校验，否则"丰盛"这类自由文本会经长期记忆绕过 applyFacts 的校验回流 state
            if ("spice".equals(fact.key()) && !SPICE_VALUES.contains(fact.value())) continue;
            if ("healthGoal".equals(fact.key()) && !GOAL_VALUES.contains(fact.value())) continue;
            if ("budget".equals(fact.key()) && !BUDGET_VALUES.contains(fact.value())) continue;
            store.remember(new AgentMemoryStore.RememberCommand(openid, fact.key(), fact.value(),
                    fact.confidence(),
                    fact.explicit() ? AgentMemoryStore.SRC_CHAT : AgentMemoryStore.SRC_INFERRED,
                    fact.evidence(), fact.explicit() ? null : 45));
        }
    }

    private DialogueState.AgentState mergeProfile(UserProfile profile,
                                                             DialogueState.AgentState state) {
        DialogueState.AgentState merged = state;
        if (merged.people() == null && profile.people() != null) merged = merged.withPeople(profile.people());
        if (merged.dishesPerDay() == null && profile.dishesPerDay() != null) {
            merged = merged.withDishesPerDay(profile.dishesPerDay());
        }
        if ((merged.cookingDays() == null || merged.cookingDays().isEmpty()) && profile.cookingDays() != null
                && !profile.cookingDays().isEmpty()) {
            merged = merged.withCookingDays(profile.cookingDays());
        }
        if (merged.spiceLevel() == null && profile.spiceLevel() != null) {
            merged = merged.withSpiceLevel(profile.spiceLevel());
        }
        if (merged.hasElder() == null && merged.hasChild() == null
                && (profile.hasElder() != null || profile.hasChild() != null)) {
            merged = merged.withHousehold(profile.hasElder(), profile.hasChild());
        }
        if (merged.healthGoal() == null && profile.healthGoal() != null) {
            merged = merged.withHealthGoal(profile.healthGoal());
        }
        if (merged.budget() == null && profile.budget() != null) merged = merged.withBudget(profile.budget());
        if (merged.favoriteCuisine() == null && !profile.favoriteCuisines().isEmpty()) {
            merged = merged.withFavoriteCuisine(profile.favoriteCuisines().get(0));
            merged = merged.withCuisineConfirmed(true);
        }
        return merged;
    }

    private void rememberCuisine(String openid, String cuisine) {
        if (cuisine == null || cuisine.isBlank()) return;
        store.remember(AgentMemoryStore.RememberCommand.explicit(openid,
                AgentMemoryStore.KEY_CUISINE, cuisine, "用户在对话中确认记住这个菜系"));
        UserFoodPreference preference = preferences.findByOpenid(openid).orElseGet(UserFoodPreference::new);
        preference.setOpenid(openid);
        Set<String> values = new LinkedHashSet<>(Arrays.asList(
                Optional.ofNullable(preference.getFavoriteCuisines()).orElse("").split(",")));
        values.removeIf(String::isBlank);
        values.add(cuisine);
        preference.setFavoriteCuisines(String.join(",", values));
        preferences.save(preference);
    }

    // ---------- 文案与工具 ----------

    private String profileText(UserProfile profile) {
        StringBuilder text = new StringBuilder(profile.summary()).append('\n');
        for (MemoryItem item : profile.memory().stream()
                .filter(item -> !item.key().startsWith("lesson."))
                .limit(8).toList()) {
            text.append("- ").append(item.key()).append('=').append(item.value())
                    .append("｜").append(item.reason()).append('\n');
        }
        return AgentPrompts.budget(text.toString(), 1800);
    }

    /**
     * 对话上下文：较早往来压缩成摘要（Compaction），最近 8 轮保留原文——
     * 长会话不失忆，提示词也不会被无限撑大。
     */
    private String historyText(String openid, List<AgentConversationService.TranscriptMessage> history) {
        if (history == null || history.isEmpty()) return "";
        int recentCount = Math.min(8, history.size());
        List<AgentConversationService.TranscriptMessage> recent =
                history.subList(history.size() - recentCount, history.size());
        StringBuilder text = new StringBuilder();
        String summary = compactor.compact(openid, history, recentCount);
        if (!summary.isBlank()) {
            text.append("此前对话的背景摘要：").append(summary).append('\n');
        }
        text.append("最近往来：\n");
        for (AgentConversationService.TranscriptMessage item : recent) {
            if (item == null || item.text() == null || item.text().isBlank()) continue;
            text.append("user".equals(item.role()) ? "用户：" : "锅仔：").append(item.text().trim()).append('\n');
        }
        return AgentPrompts.budget(text.toString(), 1400);
    }

    private String gapsText(List<Gap> gaps) {
        if (gaps.isEmpty()) return "（无缺口，信息已足够）";
        return gaps.stream().map(gap -> gap.action() + "（权重" + gap.weight() + "，" + gap.reason() + "）")
                .reduce((a, b) -> a + "；" + b).orElse("");
    }

    private String conflictsText(List<Conflict> conflicts) {
        if (conflicts.isEmpty()) return "（未发现冲突）";
        return conflicts.stream().map(Conflict::message).reduce((a, b) -> a + "；" + b).orElse("");
    }

    private String fallbackReply(String action, List<Conflict> conflicts) {
        if (!conflicts.isEmpty()) return conflicts.get(0).message() + "，先按这个来改？";
        return switch (action == null ? "" : action) {
            case "ASK_CLARIFY" -> "这里我还没完全确认，先问清楚再安排，避免自作主张。";
            case "CONFIRM_CUISINE" -> "我听到你的家乡味了，先确认要不要记住。";
            case "READY" -> "信息够了，我现在能替你安排这一周。";
            case "ASK_PEOPLE" -> "先定一下人数，分量才好算。";
            case "ASK_HOUSEHOLD" -> "这周要不要照顾老人或小孩？这会影响口感和盐度。";
            case "ASK_SPICE" -> "家里能吃多辣？这会影响一整周。";
            case "ASK_DAYS" -> "哪几天开火？我来排。";
            case "ASK_DISHES" -> "每天想做几道？";
            case "ASK_GOAL" -> "这阵子想吃得均衡、练得好，还是轻一点？";
            case "ASK_BUDGET" -> "预算想省一点、日常还是丰盛些？";
            default -> "我先把这条记下来，再问一个最影响菜单的问题。";
        };
    }

    private boolean isCuisineAnswer(String input) {
        return input != null && (input.equals("记住") || input.equals("暂不记住")
                || input.contains("这次尝尝") || input.contains("不用记"));
    }

    /** 模型不能自行猜今天周几；所有“今天（周X）”统一以中国时区服务端日期为准。 */
    private String correctTodayWeekday(String reply) {
        if (reply == null || reply.isBlank()) return reply;
        String weekday = weekday(AppClock.today().getDayOfWeek());
        return reply.replaceAll("今天\\s*[（(]周[一二三四五六日][）)]", "今天（" + weekday + "）")
                .replaceAll("今天是周[一二三四五六日]", "今天是" + weekday);
    }

    private String weekday(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "周一";
            case TUESDAY -> "周二";
            case WEDNESDAY -> "周三";
            case THURSDAY -> "周四";
            case FRIDAY -> "周五";
            case SATURDAY -> "周六";
            case SUNDAY -> "周日";
        };
    }

    private boolean acceptsCuisine(String input) {
        return input != null && (input.contains("记住") || input.contains("喜欢") || input.contains("要"));
    }

    private Integer parseInt(String value) {
        try {
            return value == null ? null : Integer.parseInt(value.trim());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean isMedicalRequest(String text) {
        return text != null && List.of("治疗", "诊断", "停药", "药物", "癌症", "糖尿病", "高血压", "肾病")
                .stream().anyMatch(text::contains);
    }

    private boolean hasMealContext(List<AgentFact> facts) {
        return facts.stream().anyMatch(fact -> "mealContext".equals(fact.key()));
    }

    private String jsonValue(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private String appendContext(String existing, String addition) {
        if (existing == null || existing.isBlank()) return addition;
        return existing.contains(addition) ? existing : existing + "；" + addition;
    }

    private static String stripFence(String content) {
        if (content == null) return "";
        return content.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
    }
}
