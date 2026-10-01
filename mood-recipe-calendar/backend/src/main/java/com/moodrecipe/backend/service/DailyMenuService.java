package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.DailyMenu;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.entity.RecipeInteraction;
import com.moodrecipe.backend.entity.UserFoodPreference;
import com.moodrecipe.backend.entity.UserRecord;
import com.moodrecipe.backend.agent.MenuPlannerAgent;
import com.moodrecipe.backend.repository.DailyMenuRepository;
import com.moodrecipe.backend.repository.RecipeInteractionRepository;
import com.moodrecipe.backend.repository.RecipeRepository;
import com.moodrecipe.backend.repository.UserFoodPreferenceRepository;
import com.moodrecipe.backend.repository.UserRecordRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 今日菜单（TodayBoard）：v2 的每日钩子。
 * 打开即有的零输入答案——当日缓存行命中直接返回（毫秒级）；
 * 未命中时从菜谱池按「忌口/过敏硬过滤 + 反馈与口味加分 + 近 3 天吃过的去重 + 少量随机」挑一份并落缓存。
 * 本版本不调用 LLM（DAU 早期阶段复杂度优先），LLM 生成留作后续升级。
 */
@Service
public class DailyMenuService {

    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");

    private static final int RECENT_DISH_DEDUPE_DAYS = 3;

    private final DailyMenuRepository dailyMenus;
    private final RecipeRepository recipes;
    private final RecipeInteractionRepository interactions;
    private final UserRecordRepository records;
    private final UserFoodPreferenceRepository preferences;
    private final AllergenNormalizationService allergenNormalization;

    public DailyMenuService(DailyMenuRepository dailyMenus, RecipeRepository recipes,
                            RecipeInteractionRepository interactions, UserRecordRepository records,
                            UserFoodPreferenceRepository preferences,
                            AllergenNormalizationService allergenNormalization) {
        this.dailyMenus = dailyMenus;
        this.recipes = recipes;
        this.interactions = interactions;
        this.records = records;
        this.preferences = preferences;
        this.allergenNormalization = allergenNormalization;
    }

    /** 今日菜单：命中当日缓存直接返回；未命中挑选并落缓存。挑选失败返回 empty。 */
    public Optional<Board> today(String openid) {
        LocalDate today = LocalDate.now(CHINA_ZONE);
        Optional<DailyMenu> cached = dailyMenus.findByOpenidAndMenuDate(openid, today);
        if (cached.isPresent()) {
            return toBoard(cached.get()).or(() -> create(openid, today, Set.of()));
        }
        return create(openid, today, Set.of());
    }

    /** 换一道：重新挑选并排除当前这道；没有别的可换时返回 empty，当前缓存保持不动。 */
    public Optional<Board> refresh(String openid) {
        LocalDate today = LocalDate.now(CHINA_ZONE);
        Set<Long> exclude = dailyMenus.findByOpenidAndMenuDate(openid, today)
                .map(row -> Set.of(row.getRecipeId()))
                .orElseGet(Set::of);
        return create(openid, today, exclude);
    }

    private Optional<Board> create(String openid, LocalDate date, Set<Long> excludeIds) {
        UserFoodPreference preference = preferences.findByOpenid(openid).orElse(null);
        Recipe picked = pick(openid, preference, excludeIds);
        if (picked == null) return Optional.empty();

        Optional<DailyMenu> existing = dailyMenus.findByOpenidAndMenuDate(openid, date);
        DailyMenu row = existing.orElseGet(() -> {
            DailyMenu fresh = new DailyMenu();
            fresh.setOpenid(openid);
            fresh.setMenuDate(date);
            return fresh;
        });
        row.setRecipeId(picked.getId());
        row.setGuozaiLine(guozaiLine(picked));
        row.setSource("POOL");
        row.setVariant(existing.map(item -> item.getVariant() + 1).orElse(0));
        dailyMenus.save(row);

        markShown(openid, picked.getId());
        return toBoard(row);
    }

    private Optional<Board> toBoard(DailyMenu row) {
        return recipes.findById(row.getRecipeId())
                .filter(this::isDisplayableRecipe)
                .map(recipe -> new Board(row.getMenuDate().toString(), recipe,
                        row.getGuozaiLine(), row.getVariant(), row.getSource()));
    }

    /** 挑选逻辑：硬过滤（忌口/过敏/明确不喜欢/今天已出/近期吃过）+ 软加分（反馈/口味/随机）。 */
    private Recipe pick(String openid, UserFoodPreference preference, Set<Long> excludeIds) {
        Set<Long> rejected = interactions.findByOpenidAndAction(openid, "DISLIKE").stream()
                .map(RecipeInteraction::getRecipeId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> recentDishes = recentDishNames(openid);
        List<String> blocked = blockedTerms(preference);
        Map<Long, Integer> feedback = new HashMap<>();
        interactions.findTop30ByOpenidOrderByCreatedAtDesc(openid).forEach(interaction ->
                feedback.merge(interaction.getRecipeId(), switch (interaction.getAction()) {
                    case "MADE" -> 3;
                    case "LIKE" -> 2;
                    default -> 0;
                }, Integer::sum));

        List<Recipe> pool = new ArrayList<>(recipes.findAiWithImages());
        if (pool.isEmpty()) pool = recipes.findAll();

        List<Recipe> candidates = pool.stream()
                .filter(this::isDisplayableRecipe)
                .filter(recipe -> recipe.getName() != null && !recipe.getName().isBlank())
                .filter(recipe -> !rejected.contains(recipe.getId()))
                .filter(recipe -> !excludeIds.contains(recipe.getId()))
                .filter(recipe -> !recentDishes.contains(recipe.getName().trim()))
                .filter(recipe -> allowedByPreference(recipe, blocked))
                .toList();
        if (candidates.isEmpty()) return null;

        return candidates.stream()
                .max(Comparator.comparingInt((Recipe recipe) ->
                        feedback.getOrDefault(recipe.getId(), 0)
                                + preferenceBonus(recipe, preference)
                                + ThreadLocalRandom.current().nextInt(4)))
                .orElse(null);
    }

    /** 今日菜单是首页入口，菜名、描述和做法出现编码污染时必须整道拦截。 */
    private boolean isDisplayableRecipe(Recipe recipe) {
        return recipe != null
                && cleanText(recipe.getName(), 80)
                && cleanText(recipe.getDescription(), 500)
                && cleanText(recipe.getIngredients(), 1500)
                && cleanText(recipe.getSteps(), 3000);
    }

    private boolean cleanText(String value, int maxLength) {
        return value == null || value.isBlank() || MenuPlannerAgent.displayableText(value.trim(), maxLength);
    }

    private Set<String> recentDishNames(String openid) {
        String since = LocalDate.now(CHINA_ZONE).minusDays(RECENT_DISH_DEDUPE_DAYS).toString();
        return records.findTop30ByOpenidOrderByCreatedAtDesc(openid).stream()
                .filter(record -> record.getRecordDate() != null
                        && record.getRecordDate().compareTo(since) >= 0)
                .map(UserRecord::getDishName)
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .collect(Collectors.toSet());
    }

    private List<String> blockedTerms(UserFoodPreference preference) {
        if (preference == null) return List.of();
        Set<String> terms = new java.util.LinkedHashSet<>(allergenNormalization.normalize(
                preference.getAvoidIngredients(), preference.getAllergens()));
        if (preference.getNormalizedBlockedTerms() != null && !preference.getNormalizedBlockedTerms().isBlank()) {
            terms.addAll(allergenNormalization.normalize(
                    preference.getNormalizedBlockedTerms(), ""));
        }
        return terms.stream().filter(term -> term != null && !term.isBlank()).toList();
    }

    private boolean allowedByPreference(Recipe recipe, List<String> blocked) {
        if (blocked.isEmpty()) return true;
        String text = (safe(recipe.getName()) + safe(recipe.getIngredients())).toLowerCase();
        return blocked.stream().noneMatch(term -> text.contains(term.toLowerCase()));
    }

    private int preferenceBonus(Recipe recipe, UserFoodPreference preference) {
        if (preference == null) return 0;
        String text = (safe(recipe.getName()) + safe(recipe.getMoodTags())).toLowerCase();
        int bonus = 0;
        if (containsAny(text, preference.getFavoriteTags())) bonus += 3;
        if (containsAny(text, preference.getFavoriteCuisines())) bonus += 3;
        if (containsAny(text, preference.getFavoriteDishes())) bonus += 1;
        return bonus;
    }

    private boolean containsAny(String text, String csv) {
        if (text == null || text.isBlank() || csv == null || csv.isBlank()) return false;
        for (String term : csv.split("[,，、]")) {
            String value = term.trim().toLowerCase();
            if (!value.isEmpty() && text.contains(value)) return true;
        }
        return false;
    }

    /** 锅仔今日一句话：本地模板 + 轻随机，避免每天同一句。 */
    private String guozaiLine(Recipe recipe) {
        String name = safe(recipe.getName());
        String minutes = recipe.getCookingTime() != null && recipe.getCookingTime() > 0
                ? "，" + recipe.getCookingTime() + " 分钟就能端上桌" : "";
        String[] lines = {
                "今天不用纠结，锅仔替你定了：" + name + minutes,
                "就它了——" + name + "，锅仔觉得和今天很配",
                "今天这份 " + name + " 已经帮你留好啦" + minutes,
                "别想太多，今晚 " + name + "，吃过再说",
        };
        return lines[ThreadLocalRandom.current().nextInt(lines.length)];
    }

    /** 今日卡也算一次曝光，普通推荐会自动避开它，避免一天内重复出现。 */
    private void markShown(String openid, Long recipeId) {
        try {
            RecipeInteraction shown = new RecipeInteraction();
            shown.setOpenid(openid);
            shown.setRecipeId(recipeId);
            shown.setAction("SHOWN");
            interactions.save(shown);
        } catch (RuntimeException ignored) {
            // 曝光记录失败不能影响今日卡返回。
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    /** 今日卡响应：菜谱对象结构沿用现有前端渲染契约（ingredients/steps 仍是 JSON 字符串）。 */
    public record Board(String date, Recipe recipe, String guozaiLine, int variant, String source) { }
}
