package com.moodrecipe.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.agent.AgentMemoryStore;
import com.moodrecipe.backend.agent.UserProfile;
import com.moodrecipe.backend.config.AppClock;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class DailyMealPlanService implements SwapCandidateService.SwapQueueBuilder {
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
    /** 图片懒生成依赖：可空（测试/降级场景），为空时 ensureCover 直接跳过。 */
    private AgnesRecipeImageService images;
    private CosImageStorageService cosImageStorage;

    /** 候选集封顶：findThree 是 O(n³) 暴力枚举，菜谱表稍大就会让请求挂死（线上曾因 AI 灌入数百道菜而 60s 超时）。取排序后的前若干道即可把组合数收敛为常数级，且前三道偏好已排序不妨碍质量。 */
    private static final int MAX_CANDIDATES = 120;

    /** 三餐计划跨天去重天数：近 3 天（不含今天）三餐计划出过的菜不再参与生成，保证「连着几天吃的不重样」。 */
    private static final int PLAN_RECENT_DAYS = 3;

    /**
     * [2026-10-20] 每餐的指南依据按「日期+餐序+菜名」缓存（替代原按天整包缓存）：
     * 换菜只重算被换的那一餐的向量检索，未动的两餐直接复用——这是三餐换菜从秒级降到毫秒级的关键一环
     * （换菜前 enrich 要同步打 3 次 RAG，现在命中时 0 次、未命中时 1 次）。
     */
    private final Map<String, java.util.Optional<GuidelinePoint>> guidelinePointCache = new ConcurrentHashMap<>();
    /** 缓存容量兜底：按天×餐×换菜轮次累积，超限整体清空（最坏情况是下一轮多打几次向量检索）。 */
    private static final int GUIDELINE_CACHE_MAX = 4096;

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

    /** 可选依赖走 setter 注入：保持既有构造器（测试用）签名不变。 */
    @Autowired(required = false)
    void setImageDependencies(AgnesRecipeImageService images, CosImageStorageService cosImageStorage) {
        this.images = images;
        this.cosImageStorage = cosImageStorage;
    }

    /** [2026-10-20] 换菜预生成引擎：换菜优先消费离线队列，毫秒级返回。可选依赖，为空时退化为现场排序。 */
    private SwapCandidateService swapCandidates;

    @Autowired(required = false)
    void setSwapCandidates(SwapCandidateService swapCandidates) {
        this.swapCandidates = swapCandidates;
    }

    public Optional<PlanView> plan(String openid, LocalDate date) {
        Optional<DailyMealPlan> existing = plans.findByOpenidAndPlanDate(openid, date);
        if (existing.isPresent()) return read(existing.get());
        return generate(openid, date, Set.of());
    }

    /**
     * 换一整天：删除当日旧计划后重新生成整包三餐，新选的菜避开「当天已出过的菜」，
     * 避免「换了个寂寞」。额度（普通 1 / 会员 3 / 看广告无限）由 Controller 层消费，此处只重选三餐。
     */
    public Optional<PlanView> reshuffle(String openid, LocalDate date) {
        Set<String> currentNames = Set.of();
        Optional<DailyMealPlan> stored = plans.findByOpenidAndPlanDate(openid, date);
        if (stored.isPresent()) {
            try {
                currentNames = json.readValue(stored.get().getPlanJson(), new TypeReference<List<Recipe>>() { })
                        .stream().map(Recipe::getName).filter(Objects::nonNull)
                        .collect(Collectors.toSet());
            } catch (Exception ignored) { }
            plans.delete(stored.get());
        }
        return generate(openid, date, currentNames);
    }

    /** 三餐生成内核：strict 走营养校验，fallback 只走安全/忌口校验。extraExclude 额外排除的菜名（如当天已出过的）。 */
    private Optional<PlanView> generate(String openid, LocalDate date, Set<String> extraExclude) {
        List<NutritionKnowledgePack> knowledge = eligiblePacks();
        List<SeasonalIngredient> inSeason = eligibleSeasonalIngredients();

        UserProfile profile = profile(openid);
        UserFoodPreference preference = validationPreference(openid, profile);
        Set<String> recentPlanNames = new HashSet<>(recentPlanDishNames(openid, date));
        recentPlanNames.addAll(extraExclude);

        if (!knowledge.isEmpty() && !inSeason.isEmpty()) {
            // 早餐槽只从早餐组合池选（包子/饺子/面/粥配豆浆牛奶），池子为空时回退全池保证不空窗
            List<Recipe> allPool = topCandidates(rankedCandidates(profile, inSeason, true, false, recentPlanNames));
            if (allPool.isEmpty()) allPool = topCandidates(rankedCandidates(profile, inSeason, true));
            List<Recipe> breakfastPool = breakfastPool(allPool);
            if (breakfastPool.isEmpty()) breakfastPool = allPool;
            Optional<List<Recipe>> strict = findThree(breakfastPool, allPool,
                    meals -> validator.validate(meals, preference, knowledge).valid());
            if (strict.isPresent()) return save(openid, date, strict.get(), knowledge.get(0).getVersion());
        }

        // 季节数据或知识包是软条件；没有严格组合时，仍返回通过安全、忌口和展示校验的三餐。
        List<Recipe> fallbackBreakfast = breakfastPool(topCandidates(rankedCandidates(profile, inSeason, false, false, recentPlanNames)));
        if (fallbackBreakfast.isEmpty()) fallbackBreakfast = breakfastPool(topCandidates(rankedCandidates(profile, inSeason, false)));
        List<Recipe> fallbackAll = topCandidates(rankedCandidates(profile, inSeason, false, false, recentPlanNames));
        if (fallbackAll.isEmpty()) fallbackAll = topCandidates(rankedCandidates(profile, inSeason, false));
        Optional<List<Recipe>> fallback = findThree(
                fallbackBreakfast.isEmpty() ? fallbackAll : fallbackBreakfast, fallbackAll,
                meals -> validator.isSafe(meals, preference));
        return fallback.flatMap(meals -> save(openid, date, meals,
                knowledge.isEmpty() ? "fallback" : knowledge.get(0).getVersion()));
    }

    /**
     * 三餐换菜，双路径：
     *  毫秒级热路径——候选来自离线预生成队列（SwapCandidateService，按画像签名隔离），
     *  请求内只做「出队 + 内存校验（安全/忌口/营养）」，校验不过自动滑到队列下一个；
     *  慢路径兜底——队列未命中（冷启动/刚失效/耗尽）时现场排序一次，同时队列已在后台补货。
     * 热路径校验用显式忌口行（权威安全面）；行为学习到的厌恶（DISLIKE 菜名等）已在队列构建的
     * 硬过滤层整列剔除，且签名随画像变化换血，不会 serving 过期的个性化结果。
     */
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
            UserFoodPreference preference = preferences.findByOpenid(openid).orElseGet(UserFoodPreference::new);

            if (swapCandidates != null) {
                String mealType = mealIndex == 0 ? "BREAKFAST" : "MAIN";
                Optional<Recipe> queued = swapCandidates.next(SwapCandidateService.SCOPE_MEAL_PLAN, openid,
                        SwapCandidateService.ANY_MOOD, mealType,
                        candidate -> acceptable(meals, mealIndex, existingNames, preference, knowledge, candidate));
                if (queued.isPresent()) {
                    stored.get().setPlanJson(json.writeValueAsString(meals));
                    plans.save(stored.get());
                    return Optional.of(enrich(date, List.copyOf(meals), knowledgeVersion(knowledge)));
                }
            }

            UserProfile profile = profile(openid);
            UserFoodPreference validationPreference = validationPreference(openid, profile);
            // 换早餐时只从早餐组合池换；早餐池为空时回退全池；跨天去重兜底同 plan()
            Set<String> recentPlanNames = recentPlanDishNames(openid, stored.get().getPlanDate());
            List<Recipe> candidates = rankedCandidates(profile, eligibleSeasonalIngredients(), false, mealIndex == 0, recentPlanNames);
            if (candidates.isEmpty() && mealIndex == 0) {
                candidates = rankedCandidates(profile, eligibleSeasonalIngredients(), false, false, recentPlanNames);
            }
            if (candidates.isEmpty()) {
                candidates = rankedCandidates(profile, eligibleSeasonalIngredients(), false);
            }
            for (Recipe candidate : candidates) {
                if (existingNames.contains(candidate.getName())) continue;
                meals.set(mealIndex, candidate);
                if (validator.isSafe(meals, validationPreference)
                        && (knowledge.isEmpty() || validator.validate(meals, validationPreference, knowledge).valid())) {
                    stored.get().setPlanJson(json.writeValueAsString(meals));
                    plans.save(stored.get());
                    return Optional.of(enrich(date, List.copyOf(meals), knowledgeVersion(knowledge)));
                }
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    /** 换菜候选验收（纯内存）：不与当前三餐重名 + 整包安全/忌口/营养校验通过。 */
    private boolean acceptable(List<Recipe> meals, int mealIndex, Set<String> existingNames,
                               UserFoodPreference preference, List<NutritionKnowledgePack> knowledge,
                               Recipe candidate) {
        if (candidate.getName() == null || existingNames.contains(candidate.getName())) return false;
        meals.set(mealIndex, candidate);
        return validator.isSafe(meals, preference)
                && (knowledge.isEmpty() || validator.validate(meals, preference, knowledge).valid());
    }

    private String knowledgeVersion(List<NutritionKnowledgePack> knowledge) {
        return knowledge.isEmpty() ? "fallback" : knowledge.get(0).getVersion();
    }

    // ---- 换菜队列构建器（SwapCandidateService 回调，仅离线调用） ----

    @Override
    public String scope() {
        return SwapCandidateService.SCOPE_MEAL_PLAN;
    }

    /** 预生成换菜队列：与慢路径同一套 rankedCandidates 口径，含跨天去重与早餐池约束。 */
    @Override
    public List<Recipe> build(String openid, String mood, String mealType) {
        boolean breakfastOnly = "BREAKFAST".equals(mealType);
        UserProfile profile = profile(openid);
        Set<String> recentPlanNames = recentPlanDishNames(openid, AppClock.today());
        List<Recipe> candidates = rankedCandidates(profile, eligibleSeasonalIngredients(), false, breakfastOnly, recentPlanNames);
        if (candidates.isEmpty() && breakfastOnly) {
            candidates = rankedCandidates(profile, eligibleSeasonalIngredients(), false, false, recentPlanNames);
        }
        return topCandidates(candidates);
    }

    /**
     * 三餐卡封面懒生成：该餐没有真实图（只有锅仔插画兜底）时补拍专属封面，
     * 成功后同时回写菜谱表（之后的计划与今日卡都能复用）和当日 planJson。失败返回 empty，不影响浏览。
     */
    public Optional<PlanView> ensureCover(String openid, LocalDate date, int mealIndex) {
        if (images == null || cosImageStorage == null) return Optional.empty();
        Optional<DailyMealPlan> stored = plans.findByOpenidAndPlanDate(openid, date);
        if (stored.isEmpty()) return Optional.empty();
        try {
            List<Recipe> meals = new ArrayList<>(json.readValue(stored.get().getPlanJson(),
                    new TypeReference<List<Recipe>>() { }));
            if (mealIndex < 0 || mealIndex >= meals.size()) return Optional.empty();
            Recipe meal = meals.get(mealIndex);
            if (hasRealImage(meal)) return Optional.of(enrich(date, List.copyOf(meals), "saved"));
            Optional<String> cover = images.generateCover(meal)
                    .flatMap(url -> cosImageStorage.transferFromUrl(url, "recipes/"));
            if (cover.isEmpty()) return Optional.empty();
            meal.setImage(cover.get());
            recipes.save(meal);
            // 生图耗时 30-90s，期间用户可能已「换一道」。落库前重读最新 planJson，
            // 只把图片写回同一道菜（按菜名匹配且仍无图），避免整包覆盖丢失并发更新；
            // 该餐已换成别的菜时只保留菜谱表里的封面，不回写当日计划。
            DailyMealPlan latest = plans.findByOpenidAndPlanDate(openid, date).orElse(stored.get());
            List<Recipe> latestMeals = new ArrayList<>(json.readValue(latest.getPlanJson(),
                    new TypeReference<List<Recipe>>() { }));
            boolean applied = false;
            for (Recipe item : latestMeals) {
                if (item.getName() != null && item.getName().equals(meal.getName()) && !hasRealImage(item)) {
                    item.setImage(cover.get());
                    applied = true;
                    break;
                }
            }
            if (applied) {
                latest.setPlanJson(json.writeValueAsString(latestMeals));
                plans.save(latest);
                return Optional.of(enrich(date, List.copyOf(latestMeals), "saved"));
            }
            return Optional.of(enrich(date, latestMeals, "saved"));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private UserProfile profile(String openid) {
        return memory == null ? UserProfile.empty(openid)
                : memory.profile(openid, AgentMemoryStore.Scene.WEEKLY_PLAN);
    }

    private List<Recipe> rankedCandidates(UserProfile profile, List<SeasonalIngredient> inSeason,
                                          boolean requireSeasonalIngredient) {
        return rankedCandidates(profile, inSeason, requireSeasonalIngredient, false, Set.of());
    }

    /** breakfastOnly=true 时只保留早餐组合池（moodTags 打了「早餐」标签的菜谱）。excludeNames 为跨天去重集合。 */
    private List<Recipe> rankedCandidates(UserProfile profile, List<SeasonalIngredient> inSeason,
                                          boolean requireSeasonalIngredient, boolean breakfastOnly,
                                          Set<String> excludeNames) {
        Set<String> blocked = new HashSet<>();
        blocked.addAll(profile.rejectedDishes());
        blocked.addAll(profile.avoidDishes());
        Set<String> loved = new HashSet<>(profile.lovedDishes());
        Set<String> recent = new HashSet<>(profile.recentDishes());
        List<Recipe> candidates = recipePool.all().stream()
                .filter(recipe -> !requireSeasonalIngredient
                        || inSeason.stream().anyMatch(item -> text(recipe).contains(item.getName())))
                .filter(recipe -> !breakfastOnly || isBreakfast(recipe))
                .filter(recipe -> !blocked.contains(recipe.getName()))
                .filter(recipe -> recipe.getName() == null || !excludeNames.contains(recipe.getName().trim()))
                .toList();
        // [2026-10-27] 末位随机决胜必须"每候选定一次"再排序：comparator 的 keyExtractor
        // 会对同一元素多次求值，内嵌 ThreadLocalRandom 让同一元素两次比较互相矛盾，
        // TimSort 抛契约异常（与 DailyMenuService 换菜队列批量失败同根因）。
        // 优先有真实菜品图（避免卡片空图），再考虑爱吃的菜，最后错开最近吃过的；
        // 前三档相同者按预生成的随机序打散，组合才不会天天一样。
        Map<Recipe, Integer> tieBreak = new java.util.IdentityHashMap<>();
        candidates.forEach(recipe -> tieBreak.put(recipe, ThreadLocalRandom.current().nextInt()));
        return candidates.stream()
                .sorted(Comparator
                        .comparing((Recipe recipe) -> !hasRealImage(recipe))
                        .thenComparing(recipe -> !loved.contains(recipe.getName()))
                        .thenComparing(recipe -> recent.contains(recipe.getName()))
                        .thenComparing(recipe -> tieBreak.get(recipe)))
                .toList();
    }

    /**
     * 近几天三餐计划出过的全部菜名（不含 date 当天——当天计划允许「换回去」）。
     * 这是确定性算法下「连着几天同一份三餐」的关键解药：计划结果虽不回流打卡记录，
     * 但直接从历史 planJson 反查即可闭环。查询/解析失败静默放行，不影响出餐。
     */
    private Set<String> recentPlanDishNames(String openid, LocalDate date) {
        try {
            Set<String> names = new HashSet<>();
            for (DailyMealPlan row : plans.findByOpenidAndPlanDateGreaterThanEqual(
                    openid, date.minusDays(PLAN_RECENT_DAYS - 1L))) {
                if (row.getPlanDate() == null || !row.getPlanDate().isBefore(date)) continue;
                try {
                    for (Recipe meal : json.readValue(row.getPlanJson(), new TypeReference<List<Recipe>>() { })) {
                        if (meal.getName() != null && !meal.getName().isBlank()) names.add(meal.getName().trim());
                    }
                } catch (Exception ignored) {
                    // 单行 planJson 损坏只影响这一行的去重，不放大为整体失败
                }
            }
            return names;
        } catch (RuntimeException e) {
            return Set.of();
        }
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

    /** 组装 PlanView：每餐指南依据走（日期+餐序+菜名）粒度缓存，营养结构为纯内存启发式实时计算。 */
    private PlanView enrich(LocalDate date, List<Recipe> meals, String version) {
        List<GuidelinePoint> basis = new ArrayList<>();
        String[] labels = {"早餐", "午餐", "晚餐"};
        for (int i = 0; i < 3; i++) {
            Recipe r = i < meals.size() ? meals.get(i) : null;
            GuidelinePoint point = r == null ? null : cachedGuidelinePoint(date, i, labels[i], r);
            basis.add(point != null ? point : new GuidelinePoint("", "", ""));
        }
        return new PlanView(date, meals, version, basis, buildNutrition(meals));
    }

    /** 单餐指南依据缓存：换菜后仅被换餐位的菜名变化，只有它触发一次新的向量检索。 */
    private GuidelinePoint cachedGuidelinePoint(LocalDate date, int mealIndex, String label, Recipe recipe) {
        if (guidelinePointCache.size() > GUIDELINE_CACHE_MAX) guidelinePointCache.clear();
        String key = date + "|" + mealIndex + "|" + recipe.getName();
        return guidelinePointCache.computeIfAbsent(key,
                k -> java.util.Optional.ofNullable(topGuidelinePoint(label, recipe))).orElse(null);
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

    public record PlanView(LocalDate date, List<Recipe> meals, String knowledgePackVersion,
                           List<GuidelinePoint> guidelineBasis, NutritionSnapshot nutrition) { }
}
