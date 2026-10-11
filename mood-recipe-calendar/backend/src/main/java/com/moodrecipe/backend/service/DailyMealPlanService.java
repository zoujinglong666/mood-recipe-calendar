package com.moodrecipe.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.agent.AgentMemoryStore;
import com.moodrecipe.backend.agent.UserProfile;
import com.moodrecipe.backend.entity.DailyMealPlan;
import com.moodrecipe.backend.entity.NutritionKnowledgePack;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.entity.SeasonalIngredient;
import com.moodrecipe.backend.entity.UserFoodPreference;
import com.moodrecipe.backend.repository.DailyMealPlanRepository;
import com.moodrecipe.backend.repository.NutritionKnowledgePackRepository;
import com.moodrecipe.backend.repository.RecipeRepository;
import com.moodrecipe.backend.repository.SeasonalIngredientRepository;
import com.moodrecipe.backend.repository.UserFoodPreferenceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class DailyMealPlanService {
    private final DailyMealPlanRepository plans;
    private final RecipeRepository recipes;
    private final UserFoodPreferenceRepository preferences;
    private final NutritionKnowledgePackRepository packs;
    private final SeasonalIngredientRepository seasonal;
    private final DailyMealPlanValidator validator;
    private final ObjectMapper json;
    private final AgentMemoryStore memory;
    private final RecipePool recipePool;
    private final GuidelineRagService guidelineRag;

    /** 候选集封顶：findThree 是 O(n³) 暴力枚举，菜谱表稍大就会让请求挂死（线上曾因 AI 灌入数百道菜而 60s 超时）。取排序后的前若干道即可把组合数收敛为常数级，且前三道偏好已排序不妨碍质量。 */
    private static final int MAX_CANDIDATES = 120;

    /** 同一天的三餐内容稳定，RAG 检索与营养结构只算一次，避免每次 onShow 重复打向量库。 */
    private final Map<LocalDate, Enrichment> enrichmentCache = new ConcurrentHashMap<>();

    /** 每餐的《中国居民膳食指南》检索关键词骨架：先框定餐别，再交给向量检索细化。 */
    private static final Map<Integer, String> MEAL_GUIDANCE = Map.of(
            0, "早餐 营养充足 谷薯类 优质蛋白 蔬果 粗细搭配",
            1, "午餐 能量充足 粗细搭配 鱼禽肉蛋 蔬菜 豆制品",
            2, "晚餐 清淡 七分饱 蔬菜为主 易消化 少油盐"
    );

    /** 营养结构启发式：用食材名命中大类，给「整体营养结构」一个可追溯的概览。 */
    private static final Map<String, List<String>> CATEGORY_KEYWORDS = Map.of(
            "谷薯类", List.of("米", "饭", "粥", "面", "粉", "馒", "包", "饺", "薯", "土豆", "红薯", "玉米", "麦", "饼", "糯", "杂粮", "燕麦", "荞", "薏"),
            "优质蛋白", List.of("鸡", "牛", "猪", "羊", "鱼", "虾", "蟹", "蛋", "肉", "腿", "排"),
            "大豆及制品", List.of("豆", "腐", "浆", "豉", "纳"),
            "蔬菜", List.of("菜", "青", "白", "菠", "芹", "萝", "茄", "黄", "瓜", "葱", "蒜", "洋", "蘑", "菌", "椒", "笋", "藕", "番", "西蓝", "西兰"),
            "奶类", List.of("奶", "乳", "酸", "酪", "芝", "豆浆", "豆奶"),
            "水果", List.of("果", "苹", "香", "橙", "橘", "莓", "桃", "梨", "葡", "西", "芒")
    );

    DailyMealPlanService(DailyMealPlanRepository plans, RecipeRepository recipes,
                         UserFoodPreferenceRepository preferences,
                         NutritionKnowledgePackRepository packs,
                         SeasonalIngredientRepository seasonal,
                         DailyMealPlanValidator validator, ObjectMapper json) {
        this(plans, recipes, preferences, packs, seasonal, validator, json, null, null, null);
    }

    @Autowired
    public DailyMealPlanService(DailyMealPlanRepository plans, RecipeRepository recipes,
                                UserFoodPreferenceRepository preferences,
                                NutritionKnowledgePackRepository packs,
                                SeasonalIngredientRepository seasonal,
                                DailyMealPlanValidator validator, ObjectMapper json,
                                AgentMemoryStore memory, RecipePool recipePool,
                                GuidelineRagService guidelineRag) {
        this.plans = plans;
        this.recipes = recipes;
        this.preferences = preferences;
        this.packs = packs;
        this.seasonal = seasonal;
        this.validator = validator;
        this.json = json;
        this.memory = memory;
        this.recipePool = recipePool;
        this.guidelineRag = guidelineRag;
    }

    public Optional<PlanView> plan(String openid, LocalDate date) {
        Optional<DailyMealPlan> existing = plans.findByOpenidAndPlanDate(openid, date);
        if (existing.isPresent()) return read(existing.get());

        List<NutritionKnowledgePack> knowledge = eligiblePacks();
        List<SeasonalIngredient> inSeason = eligibleSeasonalIngredients();

        UserProfile profile = profile(openid);
        UserFoodPreference preference = validationPreference(openid, profile);
        if (!knowledge.isEmpty() && !inSeason.isEmpty()) {
            // 早餐槽只从早餐组合池选（包子/饺子/面/粥配豆浆牛奶），池子为空时回退全池保证不空窗
            List<Recipe> allPool = topCandidates(rankedCandidates(profile, inSeason, true));
            List<Recipe> breakfastPool = breakfastPool(allPool);
            if (breakfastPool.isEmpty()) breakfastPool = allPool;
            Optional<List<Recipe>> strict = findThree(breakfastPool, allPool,
                    meals -> validator.validate(meals, preference, knowledge).valid());
            if (strict.isPresent()) return save(openid, date, strict.get(), knowledge.get(0).getVersion());
        }

        // 季节数据或知识包是软条件；没有严格组合时，仍返回通过安全、忌口和展示校验的三餐。
        List<Recipe> fallbackBreakfast = breakfastPool(topCandidates(rankedCandidates(profile, inSeason, false)));
        List<Recipe> fallbackAll = topCandidates(rankedCandidates(profile, inSeason, false));
        Optional<List<Recipe>> fallback = findThree(
                fallbackBreakfast.isEmpty() ? fallbackAll : fallbackBreakfast, fallbackAll,
                meals -> validator.isSafe(meals, preference));
        return fallback.flatMap(meals -> save(openid, date, meals,
                knowledge.isEmpty() ? "fallback" : knowledge.get(0).getVersion()));
    }

    public Optional<PlanView> replace(String openid, LocalDate date, int mealIndex) {
        if (mealIndex < 0 || mealIndex > 2) return Optional.empty();
        Optional<DailyMealPlan> stored = plans.findByOpenidAndPlanDate(openid, date);
        if (stored.isEmpty()) return Optional.empty();

        try {
            List<Recipe> meals = new ArrayList<>(json.readValue(stored.get().getPlanJson(),
                    new TypeReference<List<Recipe>>() { }));
            if (meals.size() != 3) return Optional.empty();
            Set<String> existingNames = new HashSet<>(meals.stream().map(Recipe::getName)
                    .filter(Objects::nonNull).toList());
            List<NutritionKnowledgePack> knowledge = eligiblePacks();
            UserProfile profile = profile(openid);
            UserFoodPreference preference = validationPreference(openid, profile);
            // 换早餐时只从早餐组合池换；早餐池为空时回退全池
            List<Recipe> candidates = rankedCandidates(profile, eligibleSeasonalIngredients(), false, mealIndex == 0);
            if (candidates.isEmpty() && mealIndex == 0) {
                candidates = rankedCandidates(profile, eligibleSeasonalIngredients(), false);
            }
            for (Recipe candidate : candidates) {
                if (existingNames.contains(candidate.getName())) continue;
                meals.set(mealIndex, candidate);
                if (validator.isSafe(meals, preference)
                        && (knowledge.isEmpty() || validator.validate(meals, preference, knowledge).valid())) {
                    stored.get().setPlanJson(json.writeValueAsString(meals));
                    plans.save(stored.get());
                    enrichmentCache.remove(date);
                    return Optional.of(enrich(date, List.copyOf(meals),
                            knowledge.isEmpty() ? "fallback" : knowledge.get(0).getVersion()));
                }
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private UserProfile profile(String openid) {
        return memory == null ? UserProfile.empty(openid)
                : memory.profile(openid, AgentMemoryStore.Scene.WEEKLY_PLAN);
    }

    private List<Recipe> rankedCandidates(UserProfile profile, List<SeasonalIngredient> inSeason) {
        return rankedCandidates(profile, inSeason, true);
    }

    private List<Recipe> rankedCandidates(UserProfile profile, List<SeasonalIngredient> inSeason,
                                          boolean requireSeasonalIngredient) {
        return rankedCandidates(profile, inSeason, requireSeasonalIngredient, false);
    }

    /** breakfastOnly=true 时只保留早餐组合池（moodTags 打了「早餐」标签的菜谱）。 */
    private List<Recipe> rankedCandidates(UserProfile profile, List<SeasonalIngredient> inSeason,
                                          boolean requireSeasonalIngredient, boolean breakfastOnly) {
        Set<String> blocked = new HashSet<>();
        blocked.addAll(profile.rejectedDishes());
        blocked.addAll(profile.avoidDishes());
        Set<String> loved = new HashSet<>(profile.lovedDishes());
        Set<String> recent = new HashSet<>(profile.recentDishes());
        return recipePool.all().stream()
                .filter(recipe -> !requireSeasonalIngredient
                        || inSeason.stream().anyMatch(item -> text(recipe).contains(item.getName())))
                .filter(recipe -> !breakfastOnly || isBreakfast(recipe))
                .filter(recipe -> !blocked.contains(recipe.getName()))
                // 优先有真实菜品图（避免卡片空图），再考虑爱吃的菜，最后错开最近吃过的。
                .sorted(Comparator
                        .comparing((Recipe recipe) -> !hasRealImage(recipe))
                        .thenComparing(recipe -> !loved.contains(recipe.getName()))
                        .thenComparing(recipe -> recent.contains(recipe.getName())))
                .toList();
    }

    /** 早餐组合菜谱：moodTags 打了「早餐」标签（由 BreakfastPoolSeeder 生成时写入）。 */
    private boolean isBreakfast(Recipe recipe) {
        return recipe.getMoodTags() != null && recipe.getMoodTags().contains(BreakfastPoolSeeder.BREAKFAST_TAG);
    }

    /** 从已排序的候选里截出早餐组合池。 */
    private List<Recipe> breakfastPool(List<Recipe> ranked) {
        return ranked.stream().filter(this::isBreakfast).toList();
    }

    private List<Recipe> topCandidates(List<Recipe> candidates) {
        if (candidates.size() <= MAX_CANDIDATES) return candidates;
        return new ArrayList<>(candidates.subList(0, MAX_CANDIDATES));
    }

    /**
     * 早餐槽从 breakfastPool 选，午/晚槽从 pool 选，枚举第一个通过校验的组合。
     * breakfastPool 为空时调用方应回退为全池；两池相同时等价于原来的单池枚举。
     */
    private Optional<List<Recipe>> findThree(List<Recipe> breakfastPool, List<Recipe> candidates,
                                             java.util.function.Predicate<List<Recipe>> valid) {
        for (Recipe first : breakfastPool) {
            for (int second = 0; second < candidates.size(); second++) {
                Recipe secondRecipe = candidates.get(second);
                if (sameDish(first, secondRecipe)) continue;
                for (int third = second + 1; third < candidates.size(); third++) {
                    Recipe thirdRecipe = candidates.get(third);
                    if (sameDish(first, thirdRecipe) || sameDish(secondRecipe, thirdRecipe)) continue;
                    List<Recipe> meals = List.of(first, secondRecipe, thirdRecipe);
                    if (valid.test(meals)) return Optional.of(meals);
                }
            }
        }
        return Optional.empty();
    }

    private boolean sameDish(Recipe a, Recipe b) {
        return a == b || (a.getName() != null && a.getName().equals(b.getName()));
    }

    private UserFoodPreference validationPreference(String openid, UserProfile profile) {
        UserFoodPreference preference = preferences.findByOpenid(openid).orElseGet(UserFoodPreference::new);
        preference.setAvoidIngredients(String.join(",", profile.avoidIngredients()));
        preference.setAllergens(String.join(",", profile.allergens()));
        return preference;
    }

    private List<NutritionKnowledgePack> eligiblePacks() {
        return packs.findByEnabledTrueAndLicenseStatus(NutritionKnowledgePack.COMMERCIALLY_USABLE);
    }

    private List<SeasonalIngredient> eligibleSeasonalIngredients() {
        return seasonal.findByEnabledTrueAndLicenseStatus(NutritionKnowledgePack.COMMERCIALLY_USABLE);
    }

    private Optional<PlanView> save(String openid, LocalDate date, List<Recipe> meals, String version) {
        try {
            DailyMealPlan row = new DailyMealPlan();
            row.setOpenid(openid);
            row.setPlanDate(date);
            row.setPlanJson(json.writeValueAsString(meals));
            plans.save(row);
            return Optional.of(enrich(date, meals, version));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private Optional<PlanView> read(DailyMealPlan row) {
        try {
            List<Recipe> meals = json.readValue(row.getPlanJson(), new TypeReference<List<Recipe>>() { });
            return validator.isDisplayable(meals)
                    ? Optional.of(enrich(row.getPlanDate(), meals, "saved"))
                    : Optional.empty();
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    /** 计算（并缓存）当天的 RAG 依据与营养结构，再组装成 PlanView。 */
    private PlanView enrich(LocalDate date, List<Recipe> meals, String version) {
        Enrichment enrichment = enrichmentCache.computeIfAbsent(date, d -> buildEnrichment(meals));
        return new PlanView(date, meals, version, enrichment.guidelineBasis(), enrichment.nutrition());
    }

    private Enrichment buildEnrichment(List<Recipe> meals) {
        List<GuidelinePoint> basis = new ArrayList<>();
        String[] labels = {"早餐", "午餐", "晚餐"};
        for (int i = 0; i < 3; i++) {
            Recipe r = i < meals.size() ? meals.get(i) : null;
            GuidelinePoint point = r == null ? null : topGuidelinePoint(labels[i], r);
            basis.add(point != null ? point : new GuidelinePoint("", "", ""));
        }
        return new Enrichment(basis, buildNutrition(meals));
    }

    /** 针对某一餐检索《中国居民膳食指南》最相关的一条要点，失败返回 null。 */
    private GuidelinePoint topGuidelinePoint(String mealLabel, Recipe r) {
        if (guidelineRag == null || !guidelineRag.isReady()) return null;
        try {
            String guidance = MEAL_GUIDANCE.getOrDefault(mealIndex(mealLabel), "");
            String query = (guidance + " " + (r.getName() == null ? "" : r.getName()) + " "
                    + firstIngredients(r.getIngredients(), 4)).trim();
            List<Map<String, Object>> hits = guidelineRag.search(query, 1);
            if (hits.isEmpty()) return null;
            Map<String, Object> h = hits.get(0);
            String title = str(h.get("title"));
            String content = str(h.get("content"));
            String source = str(h.get("source"));
            if (title.isEmpty() && content.isEmpty()) return null;
            return new GuidelinePoint(title, content, source);
        } catch (Exception ignored) {
            return null;
        }
    }

    private int mealIndex(String label) {
        if ("午餐".equals(label)) return 1;
        if ("晚餐".equals(label)) return 2;
        return 0;
    }

    /** 启发式汇总三餐的营养结构：命中大类即视为覆盖，给一句可追溯的提示。 */
    private NutritionSnapshot buildNutrition(List<Recipe> meals) {
        String allText = meals.stream()
                .map(r -> (r.getName() == null ? "" : r.getName()) + " "
                        + (r.getIngredients() == null ? "" : r.getIngredients()))
                .collect(Collectors.joining(" "));
        List<String> present = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
            for (String kw : entry.getValue()) {
                if (allText.contains(kw)) {
                    present.add(entry.getKey());
                    break;
                }
            }
        }
        List<String> recommended = List.of("谷薯类", "优质蛋白", "蔬菜", "奶类");
        List<String> missing = recommended.stream().filter(c -> !present.contains(c)).toList();
        String headline = "今日三餐覆盖了"
                + (present.isEmpty() ? "基础食材" : String.join("、", present)) + "。";
        String tip;
        if (missing.isEmpty()) {
            tip = "三大类与蔬果奶类都齐了，搭配很完整，继续保持。";
        } else if (missing.size() == 1 && missing.get(0).equals("奶类")) {
            tip = "整体搭配均衡，《中国居民膳食指南》建议每天喝奶，可加一杯牛奶或酸奶。";
        } else {
            tip = "可补充" + String.join("、", missing) + "，《中国居民膳食指南》建议每餐有谷薯、优质蛋白与足量蔬菜。";
        }
        return new NutritionSnapshot(headline, present, tip, goalTag(missing));
    }

    private boolean hasRealImage(Recipe recipe) {
        String img = recipe.getImage();
        // doubaocdn 短链有时效，过期后 403，一律视为无图
        return img != null && !img.isBlank() && !img.startsWith("/static/") && !img.contains("doubaocdn.com");
    }

    private String firstIngredients(String raw, int n) {
        if (raw == null || raw.isBlank()) return "";
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String p : raw.split("[,，;；\n]")) {
            p = p.trim();
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" ");
            sb.append(p);
            if (++count >= n) break;
        }
        return sb.toString();
    }

    private String text(Recipe recipe) {
        return (recipe.getName() == null ? "" : recipe.getName()) + " "
                + (recipe.getIngredients() == null ? "" : recipe.getIngredients());
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    public record GuidelinePoint(String title, String content, String source) { }

    public record NutritionSnapshot(String headline, List<String> tags, String tip, String goalTag) { }

    /** 由缺失大类推导今日真实营养目标，给前端 TIP 标签用；齐了就是「均衡搭配」。 */
    private String goalTag(List<String> missing) {
        boolean missMilk = missing.contains("奶类");
        boolean missVeg = missing.contains("蔬菜");
        boolean missProtein = missing.contains("优质蛋白");
        if (missMilk && missVeg) return "补钙与膳食纤维";
        if (missMilk) return "补钙";
        if (missVeg) return "多来点膳食纤维";
        if (missProtein) return "蛋白质加量";
        if (missing.contains("谷薯类")) return "主食别缺席";
        return "均衡搭配";
    }

    private record Enrichment(List<GuidelinePoint> guidelineBasis, NutritionSnapshot nutrition) { }

    public record PlanView(LocalDate date, List<Recipe> meals, String knowledgePackVersion,
                           List<GuidelinePoint> guidelineBasis, NutritionSnapshot nutrition) { }
}
