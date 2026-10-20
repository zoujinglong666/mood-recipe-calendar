package com.moodrecipe.backend.service;

import com.moodrecipe.backend.config.AppClock;
import com.moodrecipe.backend.entity.DailyMenu;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.entity.RecipeInteraction;
import com.moodrecipe.backend.entity.RecipeFavorite;
import com.moodrecipe.backend.entity.UserFoodPreference;
import com.moodrecipe.backend.entity.UserRecord;
import com.moodrecipe.backend.model.UserProfile;
import com.moodrecipe.backend.agent.MenuPlannerAgent;
import com.moodrecipe.backend.service.GuidelineRagService;
import com.moodrecipe.backend.repository.SeasonalIngredientRepository;
import com.moodrecipe.backend.repository.DailyMenuRepository;
import com.moodrecipe.backend.repository.RecipeInteractionRepository;
import com.moodrecipe.backend.repository.RecipeFavoriteRepository;
import com.moodrecipe.backend.repository.RecipeRepository;
import com.moodrecipe.backend.repository.UserFoodPreferenceRepository;
import com.moodrecipe.backend.repository.UserRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.moodrecipe.backend.service.RecipePool;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 今日菜单（TodayBoard）：v2 的每日钩子。
 * 打开即有的零输入答案——当日缓存行命中直接返回（毫秒级）；
 * 未命中时从菜谱池按「忌口/过敏硬过滤 + 反馈与口味加分 + 近 3 天吃过的去重 + 少量随机」挑一份并落缓存。
 * 本版本不调用 LLM（DAU 早期阶段复杂度优先），LLM 生成留作后续升级。
 */
@Service
public class DailyMenuService implements SwapCandidateService.SwapQueueBuilder {

    private static final ZoneId CHINA_ZONE = AppClock.ZONE;

    private static final int RECENT_DISH_DEDUPE_DAYS = 5;

    /** 今日卡窗口去重天数：近 3 天（含今天）今日卡推荐过的菜不再参与挑选，保证「每天不一样」。 */
    private static final int BOARD_RECENT_DAYS = 3;

    private final DailyMenuRepository dailyMenus;
    private final RecipeRepository recipes;
    private final RecipeInteractionRepository interactions;
    private final UserRecordRepository records;
    private final UserFoodPreferenceRepository preferences;
    private final AllergenNormalizationService allergenNormalization;
    private final RecipePool recipePool;
    private final RecipeFavoriteRepository favorites;
    /** 图片懒生成依赖：可空（测试/降级场景），为空时 ensureCover 直接跳过。 */
    private AgnesRecipeImageService images;
    private CosImageStorageService cosImageStorage;
    /** [2026-10-20] 营养知识（膳食指南 RAG）与时令食材，用于把"健康/时令"真正纳入今日卡打分，而非只作为生成文案。
     *  可选依赖，未注入时今日卡退化为原纯启发式逻辑，不影响主流程。 */
    private GuidelineRagService guidelineRag;
    private SeasonalIngredientRepository seasonalIngredients;
    /** [2026-10-20] 学习引擎：把用户行为聚合成随用随进化的动态画像，今日卡据此"越推越懂"。可选依赖。 */
    private FeedbackLearningService learning;

    public DailyMenuService(DailyMenuRepository dailyMenus, RecipeRepository recipes,
                            RecipeInteractionRepository interactions, UserRecordRepository records,
                            UserFoodPreferenceRepository preferences,
                            AllergenNormalizationService allergenNormalization, RecipePool recipePool,
                            RecipeFavoriteRepository favorites) {
        this.dailyMenus = dailyMenus;
        this.recipes = recipes;
        this.interactions = interactions;
        this.records = records;
        this.preferences = preferences;
        this.allergenNormalization = allergenNormalization;
        this.recipePool = recipePool;
        this.favorites = favorites;
    }

    /** 可选依赖走 setter 注入：保持既有构造器（测试用）签名不变。 */
    @Autowired(required = false)
    void setImageDependencies(AgnesRecipeImageService images, CosImageStorageService cosImageStorage) {
        this.images = images;
        this.cosImageStorage = cosImageStorage;
    }

    /** [2026-10-20] 智能信号依赖走 setter 注入，保持既有构造器（含测试直接 new）签名不变。 */
    @Autowired(required = false)
    void setIntelligenceDependencies(GuidelineRagService guidelineRag, SeasonalIngredientRepository seasonalIngredients) {
        this.guidelineRag = guidelineRag;
        this.seasonalIngredients = seasonalIngredients;
    }

    /** [2026-10-20] 学习引擎走 setter 注入，保持既有构造器签名不变。 */
    @Autowired(required = false)
    void setLearning(FeedbackLearningService learning) {
        this.learning = learning;
    }

    /** [2026-10-20] 换菜预生成引擎：换一道优先消费离线队列，毫秒级返回。可选依赖，为空时退化为现场挑选。 */
    private SwapCandidateService swapCandidates;

    @Autowired(required = false)
    void setSwapCandidates(SwapCandidateService swapCandidates) {
        this.swapCandidates = swapCandidates;
    }

    /** 今日菜单：命中「当日 + 当前餐别」缓存直接返回；跨餐（早→午→晚）自动换菜。挑选失败返回 empty。 */
    public Optional<Board> today(String openid) {
        LocalDate today = AppClock.today();
        String meal = currentMealType();
        Optional<DailyMenu> cached = dailyMenus.findByOpenidAndMenuDate(openid, today);
        if (cached.isPresent() && meal.equals(cached.get().getMealType())) {
            return toBoard(cached.get()).or(() -> create(openid, today, meal));
        }
        // 首次打开或跨餐：排除今天已推过的所有菜，让早/午/晚各吃各的、顿顿不重样
        return create(openid, today, meal);
    }

    /** 换一道：优先消费预生成队列（毫秒级，零排序零候选查询）；未命中走现场挑选兜底并异步补货。 */
    public Optional<Board> refresh(String openid) {
        String meal = currentMealType();
        Set<Long> excludeIds = todaysBoardRecipeIds(openid);
        if (swapCandidates != null) {
            // 验收必须是纯内存判断：打卡近几天菜名 freshly 查一次（索引小查询），保证队列不与最新打卡打架
            Set<String> recentDishes = recentDishNames(openid);
            Optional<Recipe> queued = swapCandidates.next(SwapCandidateService.SCOPE_TODAY_BOARD, openid,
                    SwapCandidateService.ANY_MOOD, meal,
                    recipe -> (recipe.getId() == null || !excludeIds.contains(recipe.getId()))
                            && (recipe.getName() == null || !recentDishes.contains(recipe.getName().trim())));
            if (queued.isPresent()) return record(openid, AppClock.today(), meal, queued.get());
        }
        Recipe picked = pick(openid, preferences.findByOpenid(openid).orElse(null), excludeIds, meal);
        return picked == null ? Optional.empty() : record(openid, AppClock.today(), meal, picked);
    }

    private Optional<Board> create(String openid, LocalDate date, String mealType) {
        Recipe picked = pick(openid, preferences.findByOpenid(openid).orElse(null),
                todaysBoardRecipeIds(openid), mealType);
        return picked == null ? Optional.empty() : record(openid, date, mealType, picked);
    }

    /** 落当日缓存行 + 曝光记录：现场挑选与换菜队列两条路径共用同一写入语义。 */
    private Optional<Board> record(String openid, LocalDate date, String mealType, Recipe picked) {
        Optional<DailyMenu> existing = dailyMenus.findByOpenidAndMenuDate(openid, date);
        DailyMenu row = existing.orElseGet(() -> {
            DailyMenu fresh = new DailyMenu();
            fresh.setOpenid(openid);
            fresh.setMenuDate(date);
            return fresh;
        });
        row.setRecipeId(picked.getId());
        row.setGuozaiLine(guozaiLine(picked, mealType));
        row.setSource("POOL");
        row.setMealType(mealType);
        row.setVariant(existing.map(item -> item.getVariant() + 1).orElse(0));
        dailyMenus.save(row);

        markShown(openid, picked.getId());
        return toBoard(row);
    }

    /** 当前餐别窗口：早餐 5-10 点、午餐 10-16 点、晚餐 16-22 点、其余算夜宵轻食。 */
    public static String mealTypeOf(java.time.LocalDateTime now) {
        int hour = now.getHour();
        if (hour >= 5 && hour < 10) return "BREAKFAST";
        if (hour >= 10 && hour < 16) return "LUNCH";
        if (hour >= 16 && hour < 22) return "DINNER";
        return "LATE";
    }

    private String currentMealType() {
        return mealTypeOf(AppClock.now());
    }

    /** 今天今日卡已推过的全部菜（含此前餐别）：跨餐与「换一道」都不重复。 */
    private Set<Long> todaysBoardRecipeIds(String openid) {
        try {
            return dailyMenus.findByOpenidAndMenuDateGreaterThanEqual(openid, AppClock.today()).stream()
                    .map(DailyMenu::getRecipeId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (RuntimeException e) {
            return Set.of();
        }
    }

    /**
     * 今日卡封面懒生成：今日菜没有真实图（只有锅仔插画兜底）时补拍专属封面并回写菜谱表，
     * 之后所有入口（三餐/周计划/菜谱库）都能复用。失败返回 empty，不影响今日卡展示。
     */
    public Optional<Board> ensureCover(String openid) {
        if (images == null || cosImageStorage == null) return Optional.empty();
        Optional<DailyMenu> row = dailyMenus.findByOpenidAndMenuDate(openid, AppClock.today());
        if (row.isEmpty()) return Optional.empty();
        Recipe recipe = recipes.findById(row.get().getRecipeId()).orElse(null);
        if (recipe == null || hasRealImage(recipe)) return Optional.empty();
        Optional<String> cover = images.generateCover(recipe)
                .flatMap(url -> cosImageStorage.transferFromUrl(url, "recipes/"));
        if (cover.isEmpty()) return Optional.empty();
        recipe.setImage(cover.get());
        recipes.save(recipe);
        return toBoard(row.get());
    }

    /** doubaocdn 短链已过期（403），与 static 插画一样视为无图。 */
    private boolean hasRealImage(Recipe recipe) {
        String img = recipe.getImage();
        return img != null && !img.isBlank() && !img.startsWith("/static/") && !img.contains("doubaocdn.com");
    }

    private Optional<Board> toBoard(DailyMenu row) {
        return recipes.findById(row.getRecipeId())
                .filter(this::isDisplayableRecipe)
                .map(recipe -> new Board(row.getMenuDate().toString(), recipe,
                        row.getGuozaiLine(), row.getVariant(), row.getSource()));
    }

    /**
     * 挑选逻辑：硬过滤（忌口/过敏/明确不喜欢/今天已出/近期吃过/近几天今日卡出过）+ 软加分（反馈/口味/餐别/随机）。
     * 餐别是软偏置而不是硬过滤：早餐池为空时自然降级到普通菜，永不空窗。
     * 近几日今日卡去重是两轮的：第一轮排除；候选被排除光时第二轮放开（保底有菜可推）。
     * 随机权重（0~5）必须足以抗衡单项加分差距，否则"收藏+做过"的高分菜会天天霸榜。
     */
    private Recipe pick(String openid, UserFoodPreference preference, Set<Long> excludeIds, String mealType) {
        List<Recipe> ordered = ranked(openid, preference, excludeIds, mealType);
        return ordered.isEmpty() ? null : ordered.get(0);
    }

    /**
     * 排序内核（与换菜队列预生成共用同一口径）：硬过滤（忌口/过敏/明确不喜欢/今天已出/近期吃过/近几天今日卡出过）
     * + 软加分（反馈/口味/餐别/随机），返回全量降序候选。
     * 单次挑选取队头；换菜队列整列预生成。两处只有这一份排序逻辑，永不漂移。
     * 餐别是软偏置而不是硬过滤：早餐池为空时自然降级到普通菜，永不空窗。
     * 近几日今日卡去重是两轮的：第一轮排除；候选被排除光时第二轮放开（保底有菜可推）。
     * 随机权重（0~5）必须足以抗衡单项加分差距，否则"收藏+做过"的高分菜会天天霸榜。
     */
    private List<Recipe> ranked(String openid, UserFoodPreference preference, Set<Long> excludeIds, String mealType) {
        Set<Long> rejected = interactions.findByOpenidAndAction(openid, "DISLIKE").stream()
                .map(RecipeInteraction::getRecipeId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> recentDishes = recentDishNames(openid);
        List<String> blocked = blockedTerms(preference);
        Set<Long> recentBoard = recentBoardRecipeIds(openid);
        Map<Long, Integer> feedback = new HashMap<>();
        interactions.findTop30ByOpenidOrderByCreatedAtDesc(openid).forEach(interaction ->
                feedback.merge(interaction.getRecipeId(), switch (interaction.getAction()) {
                    case "MADE" -> 5;
                    case "LIKE" -> 4;
                    default -> 0;
                }, Integer::sum));

        // 候选池不再只限「AI 且有图」：AI 有图仅 60 余道且番茄/虾/蛋扎堆，是「天天番茄炒蛋、白灼虾」的直接原因。
        // 阁藏经典里有真实图的菜一并入围（分数上仍偏好有图），食材分布才铺得开；无图菜仍不进池，封面懒生成兜底。
        List<Recipe> pool = new ArrayList<>(recipePool.aiWithImages());
        Set<Long> poolIds = new HashSet<>();
        pool.forEach(r -> { if (r.getId() != null) poolIds.add(r.getId()); });
        for (Recipe r : recipePool.all()) {
            if (r.getId() != null && !poolIds.contains(r.getId()) && hasRealImage(r) && isDisplayableRecipe(r))
                pool.add(r);
        }
        if (pool.isEmpty()) pool = recipePool.all();

        // 近几天今日卡/打卡出现过的核心食材集合：命中过的主料压分，逼着番茄、虾这类高频主料轮流上场
        Set<String> recentIngredients = recentCoreIngredients(openid, recentBoard, pool);

        // [2026-10-20] 一次取出"营养要点关键词"与"当季食材"集合，供候选打分共用，避免逐候选再检索。
        // 这两个集合在 pick 内只算一次（O(候选) 的循环里只做内存 contains），把膳食指南/时令真正变成排序信号。
        Set<String> guidelineFoods = guidelineFoods(mealType);
        Set<String> seasonalFoods = seasonalFoods();
        // [2026-10-20] 取动态用户画像：刚发生的点赞/复做会因事件失效缓存而即时反映到这次排序，
        // 实现"越用越懂"。画像为空（学习引擎未注入）时退化为原启发式逻辑。
        UserProfile profile = (learning != null) ? learning.getProfile(openid) : null;

        // 收藏菜集合（权重×3）
        Set<Long> favoriteIds = favorites.findTop200ByOpenidOrderByCreatedAtDesc(openid).stream()
                .map(RecipeFavorite::getRecipeId).collect(Collectors.toSet());

        for (boolean skipBoardDedupe : new boolean[]{false, true}) {
            List<Recipe> candidates = pool.stream()
                    .filter(this::isDisplayableRecipe)
                    .filter(recipe -> recipe.getName() != null && !recipe.getName().isBlank())
                    .filter(recipe -> !rejected.contains(recipe.getId()))
                    .filter(recipe -> !excludeIds.contains(recipe.getId()))
                    .filter(recipe -> skipBoardDedupe || !recentBoard.contains(recipe.getId()))
                    .filter(recipe -> !recentDishes.contains(recipe.getName().trim()))
                    .filter(recipe -> allowedByPreference(recipe, blocked))
                    .toList();
            if (candidates.isEmpty()) continue;

            return candidates.stream()
                    .sorted(Comparator.comparingInt((Recipe recipe) ->
                            feedback.getOrDefault(recipe.getId(), 0)
                                    + preferenceBonus(recipe, preference)
                                    + quickBonus(recipe)
                                    + profileBonus(recipe, preference)
                                    + mealBonus(recipe, mealType)
                                    + (favoriteIds.contains(recipe.getId()) ? 5 : 0)
                                    + (hasRealImage(recipe) ? 2 : 0)
                                    + ingredientDiversityBonus(recipe, recentIngredients)
                                    + guidelineBonus(recipe, guidelineFoods)
                                    + seasonalBonus(recipe, seasonalFoods)
                                    + profileAffinityBonus(recipe, profile)
                                    + ThreadLocalRandom.current().nextInt(6)).reversed())
                    .toList();
        }
        return List.of();
    }

    // ---- 换菜队列构建器（SwapCandidateService 回调，仅离线调用） ----

    @Override
    public String scope() {
        return SwapCandidateService.SCOPE_TODAY_BOARD;
    }

    /** 预生成换菜队列：与现场挑选同一套排序内核（ranked），硬过滤与去重口径永远一致。 */
    @Override
    public List<Recipe> build(String openid, String mood, String mealType) {
        UserFoodPreference preference = preferences.findByOpenid(openid).orElse(null);
        return ranked(openid, preference, todaysBoardRecipeIds(openid), mealType);
    }

    /**
     * 核心食材关键词 → 归一标签（顺序敏感：长词在前，防止「番茄」被「茄」吞掉、「鸡蛋」被「鸡」吞掉）。
     * 用于首页推荐的食材多样性轮换：近几天出现过的主料压分，让蔬菜、菌菇、豆制品轮着上场。
     * [2026-10-20] 当前为硬编码常量，新增菜系/食材要改代码重发版；应外置到配置/DB，
     * 让运营或数据反馈能直接调整词表与权重，避免"想让时令海鲜更聪明"却只能改源码。
     */
    private static final List<String[]> CORE_INGREDIENT_KEYWORDS = List.of(
            new String[]{"番茄", "番茄"}, new String[]{"西红柿", "番茄"},
            new String[]{"鲜虾", "虾类"}, new String[]{"虾仁", "虾类"}, new String[]{"虾", "虾类"},
            new String[]{"鸡蛋", "蛋类"}, new String[]{"鸭蛋", "蛋类"}, new String[]{"鹌鹑蛋", "蛋类"}, new String[]{"蛋", "蛋类"},
            new String[]{"鸡胸", "鸡肉"}, new String[]{"鸡腿", "鸡肉"}, new String[]{"鸡", "鸡肉"},
            new String[]{"牛肉", "牛肉"}, new String[]{"肥牛", "牛肉"}, new String[]{"牛", "牛肉"},
            new String[]{"猪", "猪肉"}, new String[]{"排骨", "猪肉"}, new String[]{"里脊", "猪肉"},
            new String[]{"三文鱼", "鱼类"}, new String[]{"鱼", "鱼类"},
            new String[]{"豆腐", "豆制品"}, new String[]{"豆干", "豆制品"}, new String[]{"腐竹", "豆制品"}, new String[]{"豆浆", "豆制品"},
            new String[]{"土豆", "土豆"}, new String[]{"红薯", "薯类"}, new String[]{"山药", "薯类"},
            new String[]{"茄子", "茄子"}, new String[]{"青椒", "椒类"}, new String[]{"辣椒", "椒类"}, new String[]{"彩椒", "椒类"},
            new String[]{"黄瓜", "瓜类"}, new String[]{"冬瓜", "瓜类"}, new String[]{"南瓜", "瓜类"}, new String[]{"丝瓜", "瓜类"}, new String[]{"苦瓜", "瓜类"}, new String[]{"西葫芦", "瓜类"},
            new String[]{"白菜", "白菜"}, new String[]{"菠菜", "菠菜"}, new String[]{"芹菜", "芹菜"},
            new String[]{"萝卜", "萝卜"}, new String[]{"藕", "莲藕"}, new String[]{"芦笋", "芦笋"},
            new String[]{"蘑", "菌菇"}, new String[]{"菇", "菌菇"}, new String[]{"木耳", "菌菇"}, new String[]{"竹荪", "菌菇"},
            new String[]{"豆角", "豆角"}, new String[]{"韭菜", "韭菜"}, new String[]{"花菜", "花菜"}, new String[]{"西兰花", "花菜"},
            new String[]{"笋", "笋类"}, new String[]{"海带", "海带"}, new String[]{"紫菜", "海带"},
            new String[]{"面条", "面食"}, new String[]{"面粉", "面食"}, new String[]{"挂面", "面食"}, new String[]{"米粉", "粉面"}, new String[]{"米", "米类"});

    /** 食材轮换惩罚：命中近期吃过的主料每个 -6（大于随机项 0~5，保证轮换真的生效），上限 -12。
     *  [2026-10-20] 魔法数，需随实验调优；应与上面词表一起外置成可配置权重，便于 A/B 与线上微调。 */
    private static final int INGREDIENT_PENALTY = 6;
    private static final int INGREDIENT_PENALTY_CAP = 12;

    /** 菜谱命中的核心食材标签集合（全匹配：番茄炒蛋同时占「番茄」「蛋类」两个轮换位）。 */
    private Set<String> coreIngredientLabels(Recipe recipe) {
        Set<String> labels = new HashSet<>();
        if (recipe == null) return labels;
        String text = (safe(recipe.getName()) + " " + safe(recipe.getIngredients())).toLowerCase();
        for (String[] item : CORE_INGREDIENT_KEYWORDS) {
            if (text.contains(item[0])) labels.add(item[1]);
        }
        return labels;
    }

    /** 近几天今日卡与打卡吃过的核心食材：这些主料接下来几天要被压分，给其他食材让位。 */
    private Set<String> recentCoreIngredients(String openid, Set<Long> recentBoardIds, List<Recipe> pool) {
        Set<String> labels = new HashSet<>();
        Map<Long, Recipe> poolById = new HashMap<>();
        Map<String, Recipe> poolByName = new HashMap<>();
        for (Recipe recipe : pool) {
            if (recipe.getId() != null) poolById.put(recipe.getId(), recipe);
            if (recipe.getName() != null) poolByName.put(recipe.getName().trim(), recipe);
        }
        for (Long id : recentBoardIds) {
            Recipe recipe = poolById.get(id);
            if (recipe != null) labels.addAll(coreIngredientLabels(recipe));
        }
        // 打卡菜名对得上池内菜谱的也计入（换算成菜谱后取食材标签）
        String since = AppClock.today().minusDays(RECENT_DISH_DEDUPE_DAYS).toString();
        records.findTop30ByOpenidOrderByCreatedAtDesc(openid).stream()
                .filter(record -> record.getRecordDate() != null && record.getRecordDate().compareTo(since) >= 0)
                .map(record -> record.getDishName() == null ? "" : record.getDishName().trim())
                .filter(name -> !name.isEmpty())
                .map(poolByName::get)
                .filter(Objects::nonNull)
                .forEach(recipe -> labels.addAll(coreIngredientLabels(recipe)));
        return labels;
    }

    /** 食材多样性加分：命中近期吃过的主料则压分（负值），惩罚随命中数累加但有上限。 */
    private int ingredientDiversityBonus(Recipe recipe, Set<String> recentIngredients) {
        if (recentIngredients.isEmpty()) return 0;
        int penalty = 0;
        for (String label : coreIngredientLabels(recipe)) {
            if (recentIngredients.contains(label)) penalty -= INGREDIENT_PENALTY;
            if (penalty <= -INGREDIENT_PENALTY_CAP) break;
        }
        return penalty;
    }

    /**
     * [2026-10-20] 把《中国居民膳食指南》从"生成文案"升级为"排序信号"。
     * 此前景象：GuidelineRagService 检索结果只注入生成 prompt 当话术，本地今日卡排序完全没用到它（见 GuozaiAgent.guidelinePoints）。
     * 这里用同一检索结果抽取关键词，命中指南强调要多吃的食材/品类的菜加分（上限 +3）。
     * 权重刻意压低，仅作"健康倾向"微调，绝不盖过忌口硬过滤、食材轮换、真实图片等主信号。
     * 注：这是启发式初版，后续应把指南要点结构化（食材级标签）后做强匹配，而非关键词包含。
     */
    private Set<String> guidelineFoods(String mealType) {
        if (guidelineRag == null || !guidelineRag.isReady()) return Set.of();
        try {
            // 今日卡无 mood 维度，查询以餐别 + 通用均衡诉求为主；结果经 @Cacheable 缓存避免重复向量检索
            String query = "膳食搭配 营养均衡 清淡饮食 " + (mealType == null ? "" : mealType);
            List<Map<String, Object>> points = guidelineRag.search(query, 3);
            Set<String> foods = new HashSet<>();
            for (Map<String, Object> p : points) {
                Object tags = p.get("tags");
                if (tags instanceof List<?> list) {
                    for (Object t : list) {
                        String s = t == null ? "" : t.toString().trim().toLowerCase();
                        if (s.length() >= 2) foods.add(s);
                    }
                }
            }
            return foods;
        } catch (RuntimeException e) {
            return Set.of();
        }
    }

    private int guidelineBonus(Recipe recipe, Set<String> foods) {
        if (foods.isEmpty()) return 0;
        String text = (safe(recipe.getName()) + " " + safe(recipe.getIngredients()) + " " + safe(recipe.getTags())).toLowerCase();
        int bonus = 0;
        for (String f : foods) {
            if (text.contains(f)) {
                bonus += 2;
                if (bonus >= 3) break;
            }
        }
        return bonus;
    }

    /**
     * [2026-10-20] 把"时令"从运营位展示变成排序信号。此前 WeeklyThemeService 只在前端展示"当季正好吃…"，
     * 没有回灌到今日卡排序。这里取当季可商用食材名集合（SeasonalIngredientRepository 已 @Cacheable 缓存），
     * 含当季食材的菜加分，落实"什么季节吃什么"。
     */
    private Set<String> seasonalFoods() {
        if (seasonalIngredients == null) return Set.of();
        try {
            int month = AppClock.today().getMonthValue();
            return seasonalIngredients.findByEnabledTrueAndLicenseStatus("COMMERCIALLY_USABLE").stream()
                    .filter(item -> inSeason(item.getStartMonth(), item.getEndMonth(), month))
                    .map(item -> item.getName() == null ? "" : item.getName().trim())
                    .filter(name -> !name.isEmpty())
                    .collect(Collectors.toSet());
        } catch (RuntimeException e) {
            return Set.of();
        }
    }

    private boolean inSeason(Integer start, Integer end, int month) {
        if (start == null || end == null) return false;
        return start <= end ? month >= start && month <= end : month >= start || month <= end;
    }

    private int seasonalBonus(Recipe recipe, Set<String> seasonalFoods) {
        if (seasonalFoods.isEmpty()) return 0;
        String text = (safe(recipe.getName()) + " " + safe(recipe.getIngredients())).toLowerCase();
        int bonus = 0;
        for (String f : seasonalFoods) {
            if (text.contains(f.toLowerCase())) {
                bonus += 2;
                if (bonus >= 4) break;
            }
        }
        return bonus;
    }

    /**
     * [2026-10-20] 把"学习到的用户画像"变成排序加分——这是"越用越智能"在今日卡上最直接的落点。
     * 喜欢的食材/标签/口味得正分、厌恶的得负分（自动避开），且分数乘以 confidence：
     * 新用户样本少时权重低（信显式偏好与通用启发式），数据越多权重越高（真·个性化）。
     * 另叠加难度/时长倾向微调，让推荐贴合用户真实做饭能力。
     */
    private int profileAffinityBonus(Recipe recipe, UserProfile profile) {
        if (profile == null || learning == null) return 0;
        double raw = learning.affinityScore(recipe, profile) * profile.getConfidence();
        int bonus = (int) Math.max(-15, Math.min(15, raw * 3));
        if (profile.getDurationTendencyMinutes() > 0 && recipe.getCookingTime() != null
                && recipe.getCookingTime() <= profile.getDurationTendencyMinutes()) bonus += 2;
        if (profile.getDifficultyTendency() != null
                && profile.getDifficultyTendency().equalsIgnoreCase(recipe.getDifficulty())) bonus += 1;
        return bonus;
    }

    /** 早餐语境关键词：菜名/标签命中即视为早餐组合（与 BreakfastPoolSeeder 的「主食 · 配饮品」池同口径）。 */
    private static final List<String> BREAKFAST_KEYWORDS = List.of(
            "粥", "豆浆", "牛奶", "包子", "馒头", "蒸饺", "煎饺", "烧麦", "肠粉", "米粉",
            "汤面", "蛋饼", "鸡蛋饼", "燕麦", "吐司", "三明治", "蒸蛋", "煎蛋", "馄饨", "早餐");

    /** 餐别偏置：早/午/晚/夜宵各有各的吃法——这是「打开就对」的智能感来源。软偏置，不硬过滤。 */
    static int mealBonus(Recipe recipe, String mealType) {
        String name = recipe.getName() == null ? "" : recipe.getName();
        String tags = recipe.getTags() == null ? "" : recipe.getTags();
        String taste = recipe.getTaste() == null ? "" : recipe.getTaste();
        String moodTags = recipe.getMoodTags() == null ? "" : recipe.getMoodTags();
        String headline = (name + " " + tags).toLowerCase();
        boolean breakfastish = moodTags.contains("早餐")
                || BREAKFAST_KEYWORDS.stream().anyMatch(headline::contains);
        boolean heavy = headline.contains("油炸") || headline.contains("烧烤") || headline.contains("麻辣")
                || headline.contains("干锅") || headline.contains("烤肉");
        boolean light = taste.contains("清淡") || taste.contains("鲜")
                || headline.contains("蒸") || headline.contains("炖") || headline.contains("汤")
                || headline.contains("白灼") || headline.contains("凉拌");

        return switch (mealType == null ? "" : mealType) {
            // 早餐：早餐组合强势加分，正餐菜压分；早餐池为空时靠软偏置自然降级，不空窗
            case "BREAKFAST" -> breakfastish ? 8 : -6;
            // 午餐：正餐主场，家常炒菜小幅加分，油炸烧烤中午也压一手
            case "LUNCH" -> (headline.contains("炒") || headline.contains("家常") ? 2 : 0) - (heavy ? 2 : 0);
            // 晚餐：清淡蒸炖汤加分，重油重辣压分
            case "DINNER" -> (light ? 3 : 0) - (heavy ? 3 : 0);
            // 夜宵：只留轻的，粥汤蛋羹最合适
            case "LATE" -> (headline.contains("粥") || headline.contains("汤") || headline.contains("蒸蛋")
                    || headline.contains("面") || light ? 4 : 0) - (heavy || (name.contains("炒") && !light) ? 3 : 0);
            default -> 0;
        };
    }

    /** 近几日今日卡已推荐过的菜：连续多天同菜是今日卡最大的体验杀手。查询失败静默放行。 */
    private Set<Long> recentBoardRecipeIds(String openid) {
        try {
            return dailyMenus.findByOpenidAndMenuDateGreaterThanEqual(
                            openid, AppClock.today().minusDays(BOARD_RECENT_DAYS - 1L)).stream()
                    .map(DailyMenu::getRecipeId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (RuntimeException e) {
            return Set.of();
        }
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
        String since = AppClock.today().minusDays(RECENT_DISH_DEDUPE_DAYS).toString();
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

    /** 快手菜加分：25分钟内能做好的菜额外加分，用户忙的时候更友好。 */
    private int quickBonus(Recipe recipe) {
        Integer t = recipe.getCookingTime();
        if (t != null && t > 0 && t <= 25) return 2;
        return 0;
    }

    /** 口味Profile匹配：根据用户健康目标和辣度偏好加权。 */
    private int profileBonus(Recipe recipe, UserFoodPreference preference) {
        if (preference == null) return 0;
        int bonus = 0;
        String taste = safe(recipe.getTaste());
        String tags = safe(recipe.getTags());
        String difficulty = safe(recipe.getDifficulty());

        // 健康目标匹配
        String goal = preference.getHealthGoal();
        if ("LIGHT".equals(goal) || "FAT_LOSS".equals(goal)) {
            // 清淡/减脂模式：清淡口味、低油加分
            if (taste.contains("清淡") || taste.contains("鲜")) bonus += 2;
            if (difficulty.equalsIgnoreCase("easy")) bonus += 1;
        }
        if ("BALANCED".equals(goal)) {
            bonus += 1; // 均衡模式不偏不倚
        }

        // 辣度偏好匹配
        String spice = preference.getSpiceLevel();
        if ("SPICY".equals(spice)) {
            if (taste.contains("辣")) bonus += 3;
        } else if ("MILD".equals(spice)) {
            if (taste.contains("清淡") || taste.contains("甜")) bonus += 2;
        }

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

    /** 锅仔今日一句话：分餐别模板 + 轻随机——早上是早饭话术，晚上是晚餐话术，智能感落在每一句话里。 */
    private String guozaiLine(Recipe recipe, String mealType) {
        String name = safe(recipe.getName());
        String minutes = recipe.getCookingTime() != null && recipe.getCookingTime() > 0
                ? "，" + recipe.getCookingTime() + " 分钟就能端上桌" : "";
        String[][] pools = {
                // 早餐
                {"早餐不用纠结，锅仔替你定了：" + name + minutes,
                        "一日之计在于晨，" + name + " 给今天开个好头",
                        "起床就有主意了——" + name + "，锅仔早上就想到了你",
                        "早饭要认真吃，这份 " + name + " 已经帮你留好啦" + minutes},
                // 午餐
                {"中午这顿 " + name + " 正合适" + minutes,
                        "今天不用纠结，锅仔替你定了：" + name + minutes,
                        "就它了——" + name + "，锅仔觉得和今天很配",
                        "干饭人辛苦了，这道 " + name + " 给你充充电",
                        "一个人吃也要认真，" + name + " 安排上了"},
                // 晚餐
                {"今晚 " + name + "，吃过再说" + minutes,
                        "晚上吃点舒坦的，" + name + " 刚刚好",
                        "今天这份 " + name + " 已经帮你留好啦" + minutes,
                        "忙碌一天了，" + name + " 犒劳一下自己"},
                // 夜宵轻食
                {"这个点就吃口轻的：锅仔留了 " + name,
                        "夜里别糊弄，" + name + " 温温柔柔刚刚好",
                        "饿了就吃，" + name + " 不给肠胃添负担"},
        };
        String[] lines = switch (mealType == null ? "" : mealType) {
            case "BREAKFAST" -> pools[0];
            case "LUNCH" -> pools[1];
            case "DINNER" -> pools[2];
            default -> pools[3];
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
