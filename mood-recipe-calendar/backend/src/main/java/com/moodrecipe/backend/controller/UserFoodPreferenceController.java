package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.entity.UserFoodPreference;
import com.moodrecipe.backend.repository.UserFoodPreferenceRepository;
import com.moodrecipe.backend.repository.RecipeInteractionRepository;
import com.moodrecipe.backend.repository.RecommendationExposureRepository;
import com.moodrecipe.backend.service.GuozaiMemory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/preferences")
public class UserFoodPreferenceController {
    private static final Set<String> SPICE_LEVELS = Set.of("NONE", "MILD", "NORMAL", "HOT");
    private static final Set<String> HEALTH_GOALS = Set.of("BALANCED", "FITNESS", "LEAN");
    private final UserFoodPreferenceRepository repository;
    private final RecipeInteractionRepository interactions;
    private final RecommendationExposureRepository exposures;
    private final GuozaiMemory memory;

    public UserFoodPreferenceController(UserFoodPreferenceRepository repository,
                                        RecipeInteractionRepository interactions,
                                        RecommendationExposureRepository exposures,
                                        GuozaiMemory memory) {
        this.repository = repository;
        this.interactions = interactions;
        this.exposures = exposures;
        this.memory = memory;
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
        UserFoodPreference preference = repository.findByOpenid(openid).orElseGet(UserFoodPreference::new);
        preference.setOpenid(openid);
        preference.setFavoriteTags(clean(request.favoriteTags()));
        preference.setFavoriteCuisines(clean(request.favoriteCuisines()));
        preference.setFavoriteDishes(clean(request.favoriteDishes()));
        preference.setAvoidIngredients(clean(request.avoidIngredients()));
        preference.setAllergens(clean(request.allergens()));
        preference.setEatScallion(request.eatScallion());
        preference.setEatCilantro(request.eatCilantro());
        preference.setSpiceLevel(request.spiceLevel());
        preference.setHealthGoal(healthGoal);
        preference.setOnboardingCompleted(true);
        return ApiResponse.ok(repository.save(preference));
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
            String healthGoal) { }
}
