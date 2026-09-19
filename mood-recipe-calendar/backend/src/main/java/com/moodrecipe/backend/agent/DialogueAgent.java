package com.moodrecipe.backend.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private static final Set<String> SPICE_VALUES = Set.of("不吃辣", "微辣", "能吃辣");
    private static final Set<String> GOAL_VALUES = Set.of("FITNESS", "LEAN", "BALANCED");
    private static final Set<String> BUDGET_VALUES = Set.of("SAVE", "DAILY", "TREAT");

    public record Gap(String action, int weight, String reason) {}

    public record Conflict(String message, String suggestAction) {}

    private final LlmClient llm;
    private final AgentMemoryStore store;
    private final UserFoodPreferenceRepository preferences;
    private final AgentLearningService learning;
    private final ObjectMapper json;

    public DialogueAgent(LlmClient llm, AgentMemoryStore store, UserFoodPreferenceRepository preferences,
                         AgentLearningService learning, ObjectMapper json) {
        this.llm = llm;
        this.store = store;
        this.preferences = preferences;
        this.learning = learning;
        this.json = json;
    }

    public DialogueState.Turn turn(String openid, String message, DialogueState.AgentState clientState) {
        List<String> degraded = new ArrayList<>();
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

        Understanding understanding = understand(input, state, profile, independentMeal, degraded);
        state = HeuristicExtractor.applySelection(state, input);
        List<AgentFact> facts = mergeFacts(heuristicFacts, understanding.facts());
        if (facts.stream().anyMatch(fact -> "mealContext".equals(fact.key()))) {
            facts = facts.stream().filter(fact -> !"favoriteCuisine".equals(fact.key())).toList();
        }
        state = applyFacts(state, facts);
        if (!independentMeal) persistFacts(openid, facts);

        List<String> skipQuestions = independentMeal ? List.of() : profile.skipQuestions();
        state = applySkippedDefaults(state, skipQuestions);
        List<Gap> gaps = gaps(state, skipQuestions);
        List<Conflict> conflicts = new ArrayList<>(conflicts(state));
        conflicts.addAll(parseConflicts(understanding.conflicts()));

        if (isCuisineAnswer(input)) {
            state = state.withCuisineConfirmed(true);
        }
        if ("CONFIRM_CUISINE".equals(nextAction(state, gaps)) && acceptsCuisine(input)) {
            state = state.withCuisineConfirmed(true);
            rememberCuisine(openid, state.favoriteCuisine());
        }

        Decision decision = decide(input, state, profile, independentMeal, gaps, conflicts, degraded);
        String action = validateAction(decision.action(), state, gaps, conflicts);
        DialogueState.Card card = AgentCards.accept(action, state,
                decision.cardType(), decision.cardTitle(), decision.cardDescription(), decision.cardOptions());

        String askReason = decision.askReason().isBlank()
                ? gaps.stream().filter(gap -> gap.action().equals(action)).map(Gap::reason).findFirst().orElse("")
                : decision.askReason();
        String reply = decision.reply().isBlank() ? fallbackReply(action, conflicts) : decision.reply();

        List<String> memoryUsed = independentMeal ? List.of() : profile.memory().stream()
                .map(item -> item.key() + "：" + item.reason()).limit(6).toList();
        if (!independentMeal) store.reinforce(openid, profile.memory().stream().map(MemoryItem::key).toList());
        learning.recordCardShown(openid, action);
        String answeredAction = AgentCards.actionOfValue(input);
        if (answeredAction != null) {
            learning.recordCardAnswered(openid, answeredAction, true);
        }

        List<String> conflictTexts = conflicts.stream().map(Conflict::message).toList();
        return new DialogueState.Turn(reply, action, state, card, askReason,
                memoryUsed, conflictTexts, degraded);
    }

    // ---------- 理解 ----------

    private Understanding understand(String input, DialogueState.AgentState state,
                                     UserProfile profile, boolean independentMeal, List<String> degraded) {
        if (input.isBlank()) return Understanding.empty();
        if (!llm.isConfigured()) {
            degraded.add("理解环节降级：使用本地规则（模型未配置）");
            return Understanding.empty();
        }
        String prompt = AgentPrompts.understand(input, jsonValue(state),
                independentMeal ? "本次为独立宴请，不引用长期记忆" : profileText(profile));
        LlmResult result = llm.complete(LlmRequest.json("agent-understand", AgentPrompts.system(),
                prompt, 0.2, 700, TimeoutTier.FAST));
        if (!result.ok()) {
            degraded.add("理解环节降级：" + result.reason());
            return Understanding.empty();
        }
        try {
            JsonNode root = json.readTree(stripFence(result.text()));
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
            return new Understanding(root.path("reply").asText("").trim(), facts, conflicts);
        } catch (Exception ex) {
            degraded.add("理解环节降级：模型输出无法解析为结构化结果");
            return Understanding.empty();
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
                    if (value.contains("都是成人") || value.contains("没有")) state = state.withHousehold(false, false);
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

    // ---------- 决策 ----------

    private Decision decide(String input, DialogueState.AgentState state, UserProfile profile, boolean independentMeal,
                            List<Gap> gaps, List<Conflict> conflicts, List<String> degraded) {
        List<String> allowed = allowedActions(state, gaps);
        if (!llm.isConfigured()) {
            degraded.add("决策环节降级：使用本地排序（模型未配置）");
            return Decision.empty();
        }
        String prompt = AgentPrompts.decide(input, jsonValue(state),
                independentMeal ? "本次为独立宴请，不引用长期记忆" : profileText(profile),
                gapsText(gaps), conflictsText(conflicts), String.join("、", allowed));
        LlmResult result = llm.complete(LlmRequest.json("agent-decide", AgentPrompts.system(),
                prompt, 0.4, 700, TimeoutTier.FAST));
        if (!result.ok()) {
            degraded.add("决策环节降级：" + result.reason());
            return Decision.empty();
        }
        try {
            JsonNode root = json.readTree(stripFence(result.text()));
            if (!root.isObject()) throw new IllegalArgumentException("不是 JSON 对象");
            List<DialogueState.Option> options = new ArrayList<>();
            JsonNode card = root.path("card");
            for (JsonNode item : card.path("options")) {
                String label = item.path("label").asText("").trim();
                String value = item.path("value").asText("").trim();
                if (label.isEmpty() || value.isEmpty()) continue;
                options.add(new DialogueState.Option(label, value));
            }
            return new Decision(root.path("action").asText("").trim(),
                    root.path("reply").asText("").trim(),
                    root.path("askReason").asText("").trim(),
                    card.path("type").asText("").trim(),
                    card.path("title").asText("").trim(),
                    card.path("description").asText("").trim(),
                    options);
        } catch (Exception ex) {
            degraded.add("决策环节降级：模型输出无法解析为结构化结果");
            return Decision.empty();
        }
    }

    private List<String> allowedActions(DialogueState.AgentState state, List<Gap> gaps) {
        List<String> allowed = new ArrayList<>(gaps.stream().map(Gap::action).toList());
        if (state.favoriteCuisine() != null && !state.cuisineConfirmed()) allowed.add("CONFIRM_CUISINE");
        if (gaps.isEmpty()) allowed.add("READY");
        return allowed;
    }

    private String validateAction(String proposed, DialogueState.AgentState state,
                                  List<Gap> gaps, List<Conflict> conflicts) {
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

    private void persistFacts(String openid, List<AgentFact> facts) {
        for (AgentFact fact : facts) {
            if (!fact.worthRemembering()) continue;
            if ("mealContext".equals(fact.key())) continue;
            if ("favoriteCuisine".equals(fact.key())
                    && !HeuristicExtractor.cuisines().contains(fact.value())
                    && !AgentCards.cuisineDishes().containsKey(fact.value())) {
                continue;
            }
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
        for (MemoryItem item : profile.memory().stream().limit(8).toList()) {
            text.append("- ").append(item.key()).append('=').append(item.value())
                    .append("｜").append(item.reason()).append('\n');
        }
        return AgentPrompts.budget(text.toString(), 1800);
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

    private static String stripFence(String content) {
        if (content == null) return "";
        return content.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
    }

    private record Understanding(String reply, List<AgentFact> facts, List<String> conflicts) {
        static Understanding empty() {
            return new Understanding("", List.of(), List.of());
        }
    }

    private record Decision(String action, String reply, String askReason, String cardType,
                            String cardTitle, String cardDescription,
                            List<DialogueState.Option> cardOptions) {
        static Decision empty() {
            return new Decision("", "", "", "", "", "", List.of());
        }
    }
}
