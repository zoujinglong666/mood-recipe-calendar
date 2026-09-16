package com.moodrecipe.backend.agent;

import com.moodrecipe.backend.entity.PlanDishOutcome;
import com.moodrecipe.backend.entity.RecipeInteraction;
import com.moodrecipe.backend.entity.UserRecord;
import com.moodrecipe.backend.repository.PlanDishOutcomeRepository;
import com.moodrecipe.backend.repository.RecipeInteractionRepository;
import com.moodrecipe.backend.repository.RecipeRepository;
import com.moodrecipe.backend.repository.UserRecordRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 反馈学习：把真实结果回流成策略，而不只是把评分存起来。
 * 学到的东西会改变下一次行为：菜系亲和、难度上限、少排哪些菜、哪些问题别再问、哪类卡片更有效。
 */
@Service
public class AgentLearningService {

    public static final String KEY_AVOID_DISHES = "dish.avoid";
    private static final String CARD_PREFIX = "strategy.card.";
    private static final int UNANSWERED_THRESHOLD = 3;

    public record OutcomeInput(Long planId, int dayIndex, int dishIndex, String dishName,
                               Boolean cooked, Boolean leftover, Boolean tooHard) {}

    public record StrategyHints(Set<String> skipQuestions, Set<String> preferQuickOptions,
                                Map<String, Double> cuisineAffinity, Integer maxCookingMinutes,
                                boolean preferSimple, List<String> avoidDishes) {}

    private final PlanDishOutcomeRepository outcomes;
    private final AgentMemoryStore store;
    private final UserRecordRepository records;
    private final RecipeInteractionRepository interactions;
    private final RecipeRepository recipes;

    public AgentLearningService(PlanDishOutcomeRepository outcomes, AgentMemoryStore store,
                                UserRecordRepository records, RecipeInteractionRepository interactions,
                                RecipeRepository recipes) {
        this.outcomes = outcomes;
        this.store = store;
        this.records = records;
        this.interactions = interactions;
        this.recipes = recipes;
    }

    public void recordCardShown(String openid, String action) {
        if (openid == null || action == null || action.isBlank()) return;
        bump(openid, CARD_PREFIX + action, "shown");
    }

    public void recordCardAnswered(String openid, String action, boolean viaQuickOption) {
        if (openid == null || action == null || action.isBlank()) return;
        bump(openid, CARD_PREFIX + action, viaQuickOption ? "answered" : "typed");
    }

    /** 记录周菜单的执行结果：这道菜到底做了吗、剩了吗、太难了吗。 */
    public PlanDishOutcome recordOutcome(String openid, OutcomeInput input) {
        PlanDishOutcome outcome = outcomes.findByOpenidAndPlanIdAndDayIndexAndDishIndex(
                openid, input.planId(), input.dayIndex(), input.dishIndex()).orElseGet(PlanDishOutcome::new);
        outcome.setOpenid(openid);
        outcome.setPlanId(input.planId());
        outcome.setDayIndex(input.dayIndex());
        outcome.setDishIndex(input.dishIndex());
        outcome.setDishName(input.dishName());
        if (input.cooked() != null) outcome.setCooked(input.cooked());
        if (input.leftover() != null) outcome.setLeftover(input.leftover());
        if (input.tooHard() != null) outcome.setTooHard(input.tooHard());
        PlanDishOutcome saved = outcomes.save(outcome);
        refresh(openid);
        return saved;
    }

    public void refresh(String openid) {
        Map<String, Double> scores = new LinkedHashMap<>();
        List<WeightedDish> dishes = signals(openid);
        for (String cuisine : HeuristicExtractor.cuisines()) {
            double positive = dishes.stream()
                    .filter(dish -> dish.name != null && dish.name.contains(cuisine))
                    .mapToDouble(dish -> dish.weight).sum();
            if (positive > 0) scores.put(cuisine, positive);
        }
        double best = scores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0d);
        scores.forEach((cuisine, value) -> store.remember(new AgentMemoryStore.RememberCommand(openid,
                AgentMemoryStore.AFFINITY_PREFIX + cuisine,
                String.valueOf(Math.round(value / best * 100d) / 100d), 0.7, AgentMemoryStore.SRC_LEARNED,
                "根据最近 60 天的记录与反馈统计", null)));

        List<PlanDishOutcome> history = outcomes.findTop50ByOpenidOrderByUpdatedAtDesc(openid);
        long tooHard = history.stream().filter(item -> Boolean.TRUE.equals(item.getTooHard())).count();
        long leftover = history.stream().filter(item -> Boolean.TRUE.equals(item.getLeftover())).count();
        if (tooHard >= 2) {
            store.remember(AgentMemoryStore.RememberCommand.learned(openid, AgentMemoryStore.KEY_SIMPLE,
                    "true", "有 " + tooHard + " 道菜被反馈太难做"));
            store.remember(AgentMemoryStore.RememberCommand.learned(openid, AgentMemoryStore.KEY_MAX_MINUTES,
                    "35", "有 " + tooHard + " 道菜被反馈太难做，收紧时长上限"));
        }
        if (leftover >= 2) {
            store.remember(AgentMemoryStore.RememberCommand.learned(openid, "preference.avoidLeftover",
                    "true", "有 " + leftover + " 次反馈菜做多了"));
        }
        Map<String, Integer> notCooked = new LinkedHashMap<>();
        history.stream().filter(item -> Boolean.FALSE.equals(item.getCooked()))
                .forEach(item -> notCooked.merge(item.getDishName(), 1, Integer::sum));
        List<String> avoid = notCooked.entrySet().stream()
                .filter(entry -> entry.getValue() >= 2).map(Map.Entry::getKey).toList();
        if (!avoid.isEmpty()) {
            store.remember(AgentMemoryStore.RememberCommand.learned(openid, KEY_AVOID_DISHES,
                    String.join(",", avoid), "这些菜被反馈没做，累计 " + avoid.size() + " 道"));
        }
        refreshQuestionPolicy(openid);
    }

    public StrategyHints hints(String openid) {
        Set<String> skip = new LinkedHashSet<>();
        Set<String> quick = new LinkedHashSet<>();
        Map<String, Double> affinity = new LinkedHashMap<>();
        Integer maxMinutes = null;
        boolean preferSimple = false;
        List<String> avoidDishes = new ArrayList<>();
        for (MemoryItem item : store.recall(openid, AgentMemoryStore.Scene.WEEKLY_PLAN, 60)) {
            String key = item.key();
            if (key.startsWith(AgentMemoryStore.AFFINITY_PREFIX)) {
                affinity.put(key.substring(AgentMemoryStore.AFFINITY_PREFIX.length()), parseDouble(item.value()));
            } else if (key.startsWith(CARD_PREFIX)) {
                String action = key.substring(CARD_PREFIX.length());
                int shown = readCounter(item.value(), "shown");
                int answered = readCounter(item.value(), "answered");
                if (shown >= UNANSWERED_THRESHOLD && answered == 0) skip.add(action);
                if (shown >= 2 && answered * 1.0 / shown >= 0.6) quick.add(action);
            } else if (AgentMemoryStore.KEY_MAX_MINUTES.equals(key)) {
                maxMinutes = parseInt(item.value());
            } else if (AgentMemoryStore.KEY_SIMPLE.equals(key)) {
                preferSimple = "true".equalsIgnoreCase(item.value());
            } else if (KEY_AVOID_DISHES.equals(key)) {
                avoidDishes = List.of(item.value().split(","));
            }
        }
        return new StrategyHints(skip, quick, affinity, maxMinutes, preferSimple, avoidDishes);
    }

    private void refreshQuestionPolicy(String openid) {
        for (MemoryItem item : store.recall(openid, AgentMemoryStore.Scene.WEEKLY_PLAN, 60)) {
            if (!item.key().startsWith(CARD_PREFIX)) continue;
            int shown = readCounter(item.value(), "shown");
            int answered = readCounter(item.value(), "answered");
            if (shown < UNANSWERED_THRESHOLD || answered > 0) continue;
            String action = item.key().substring(CARD_PREFIX.length());
            List<String> skip = new ArrayList<>(currentSkipList(openid));
            if (!skip.contains(action)) {
                skip.add(action);
                store.remember(AgentMemoryStore.RememberCommand.learned(openid,
                        AgentMemoryStore.KEY_SKIP_QUESTIONS, String.join(",", skip),
                        "这个问题问了 " + shown + " 次都没被回答，下次直接用默认值"));
            }
        }
    }

    private List<String> currentSkipList(String openid) {
        for (MemoryItem item : store.recall(openid, AgentMemoryStore.Scene.WEEKLY_PLAN, 60)) {
            if (AgentMemoryStore.KEY_SKIP_QUESTIONS.equals(item.key())) {
                return new ArrayList<>(List.of(item.value().split(",")));
            }
        }
        return new ArrayList<>();
    }

    private void bump(String openid, String key, String field) {
        String existingValue = "";
        for (MemoryItem item : store.recall(openid, AgentMemoryStore.Scene.WEEKLY_PLAN, 60)) {
            if (item.key().equals(key)) existingValue = item.value();
        }
        Map<String, Integer> counters = new LinkedHashMap<>();
        counters.put("shown", readCounter(existingValue, "shown"));
        counters.put("answered", readCounter(existingValue, "answered"));
        counters.put("typed", readCounter(existingValue, "typed"));
        counters.merge(field, 1, Integer::sum);
        store.remember(new AgentMemoryStore.RememberCommand(openid, key,
                "shown=" + counters.get("shown") + ";answered=" + counters.get("answered")
                        + ";typed=" + counters.getOrDefault("typed", 0),
                0.9, AgentMemoryStore.SRC_LEARNED, "卡片交互统计", null));
    }

    private List<WeightedDish> signals(String openid) {
        List<WeightedDish> dishes = new ArrayList<>();
        LocalDate from = LocalDate.now().minusDays(60);
        for (UserRecord record : records.findTop30ByOpenidOrderByCreatedAtDesc(openid)) {
            if (record.getDishName() == null || record.getDishName().isBlank()) continue;
            if (record.getRecordDate() == null || LocalDate.parse(record.getRecordDate()).isBefore(from)) continue;
            dishes.add(new WeightedDish(record.getDishName(), 3d));
        }
        var history = interactions.findTop30ByOpenidOrderByCreatedAtDesc(openid);
        Set<Long> ids = new LinkedHashSet<>();
        history.forEach(item -> {
            if (item.getRecipeId() != null) ids.add(item.getRecipeId());
        });
        Map<Long, String> names = new HashMap<>();
        if (!ids.isEmpty()) {
            recipes.findAllById(ids).forEach(recipe -> {
                if (recipe.getName() != null) names.put(recipe.getId(), recipe.getName());
            });
        }
        for (RecipeInteraction item : history) {
            String name = names.get(item.getRecipeId());
            if (name == null || name.isBlank()) continue;
            double weight = switch (item.getAction() == null ? "" : item.getAction()) {
                case "LIKE" -> 2d;
                case "MADE" -> 1.5d;
                case "DISLIKE" -> -3d;
                default -> 0d;
            };
            if (weight != 0d) dishes.add(new WeightedDish(name, weight));
        }
        outcomes.findTop50ByOpenidOrderByUpdatedAtDesc(openid).forEach(item -> {
            if (item.getDishName() == null || item.getDishName().isBlank()) return;
            if (Boolean.TRUE.equals(item.getCooked())) dishes.add(new WeightedDish(item.getDishName(), 2d));
            if (Boolean.FALSE.equals(item.getCooked())) dishes.add(new WeightedDish(item.getDishName(), -2d));
        });
        return dishes;
    }

    private static int readCounter(String value, String field) {
        if (value == null || value.isBlank()) return 0;
        for (String part : value.split(";")) {
            String[] pair = part.split("=");
            if (pair.length == 2 && pair[0].trim().equals(field)) return parseIntOrZero(pair[1]);
        }
        return 0;
    }

    private static int parseIntOrZero(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private Integer parseInt(String value) {
        try {
            return value == null ? null : Integer.parseInt(value.trim());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private double parseDouble(String value) {
        try {
            return value == null ? 0d : Double.parseDouble(value.trim());
        } catch (RuntimeException ignored) {
            return 0d;
        }
    }

    private record WeightedDish(String name, double weight) {}
}
