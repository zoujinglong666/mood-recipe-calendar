package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.entity.UserFoodPreference;
import com.moodrecipe.backend.repository.UserFoodPreferenceRepository;
import com.moodrecipe.backend.repository.RecipeInteractionRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/preferences")
public class UserFoodPreferenceController {
    private static final Set<String> SPICE_LEVELS = Set.of("NONE", "MILD", "NORMAL", "HOT");
    private final UserFoodPreferenceRepository repository;
    private final RecipeInteractionRepository interactions;

    public UserFoodPreferenceController(UserFoodPreferenceRepository repository, RecipeInteractionRepository interactions) {
        this.repository = repository;
        this.interactions = interactions;
    }

    @GetMapping
    public ApiResponse<UserFoodPreference> get(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        return ApiResponse.ok(repository.findByOpenid(openid).orElseGet(UserFoodPreference::new));
    }

    @PutMapping
    public ApiResponse<UserFoodPreference> save(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @RequestBody PreferenceRequest request) {
        if (request == null || !SPICE_LEVELS.contains(request.spiceLevel())) {
            return ApiResponse.error(400, "辣度选择无效");
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
        preference.setOnboardingCompleted(true);
        return ApiResponse.ok(repository.save(preference));
    }

    @DeleteMapping
    @Transactional
    public ApiResponse<Void> clear(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        repository.deleteByOpenid(openid);
        interactions.deleteByOpenid(openid);
        return ApiResponse.ok();
    }

    private boolean tooLong(String value) { return value != null && value.length() > 500; }
    private String clean(String value) { return value == null ? "" : value.trim(); }

    public record PreferenceRequest(
            String favoriteTags,
            String favoriteCuisines,
            String favoriteDishes,
            String avoidIngredients,
            String allergens,
            Boolean eatScallion,
            Boolean eatCilantro,
            String spiceLevel) { }
}
