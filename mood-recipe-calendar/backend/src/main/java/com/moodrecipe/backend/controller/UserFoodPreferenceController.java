package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.agent.AgentMemoryStore;
import com.moodrecipe.backend.entity.UserFoodPreference;
import com.moodrecipe.backend.repository.UserFoodPreferenceRepository;
import com.moodrecipe.backend.repository.RecipeInteractionRepository;
import com.moodrecipe.backend.repository.RecommendationExposureRepository;
import com.moodrecipe.backend.service.GuozaiMemory;
import com.moodrecipe.backend.service.AllergenNormalizationService;
import com.moodrecipe.backend.service.nutrition.NutritionTargetService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Set;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/preferences")
public class UserFoodPreferenceController {
    private static final Set<String> SPICE_LEVELS = Set.of("NONE", "MILD", "NORMAL", "HOT");
    private static final Set<String> HEALTH_GOALS = Set.of("BALANCED", "FITNESS", "LEAN");
    private static final Set<String> COOKING_SKILLS = Set.of("NOVICE", "HOME", "PRO");
    private static final Set<String> GENDERS = Set.of("MALE", "FEMALE");
    private static final Set<String> ACTIVITY_LEVELS = Set.of("INACTIVE", "LOW_ACTIVE", "ACTIVE", "VERY_ACTIVE");
    private static final Set<String> MEAL_TIMINGS = Set.of("REGULAR", "NO_BREAKFAST", "LATE_NIGHT", "INTERMITTENT");
    private final UserFoodPreferenceRepository repository;
    private final RecipeInteractionRepository interactions;
    private final RecommendationExposureRepository exposures;
    private final GuozaiMemory memory;
    private final AllergenNormalizationService allergenNormalizer;

    /** [2026-10-28] EER 目标计算。可选依赖走 setter 注入，保持构造器（测试）签名不变。 */
    private NutritionTargetService nutritionTargets;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setNutritionTargets(NutritionTargetService nutritionTargets) {
        this.nutritionTargets = nutritionTargets;
    }

    /** [2026-10-27] 记忆库（问卷双轨写入用）。可选依赖走 setter 注入，保持构造器（测试）签名不变。 */
    private AgentMemoryStore memoryStore;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setMemoryStore(AgentMemoryStore memoryStore) {
        this.memoryStore = memoryStore;
    }

    public UserFoodPreferenceController(UserFoodPreferenceRepository repository,
                                        RecipeInteractionRepository interactions,
                                        RecommendationExposureRepository exposures,
                                        GuozaiMemory memory,
                                        AllergenNormalizationService allergenNormalizer) {
        this.repository = repository;
        this.interactions = interactions;
        this.exposures = exposures;
        this.memory = memory;
        this.allergenNormalizer = allergenNormalizer;
    }

    @GetMapping
    public ApiResponse<UserFoodPreference> get(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        return ApiResponse.ok(repository.findByOpenid(openid).orElseGet(UserFoodPreference::new));
    }

    @GetMapping("/summary")
    public ApiResponse<FoodMemoryView> summary(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        UserFoodPreference explicit = repository.findByOpenid(openid).orElseGet(UserFoodPreference::new);
        GuozaiMemory.MemorySnapshot snapshot = memory.snapshot(openid, LocalTime.now().getHour());
        List<com.moodrecipe.backend.entity.RecipeInteraction> recent =
                interactions.findTop30ByOpenidOrderByCreatedAtDesc(openid);
        BehaviorMemory behavior = new BehaviorMemory(
                snapshot.topDish(), snapshot.topMood(), snapshot.streak(), snapshot.recordedToday(),
                count(recent, "LIKE"), count(recent, "DISLIKE"), count(recent, "MADE"));
        return ApiResponse.ok(new FoodMemoryView(explicit, behavior));
    }

    @PutMapping
    public ApiResponse<UserFoodPreference> save(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @RequestBody PreferenceRequest request) {
        if (request == null || !SPICE_LEVELS.contains(request.spiceLevel())) {
            return ApiResponse.error(400, "辣度选择无效");
        }
        String healthGoal = request.healthGoal() == null || request.healthGoal().isBlank()
                ? "BALANCED" : request.healthGoal();
        if (!HEALTH_GOALS.contains(healthGoal)) {
            return ApiResponse.error(400, "健康目标选择无效");
        }
        if (tooLong(request.favoriteTags()) || tooLong(request.favoriteCuisines()) || tooLong(request.favoriteDishes())
                || tooLong(request.avoidIngredients()) || tooLong(request.allergens())) {
            return ApiResponse.error(400, "口味内容不能超过 500 字");
        }
        if (request.hometown() != null && request.hometown().length() > 100) {
            return ApiResponse.error(400, "家乡填写太长啦");
        }
        String cookingSkill = request.cookingSkill() == null || request.cookingSkill().isBlank()
                ? "HOME" : request.cookingSkill();
        if (!COOKING_SKILLS.contains(cookingSkill)) {
            return ApiResponse.error(400, "做菜水平选择无效");
        }
        // [2026-10-28] 身体数据：非法值直接 400（宁可拒收不静默改写），与 onboarding 同一套校验
        if (request.gender() != null && !GENDERS.contains(request.gender())) {
            return ApiResponse.error(400, "性别选择无效");
        }
        if (request.activityLevel() != null && !ACTIVITY_LEVELS.contains(request.activityLevel())) {
            return ApiResponse.error(400, "活动水平选择无效");
        }
        if (request.mealTiming() != null && !MEAL_TIMINGS.contains(request.mealTiming())) {
            return ApiResponse.error(400, "进食节奏选择无效");
        }
        Integer bodyAge = clampRange(request.age(), 10, 100);
        java.math.BigDecimal bodyHeight = clampDecimal(request.heightCm(), 80, 250);
        java.math.BigDecimal bodyWeight = clampDecimal(request.weightKg(), 20, 300);
        UserFoodPreference preference = repository.findByOpenid(openid).orElseGet(UserFoodPreference::new);
        preference.setOpenid(openid);
        preference.setFavoriteTags(clean(request.favoriteTags()));
        preference.setFavoriteCuisines(clean(request.favoriteCuisines()));
        preference.setFavoriteDishes(clean(request.favoriteDishes()));
        preference.setAvoidIngredients(clean(request.avoidIngredients()));
        preference.setAllergens(clean(request.allergens()));
        preference.setNormalizedBlockedTerms(allergenNormalizer.normalizeJson(
                preference.getAvoidIngredients(), preference.getAllergens()));
        preference.setEatScallion(request.eatScallion());
        preference.setEatCilantro(request.eatCilantro());
        preference.setSpiceLevel(request.spiceLevel());
        preference.setHealthGoal(healthGoal);
        preference.setHometown(clean(request.hometown()));
        preference.setCookingSkill(cookingSkill);
        // 身体数据：传了才覆盖，空值不抹掉已有档案
        if (request.gender() != null) preference.setGender(request.gender());
        if (bodyAge != null) preference.setAge(bodyAge);
        if (bodyHeight != null) preference.setHeightCm(bodyHeight);
        if (bodyWeight != null) preference.setWeightKg(bodyWeight);
        if (request.activityLevel() != null) preference.setActivityLevel(request.activityLevel());
        if (request.resistanceTraining() != null) preference.setResistanceTraining(request.resistanceTraining());
        if (request.mealTiming() != null) preference.setMealTiming(request.mealTiming());
        preference.setOnboardingCompleted(true);
        return ApiResponse.ok(repository.save(preference));
    }

    /**
     * [2026-10-27] 冷启动画像问卷（主循环 P1）：新用户首次推荐前的一次性引导。
     * 双轨写入——显式偏好表（onboardingCompleted=true）+ 记忆库（EXPLICIT 来源、置信度 0.9、
     * 365 天半衰期），让打分/推荐理由/锅仔对话从第一顿饭就开始生效。
     * 只写入问卷涉及的字段，不覆盖用户已填的其余偏好（与 PUT 全量保存不同）。
     */
    @PostMapping("/onboarding")
    @Transactional
    public ApiResponse<UserFoodPreference> onboarding(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @RequestBody OnboardingRequest request) {
        if (request == null) return ApiResponse.error(400, "问卷内容不能为空");
        if (request.spiceLevel() != null && !SPICE_LEVELS.contains(request.spiceLevel())) {
            return ApiResponse.error(400, "辣度选择无效");
        }
        Integer maxMinutes = clampRange(request.maxMinutes(), 10, 120);
        Integer people = clampRange(request.people(), 1, 10);
        List<String> cuisines = cleanList(request.cuisines(), 5);
        List<String> avoid = cleanList(request.avoidIngredients(), 20);
        List<String> allergens = cleanList(request.allergens(), 20);
        // [2026-10-28] EER 身体数据 + 进食节奏：可空、非法值直接 400（宁可拒收不静默改写）
        String gender = request.gender();
        if (gender != null && !GENDERS.contains(gender)) return ApiResponse.error(400, "性别选择无效");
        String activityLevel = request.activityLevel();
        if (activityLevel != null && !ACTIVITY_LEVELS.contains(activityLevel)) return ApiResponse.error(400, "活动水平选择无效");
        String mealTiming = request.mealTiming();
        if (mealTiming != null && !MEAL_TIMINGS.contains(mealTiming)) return ApiResponse.error(400, "进食节奏选择无效");
        Integer age = clampRange(request.age(), 10, 100);
        BigDecimal heightCm = clampDecimal(request.heightCm(), 80, 250);
        BigDecimal weightKg = clampDecimal(request.weightKg(), 20, 300);
        Boolean resistanceTraining = request.resistanceTraining();
        if (maxMinutes == null && request.spiceLevel() == null && cuisines.isEmpty()
                && avoid.isEmpty() && allergens.isEmpty() && people == null
                && gender == null && age == null && heightCm == null && weightKg == null
                && activityLevel == null && resistanceTraining == null && mealTiming == null) {
            return ApiResponse.error(400, "至少回答一个问题");
        }

        UserFoodPreference preference = repository.findByOpenid(openid).orElseGet(UserFoodPreference::new);
        preference.setOpenid(openid);
        if (!cuisines.isEmpty()) preference.setFavoriteCuisines(String.join(",", cuisines));
        if (!avoid.isEmpty()) preference.setAvoidIngredients(String.join(",", avoid));
        if (!allergens.isEmpty()) preference.setAllergens(String.join(",", allergens));
        if (request.spiceLevel() != null) preference.setSpiceLevel(request.spiceLevel());
        // EER 身体数据：只写用户填了的项，其余不覆盖
        if (gender != null) preference.setGender(gender);
        if (age != null) preference.setAge(age);
        if (heightCm != null) preference.setHeightCm(heightCm);
        if (weightKg != null) preference.setWeightKg(weightKg);
        if (activityLevel != null) preference.setActivityLevel(activityLevel);
        if (resistanceTraining != null) preference.setResistanceTraining(resistanceTraining);
        if (mealTiming != null) preference.setMealTiming(mealTiming);
        preference.setOnboardingCompleted(true);
        UserFoodPreference saved = repository.save(preference);
        if (!avoid.isEmpty() || !allergens.isEmpty()) {
            saved.setNormalizedBlockedTerms(allergenNormalizer.normalizeJson(
                    saved.getAvoidIngredients(), saved.getAllergens()));
            saved = repository.save(saved);
        }

        rememberFact(openid, AgentMemoryStore.KEY_MAX_MINUTES,
                maxMinutes == null ? null : String.valueOf(maxMinutes));
        rememberFact(openid, AgentMemoryStore.KEY_SPICE, request.spiceLevel());
        rememberFact(openid, AgentMemoryStore.KEY_CUISINE, cuisines.isEmpty() ? null : cuisines.get(0));
        rememberFact(openid, AgentMemoryStore.KEY_AVOID_INGREDIENTS,
                avoid.isEmpty() ? null : String.join(",", avoid));
        rememberFact(openid, AgentMemoryStore.KEY_ALLERGENS,
                allergens.isEmpty() ? null : String.join(",", allergens));
        rememberFact(openid, AgentMemoryStore.KEY_PEOPLE,
                people == null ? null : String.valueOf(people));
        return ApiResponse.ok(saved);
    }

    @DeleteMapping
    @Transactional
    public ApiResponse<Void> clear(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        repository.deleteByOpenid(openid);
        interactions.deleteByOpenid(openid);
        exposures.deleteByOpenid(openid);
        return ApiResponse.ok();
    }

    private boolean tooLong(String value) { return value != null && value.length() > 500; }
    private String clean(String value) { return value == null ? "" : value.trim(); }

    /** 问卷单条记忆写入：EXPLICIT 来源 / 置信度 0.9 / 不设 TTL（365 天半衰期兜底）；失败静默，不阻断问卷提交。 */
    private void rememberFact(String openid, String key, String value) {
        if (value == null || value.isBlank() || memoryStore == null) return;
        try {
            memoryStore.remember(new AgentMemoryStore.RememberCommand(
                    openid, key, value.trim(), 0.9,
                    AgentMemoryStore.SRC_EXPLICIT, "首次引导问卷", null));
        } catch (RuntimeException ignored) {
            // 记忆库写入失败不影响偏好表保存（双轨互为兜底）
        }
    }

    private Integer clampRange(Integer value, int min, int max) {
        if (value == null) return null;
        return Math.max(min, Math.min(max, value));
    }

    private BigDecimal clampDecimal(BigDecimal value, double min, double max) {
        if (value == null) return null;
        BigDecimal lo = BigDecimal.valueOf(min), hi = BigDecimal.valueOf(max);
        if (value.compareTo(lo) < 0) return lo;
        if (value.compareTo(hi) > 0) return hi;
        return value;
    }

    /**
     * [2026-10-28] 个体营养目标（NASEM 2023 EER + 蛋白质范围，移植自 aiiqc/nutrition-skill）。
     * 输入全部来自偏好表自报字段；缺项返回 NEEDS_INPUT + missingFields，不猜测。
     * 观察类红线：NOT_ELIGIBLE 只返回机器码 issues，由前端映射为可读文案，不做诊断表述。
     */
    @org.springframework.web.bind.annotation.GetMapping("/nutrition-target")
    public ApiResponse<NutritionTargetService.Target> nutritionTarget(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        if (nutritionTargets == null) return ApiResponse.error(503, "目标计算暂不可用");
        UserFoodPreference p = repository.findByOpenid(openid).orElseGet(UserFoodPreference::new);
        NutritionTargetService.Goal goal = switch (p.getHealthGoal() == null ? "BALANCED" : p.getHealthGoal()) {
            case "LEAN" -> NutritionTargetService.Goal.FAT_LOSS;
            case "FITNESS" -> NutritionTargetService.Goal.MUSCLE_GAIN;
            default -> NutritionTargetService.Goal.WELLBEING;
        };
        NutritionTargetService.Inputs inputs = new NutritionTargetService.Inputs(
                "MALE".equals(p.getGender()) ? NutritionTargetService.Sex.MALE
                        : "FEMALE".equals(p.getGender()) ? NutritionTargetService.Sex.FEMALE
                        : NutritionTargetService.Sex.UNKNOWN,
                p.getAge(), p.getHeightCm(), p.getWeightKg(),
                p.getActivityLevel() == null ? null : NutritionTargetService.ActivityLevel.valueOf(p.getActivityLevel()),
                p.getResistanceTraining(), goal);
        return ApiResponse.ok(nutritionTargets.compute(inputs));
    }

    private List<String> cleanList(List<String> values, int limit) {
        if (values == null) return List.of();
        return values.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .filter(item -> item.length() <= 20)
                .distinct()
                .limit(limit)
                .toList();
    }
    private long count(List<com.moodrecipe.backend.entity.RecipeInteraction> items, String action) {
        return items.stream().filter(item -> action.equals(item.getAction())).count();
    }

    public record FoodMemoryView(UserFoodPreference explicit, BehaviorMemory behavior) { }
    public record BehaviorMemory(String topDish, String topMood, int streak, boolean recordedToday,
                                 long likedCount, long dislikedCount, long madeCount) { }

    public record PreferenceRequest(
            String favoriteTags,
            String favoriteCuisines,
            String favoriteDishes,
            String avoidIngredients,
            String allergens,
            Boolean eatScallion,
            Boolean eatCilantro,
            String spiceLevel,
            String healthGoal,
            String hometown,
            String cookingSkill,
            // [2026-10-28] 身体数据（可空；未传不覆盖既有档案）
            String gender,
            Integer age,
            java.math.BigDecimal heightCm,
            java.math.BigDecimal weightKg,
            String activityLevel,
            Boolean resistanceTraining,
            String mealTiming) { }

    /** [2026-10-27] 冷启动问卷答案：全部可空，至少答一题。 */
    public record OnboardingRequest(
            Integer maxMinutes,
            String spiceLevel,
            List<String> cuisines,
            List<String> avoidIngredients,
            List<String> allergens,
            Integer people,
            // [2026-10-28] EER 身体数据 + 进食节奏（均可空）
            String gender,
            Integer age,
            BigDecimal heightCm,
            BigDecimal weightKg,
            String activityLevel,
            Boolean resistanceTraining,
            String mealTiming) { }
}
