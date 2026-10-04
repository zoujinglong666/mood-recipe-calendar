package com.moodrecipe.backend.agent;

import com.moodrecipe.backend.entity.AgentMemoryFact;
import com.moodrecipe.backend.entity.PlanDishOutcome;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.entity.RecipeInteraction;
import com.moodrecipe.backend.entity.UserFoodPreference;
import com.moodrecipe.backend.entity.UserRecord;
import com.moodrecipe.backend.config.AppClock;
import com.moodrecipe.backend.repository.AgentMemoryFactRepository;
import com.moodrecipe.backend.repository.PlanDishOutcomeRepository;
import com.moodrecipe.backend.repository.RecipeInteractionRepository;
import com.moodrecipe.backend.repository.RecipeRepository;
import com.moodrecipe.backend.repository.UserFoodPreferenceRepository;
import com.moodrecipe.backend.repository.UserRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 智能体的事实记忆仓库。
 *
 * 三件事：
 * 1. 记住（remember）：带置信度、来源、证据、时效地写入；
 * 2. 想起（recall）：按场景检索，衰减与过期自动归档，并说明"为什么用这条"；
 * 3. 遗忘（forgetExpired）：过期或衰减到阈值以下的事实不再进入提示词。
 */
@Service
public class AgentMemoryStore {

    public enum Scene { DIALOGUE, WEEKLY_PLAN, SINGLE_RECIPE, COMPANION }

    public static final String SRC_EXPLICIT = "EXPLICIT";
    public static final String SRC_CHAT = "CHAT";
    public static final String SRC_INFERRED = "INFERRED";
    public static final String SRC_BEHAVIOR = "BEHAVIOR";
    public static final String SRC_LEARNED = "LEARNED";

    public static final String KEY_PEOPLE = "people";
    public static final String KEY_DISHES = "dishesPerDay";
    public static final String KEY_DAYS = "cookingDays";
    public static final String KEY_SPICE = "spice";
    public static final String KEY_HOUSEHOLD = "household";
    public static final String KEY_HEALTH_GOAL = "healthGoal";
    public static final String KEY_BUDGET = "budget";
    public static final String KEY_CUISINE = "favoriteCuisine";
    public static final String KEY_RECENT_DISHES = "dish.recent";
    public static final String KEY_SKIP_QUESTIONS = "strategy.skipQuestions";
    public static final String KEY_MAX_MINUTES = "preference.maxCookingMinutes";
    public static final String KEY_SIMPLE = "preference.simpleDishes";
    public static final String KEY_PERSONALIZATION = "settings.personalization";
    public static final String AFFINITY_PREFIX = "affinity.";
    public static final String KEY_ALLERGENS = "safety.allergens";
    public static final String KEY_AVOID_INGREDIENTS = "safety.avoidIngredients";
    public static final String CATEGORY_EXPLICIT_PREFERENCE = "EXPLICIT_PREFERENCE";
    public static final String CATEGORY_BEHAVIOR_SIGNAL = "BEHAVIOR_SIGNAL";
    public static final String CATEGORY_MEAL_CONTEXT = "MEAL_CONTEXT";
    public static final String CATEGORY_SHORT_TERM_STATE = "SHORT_TERM_STATE";
    public static final String CATEGORY_SAFETY_CONSTRAINT = "SAFETY_CONSTRAINT";

    /** 不同来源的半衰期（天）：用户明确设置的记得久，模型推断的忘得快。 */
    private static final Map<String, Double> HALF_LIFE_DAYS = Map.of(
            SRC_EXPLICIT, 365d,
            SRC_CHAT, 60d,
            SRC_INFERRED, 14d,
            SRC_BEHAVIOR, 45d,
            SRC_LEARNED, 90d);

    private final AgentMemoryFactRepository facts;
    private final UserFoodPreferenceRepository preferences;
    private final UserRecordRepository records;
    private final RecipeInteractionRepository interactions;
    private final RecipeRepository recipes;
    private final PlanDishOutcomeRepository outcomes;

    public AgentMemoryFactRepository repo() {
        return facts;
    }

    public AgentMemoryStore(AgentMemoryFactRepository facts, UserFoodPreferenceRepository preferences,
                            UserRecordRepository records, RecipeInteractionRepository interactions,
                            RecipeRepository recipes, PlanDishOutcomeRepository outcomes) {
        this.facts = facts;
        this.preferences = preferences;
        this.records = records;
        this.interactions = interactions;
        this.recipes = recipes;
        this.outcomes = outcomes;
    }

    /** 写入或更新一条事实；相同值会增强置信度，更可信的新值会覆盖旧值。 */
    public MemoryItem remember(RememberCommand command) {
        if (command == null || command.openid() == null || command.openid().isBlank()) return null;
        if (command.key() == null || command.key().isBlank()) return null;
        if (command.value() == null || command.value().isBlank()) return null;
        if (isSafetyKey(command.key()) && SRC_INFERRED.equals(command.source())) return null;
        if (!KEY_PERSONALIZATION.equals(command.key()) && !personalizationEnabled(command.openid())) return null;

        // memory_value 列上限 200：聚合类记忆（如 dish.avoid、preference.moodLiked 的菜名清单）可能超长，
        // 统一在此收口截断，防止 Data too long 异常沿调用链把同步接口（如 /agent/outcomes）打成 500。
        String value = trim(command.value(), 200);
        AgentMemoryFact fact = facts.findByOpenidAndMemoryKeyAndStatus(command.openid(), command.key(),
                        AgentMemoryFact.STATUS_ACTIVE)
                .orElseGet(() -> facts.findByOpenidAndMemoryKey(command.openid(), command.key()).orElse(null));

        if (fact == null) {
            fact = new AgentMemoryFact();
            fact.setOpenid(command.openid());
            fact.setMemoryKey(command.key());
            fact.setMemoryValue(value);
            fact.setMemoryCategory(categoryOf(command.key(), command.ttlDays()));
            fact.setSource(command.source());
            fact.setConfidence(clamp(command.confidence()));
            fact.setEvidence(trim(command.evidence(), 500));
            fact.setStatus(AgentMemoryFact.STATUS_ACTIVE);
            fact.setHitCount(0);
            fact.setExpiresAt(command.ttlDays() == null || command.ttlDays() <= 0
                    ? null : AppClock.now().plusDays(command.ttlDays()));
        } else {
            double current = effectiveConfidence(fact);
            if (value.equals(fact.getMemoryValue())) {
                fact.setConfidence(clamp(Math.max(current, command.confidence()) + 0.05));
                if (command.evidence() != null && !command.evidence().isBlank()) {
                    fact.setEvidence(trim(command.evidence(), 500));
                }
            } else if (command.confidence() >= current) {
                fact.setMemoryValue(value);
                fact.setConfidence(clamp(command.confidence()));
                fact.setEvidence(trim(command.evidence(), 500));
            }
            fact.setStatus(AgentMemoryFact.STATUS_ACTIVE);
            fact.setSource(command.source());
            fact.setExpiresAt(command.ttlDays() == null || command.ttlDays() <= 0
                    ? fact.getExpiresAt() : AppClock.now().plusDays(command.ttlDays()));
            if (fact.getHitCount() == null) fact.setHitCount(0);
            if (fact.getMemoryCategory() == null || fact.getMemoryCategory().isBlank()) {
                fact.setMemoryCategory(categoryOf(command.key(), command.ttlDays()));
            }
        }
        AgentMemoryFact saved = facts.save(fact);
        return toItem(saved, "刚写入");
    }

    /** 命中一次：提高置信度与使用次数，让真正有用的记忆越来越稳。 */
    public void reinforce(String openid, Collection<String> keys) {
        if (openid == null || keys == null || keys.isEmpty()) return;
        for (String key : keys) {
            if (key == null || key.isBlank()) continue;
            facts.findByOpenidAndMemoryKeyAndStatus(openid, key, AgentMemoryFact.STATUS_ACTIVE).ifPresent(fact -> {
                fact.setConfidence(clamp(effectiveConfidence(fact) + 0.03));
                fact.setHitCount((fact.getHitCount() == null ? 0 : fact.getHitCount()) + 1);
                fact.setLastUsedAt(AppClock.now());
                facts.save(fact);
            });
        }
    }

    /** 按场景检索记忆，衰减与过期的事实会自动归档。 */
    public List<MemoryItem> recall(String openid, Scene scene, int limit) {
        if (openid == null || openid.isBlank() || !personalizationEnabled(openid)) return List.of();
        forgetExpired(openid);
        return facts.findByOpenidAndStatusOrderByUpdatedAtDesc(openid, AgentMemoryFact.STATUS_ACTIVE).stream()
                .filter(fact -> scopeOf(fact.getMemoryKey()).contains(scene))
                .filter(fact -> !KEY_PERSONALIZATION.equals(fact.getMemoryKey()))
                .map(fact -> toItem(fact, reasonOf(fact)))
                .filter(item -> item.confidence() >= AgentMemoryFact.MIN_CONFIDENCE)
                .sorted(Comparator.comparingDouble(MemoryItem::confidence).reversed()
                        .thenComparing(MemoryItem::key))
                .limit(Math.max(1, limit))
                .toList();
    }

    /** 面向"锅仔记忆"页：完整列出事实与证据。 */
    public List<MemoryItem> all(String openid) {
        if (openid == null || openid.isBlank()) return List.of();
        forgetExpired(openid);
        return facts.findByOpenidAndStatusOrderByUpdatedAtDesc(openid, AgentMemoryFact.STATUS_ACTIVE).stream()
                .filter(fact -> !KEY_PERSONALIZATION.equals(fact.getMemoryKey()))
                .map(fact -> toItem(fact, reasonOf(fact)))
                .toList();
    }

    @Transactional
    public void forget(String openid, String key) {
        if (KEY_PERSONALIZATION.equals(key)) return;
        facts.deleteByOpenidAndMemoryKey(openid, key);
    }

    @Transactional
    public void clear(String openid) {
        boolean enabled = personalizationEnabled(openid);
        facts.deleteByOpenid(openid);
        interactions.deleteByOpenid(openid);
        setPersonalizationEnabled(openid, enabled);
    }

    public boolean personalizationEnabled(String openid) {
        return facts.findByOpenidAndMemoryKey(openid, KEY_PERSONALIZATION)
                .map(fact -> !"false".equalsIgnoreCase(fact.getMemoryValue()))
                .orElse(true);
    }

    public void setPersonalizationEnabled(String openid, boolean enabled) {
        remember(new RememberCommand(openid, KEY_PERSONALIZATION, Boolean.toString(enabled), 0.95,
                SRC_EXPLICIT, "用户在设置页主动选择", null));
    }

    /** 过期或衰减到阈值以下的事实归档，实现"遗忘"。 */
    public void forgetExpired(String openid) {
        LocalDateTime now = AppClock.now();
        for (AgentMemoryFact fact : facts.findByOpenidAndStatusOrderByUpdatedAtDesc(openid, AgentMemoryFact.STATUS_ACTIVE)) {
            boolean expired = fact.getExpiresAt() != null && fact.getExpiresAt().isBefore(now);
            boolean faded = !isSafetyKey(fact.getMemoryKey())
                    && effectiveConfidence(fact) < AgentMemoryFact.MIN_CONFIDENCE;
            if (expired || faded) {
                fact.setStatus(AgentMemoryFact.STATUS_ARCHIVED);
                facts.save(fact);
            }
        }
    }

    /** 组装可解释的用户档案。 */
    public UserProfile profile(String openid, Scene scene) {
        UserFoodPreference preference = preferences.findByOpenid(openid).orElse(null);
        if (!personalizationEnabled(openid)) return safetyOnlyProfile(openid, preference);
        List<UserRecord> recent = records.findTop30ByOpenidOrderByCreatedAtDesc(openid);
        List<RecipeInteraction> history = interactions.findTop30ByOpenidOrderByCreatedAtDesc(openid);
        Map<Long, String> recipeNames = recipeNames(history);
        List<MemoryItem> memory = recall(openid, scene, 12);

        LocalDate today = AppClock.today();
        List<String> recentDishes = new ArrayList<>(recent.stream()
                .filter(record -> withinDays(record.getRecordDate(), today, 14))
                .map(UserRecord::getDishName)
                .filter(name -> name != null && !name.isBlank())
                .distinct().limit(10).toList());
        List<String> lovedDishes = history.stream()
                .filter(item -> "LIKE".equals(item.getAction()) || "MADE".equals(item.getAction()))
                .map(item -> recipeNames.get(item.getRecipeId()))
                .filter(Objects::nonNull).distinct().limit(8).toList();
        List<String> rejectedDishes = history.stream()
                .filter(item -> "DISLIKE".equals(item.getAction()))
                .map(item -> recipeNames.get(item.getRecipeId()))
                .filter(Objects::nonNull).distinct().limit(8).toList();

        Map<String, Double> affinity = new LinkedHashMap<>();
        List<String> skipQuestions = new ArrayList<>();
        List<String> avoidDishes = new ArrayList<>();
        Integer maxMinutes = null;
        boolean preferSimple = false;
        Integer people = null;
        Integer dishesPerDay = null;
        List<Integer> cookingDays = null;
        String spice = null;
        Boolean elder = null;
        Boolean child = null;
        String healthGoal = null;
        String budget = null;
        List<String> cuisines = new ArrayList<>();
        List<String> rememberedAvoidIngredients = new ArrayList<>();
        List<String> rememberedAllergens = new ArrayList<>();

        for (MemoryItem item : memory) {
            String key = item.key();
            if (key.startsWith(AFFINITY_PREFIX)) {
                affinity.put(key.substring(AFFINITY_PREFIX.length()), parseDouble(item.value()));
            } else if (KEY_SKIP_QUESTIONS.equals(key)) {
                skipQuestions.addAll(split(item.value()));
            } else if (KEY_MAX_MINUTES.equals(key)) {
                maxMinutes = parseIntOrNull(item.value());
            } else if (KEY_SIMPLE.equals(key) && "true".equalsIgnoreCase(item.value())) {
                preferSimple = true;
            } else if (KEY_PEOPLE.equals(key)) {
                people = parseIntOrNull(item.value());
            } else if (KEY_DISHES.equals(key)) {
                dishesPerDay = parseIntOrNull(item.value());
            } else if (KEY_DAYS.equals(key)) {
                cookingDays = split(item.value()).stream().map(this::parseIntOrNull)
                        .filter(Objects::nonNull).filter(day -> day >= 0 && day < 7).distinct().sorted().toList();
            } else if (KEY_SPICE.equals(key)) {
                spice = item.value();
            } else if (KEY_HOUSEHOLD.equals(key)) {
                elder = item.value().contains("老人");
                child = item.value().contains("小孩");
            } else if (KEY_HEALTH_GOAL.equals(key)) {
                healthGoal = item.value();
            } else if (KEY_BUDGET.equals(key)) {
                budget = item.value();
            } else if (KEY_CUISINE.equals(key)) {
                cuisines.add(item.value());
            } else if (KEY_AVOID_INGREDIENTS.equals(key)) {
                rememberedAvoidIngredients.addAll(split(item.value()));
            } else if (KEY_ALLERGENS.equals(key)) {
                rememberedAllergens.addAll(split(item.value()));
            } else if ("dish.avoid".equals(key)) {
                avoidDishes.addAll(split(item.value()));
            } else if (KEY_RECENT_DISHES.equals(key)) {
                recentDishes.addAll(split(item.value()));
            }
        }

        if (preference != null) {
            if (spice == null && preference.getSpiceLevel() != null) spice = preference.getSpiceLevel();
            if (healthGoal == null && preference.getHealthGoal() != null) healthGoal = preference.getHealthGoal();
            cuisines.addAll(split(preference.getFavoriteCuisines()));
        }
        if (people == null && preference != null) {
            // 偏好页没有人数，保持未知，交给对话补齐
        }
        List<PlanDishOutcome> planOutcomes = outcomes == null ? List.of() : outcomes.findTop50ByOpenidOrderByUpdatedAtDesc(openid);
        long hardCount = planOutcomes.stream().filter(item -> Boolean.TRUE.equals(item.getTooHard())).count();
        long leftoverCount = planOutcomes.stream().filter(item -> Boolean.TRUE.equals(item.getLeftover())).count();
        if (hardCount >= 2) preferSimple = true;
        if (leftoverCount >= 2 && dishesPerDay == null) dishesPerDay = 1;

        int recordDays = (int) recent.stream().map(UserRecord::getRecordDate)
                .filter(Objects::nonNull).distinct()
                .filter(date -> withinDays(date, today, 30)).count();

        if (preference != null) {
            rememberedAvoidIngredients.addAll(split(preference.getAvoidIngredients()));
            rememberedAllergens.addAll(split(preference.getAllergens()));
        }

        return new UserProfile(openid, people, dishesPerDay, cookingDays, spice, elder, child,
                healthGoal, budget, cuisines.stream().distinct().toList(),
                rememberedAvoidIngredients.stream().distinct().toList(),
                rememberedAllergens.stream().distinct().toList(),
                recentDishes.stream().distinct().limit(16).toList(),
                lovedDishes, rejectedDishes, avoidDishes, skipQuestions, affinity, maxMinutes,
                preferSimple, recordDays, memory);
    }

    private UserProfile safetyOnlyProfile(String openid, UserFoodPreference preference) {
        List<String> avoidIngredients = new ArrayList<>();
        List<String> allergens = new ArrayList<>();
        if (preference != null) {
            avoidIngredients.addAll(split(preference.getAvoidIngredients()));
            allergens.addAll(split(preference.getAllergens()));
        }
        facts.findByOpenidAndStatusOrderByUpdatedAtDesc(openid, AgentMemoryFact.STATUS_ACTIVE).stream()
                .filter(fact -> isSafetyKey(fact.getMemoryKey()))
                .forEach(fact -> {
                    if (KEY_ALLERGENS.equals(fact.getMemoryKey())) allergens.addAll(split(fact.getMemoryValue()));
                    if (KEY_AVOID_INGREDIENTS.equals(fact.getMemoryKey())) {
                        avoidIngredients.addAll(split(fact.getMemoryValue()));
                    }
                });
        UserProfile empty = UserProfile.empty(openid);
        return new UserProfile(openid, empty.people(), empty.dishesPerDay(), empty.cookingDays(),
                empty.spiceLevel(), empty.hasElder(), empty.hasChild(), empty.healthGoal(), empty.budget(),
                empty.favoriteCuisines(), avoidIngredients.stream().distinct().toList(),
                allergens.stream().distinct().toList(), empty.recentDishes(), empty.lovedDishes(),
                empty.rejectedDishes(), empty.avoidDishes(), empty.skipQuestions(), empty.cuisineAffinity(),
                empty.maxCookingMinutes(), empty.preferSimple(), empty.recordDays(), empty.memory());
    }

    /** 时间衰减后的有效置信度：越久没被印证，越不可信。 */
    public double effectiveConfidence(AgentMemoryFact fact) {
        double stored = fact.getConfidence() == null ? 0.6 : fact.getConfidence();
        if (isSafetyKey(fact.getMemoryKey())) return stored;
        LocalDateTime updated = fact.getUpdatedAt() == null ? AppClock.now() : fact.getUpdatedAt();
        double ageDays = Math.max(0d, ChronoUnit.HOURS.between(updated, AppClock.now()) / 24d);
        double halfLife = HALF_LIFE_DAYS.getOrDefault(fact.getSource(), 30d);
        double decayed = stored * Math.pow(0.5, ageDays / halfLife);
        int hits = fact.getHitCount() == null ? 0 : fact.getHitCount();
        return clamp(decayed + Math.min(0.1, hits * 0.02));
    }

    private MemoryItem toItem(AgentMemoryFact fact, String reason) {
        return new MemoryItem(fact.getMemoryKey(), fact.getMemoryValue(), categoryOf(fact), effectiveConfidence(fact),
                fact.getSource(), fact.getEvidence(), fact.getUpdatedAt(), reason);
    }

    private String categoryOf(AgentMemoryFact fact) {
        if (isSafetyKey(fact.getMemoryKey())) return CATEGORY_SAFETY_CONSTRAINT;
        return fact.getMemoryCategory() == null || fact.getMemoryCategory().isBlank()
                ? categoryOf(fact.getMemoryKey(), fact.getExpiresAt() == null ? null : 1)
                : fact.getMemoryCategory();
    }

    private String categoryOf(String key, Integer ttlDays) {
        if (isSafetyKey(key)) return CATEGORY_SAFETY_CONSTRAINT;
        if (ttlDays != null && ttlDays > 0) return CATEGORY_SHORT_TERM_STATE;
        if (key != null && (key.startsWith(AFFINITY_PREFIX) || key.startsWith("dish.")
                || key.startsWith("strategy.") || key.startsWith("preference.simple"))) {
            return CATEGORY_BEHAVIOR_SIGNAL;
        }
        if (Set.of(KEY_PEOPLE, KEY_DISHES, KEY_DAYS, KEY_HOUSEHOLD, KEY_BUDGET).contains(key)) {
            return CATEGORY_MEAL_CONTEXT;
        }
        return CATEGORY_EXPLICIT_PREFERENCE;
    }

    private boolean isSafetyKey(String key) {
        return key != null && key.startsWith("safety.");
    }

    private String reasonOf(AgentMemoryFact fact) {
        String evidence = fact.getEvidence() == null || fact.getEvidence().isBlank()
                ? "无额外依据" : fact.getEvidence();
        return sourceLabel(fact.getSource()) + "（置信度 " + String.format("%.2f", effectiveConfidence(fact)) + "）：" + evidence;
    }

    private String sourceLabel(String source) {
        return switch (source == null ? "" : source) {
            case SRC_EXPLICIT -> "用户在偏好页明确设置";
            case SRC_CHAT -> "用户在对话里说过";
            case SRC_INFERRED -> "锅仔从话里推断";
            case SRC_BEHAVIOR -> "根据记录行为统计";
            case SRC_LEARNED -> "根据反馈学习";
            default -> "来源未标注";
        };
    }

    private static Set<Scene> scopeOf(String key) {
        if (key == null) return Set.of();
        // 学到的对话策略（比如"这个问题别再问了"）必须回到对话里生效，否则学习闭环是断的
        if (key.startsWith("strategy.")) return Set.of(Scene.DIALOGUE, Scene.WEEKLY_PLAN, Scene.SINGLE_RECIPE);
        if (key.startsWith(AFFINITY_PREFIX) || key.startsWith("preference.")) {
            return Set.of(Scene.WEEKLY_PLAN, Scene.SINGLE_RECIPE);
        }
        if (key.startsWith("dish.")) return Set.of(Scene.WEEKLY_PLAN, Scene.SINGLE_RECIPE, Scene.DIALOGUE);
        return Set.of(Scene.DIALOGUE, Scene.WEEKLY_PLAN, Scene.SINGLE_RECIPE, Scene.COMPANION);
    }

    private Map<Long, String> recipeNames(List<RecipeInteraction> history) {
        Set<Long> ids = history.stream().map(RecipeInteraction::getRecipeId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return recipes.findAllById(ids).stream()
                .filter(recipe -> recipe.getName() != null)
                .collect(Collectors.toMap(Recipe::getId, Recipe::getName, (a, b) -> a));
    }

    private boolean withinDays(String date, LocalDate today, int days) {
        if (date == null || date.isBlank()) return false;
        try {
            LocalDate value = LocalDate.parse(date);
            return !value.isBefore(today.minusDays(days)) && !value.isAfter(today);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private List<String> split(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split("[,，、;；|]+"))
                .map(String::trim).filter(text -> !text.isBlank()).distinct().toList();
    }

    private Integer parseIntOrNull(String value) {
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

    private String trim(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    private double clamp(double value) {
        return Math.max(0d, Math.min(0.95d, value));
    }

    public record RememberCommand(String openid, String key, String value, double confidence,
                                  String source, String evidence, Integer ttlDays) {

        public static RememberCommand explicit(String openid, String key, String value, String evidence) {
            return new RememberCommand(openid, key, value, 0.9, SRC_CHAT, evidence, null);
        }

        public static RememberCommand inferred(String openid, String key, String value, String evidence) {
            return new RememberCommand(openid, key, value, 0.55, SRC_INFERRED, evidence, 30);
        }

        public static RememberCommand learned(String openid, String key, String value, String evidence) {
            return new RememberCommand(openid, key, value, 0.7, SRC_LEARNED, evidence, null);
        }
    }
}
