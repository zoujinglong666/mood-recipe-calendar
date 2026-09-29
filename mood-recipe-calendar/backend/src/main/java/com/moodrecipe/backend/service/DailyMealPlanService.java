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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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

    DailyMealPlanService(DailyMealPlanRepository plans, RecipeRepository recipes,
                         UserFoodPreferenceRepository preferences,
                         NutritionKnowledgePackRepository packs,
                         SeasonalIngredientRepository seasonal,
                         DailyMealPlanValidator validator, ObjectMapper json) {
        this(plans, recipes, preferences, packs, seasonal, validator, json, null);
    }

    @Autowired
    public DailyMealPlanService(DailyMealPlanRepository plans, RecipeRepository recipes,
                                UserFoodPreferenceRepository preferences,
                                NutritionKnowledgePackRepository packs,
                                SeasonalIngredientRepository seasonal,
                                DailyMealPlanValidator validator, ObjectMapper json,
                                AgentMemoryStore memory) {
        this.plans = plans;
        this.recipes = recipes;
        this.preferences = preferences;
        this.packs = packs;
        this.seasonal = seasonal;
        this.validator = validator;
        this.json = json;
        this.memory = memory;
    }

    public Optional<PlanView> plan(String openid, LocalDate date) {
        Optional<DailyMealPlan> existing = plans.findByOpenidAndPlanDate(openid, date);
        if (existing.isPresent()) return read(existing.get());

        List<NutritionKnowledgePack> knowledge = eligiblePacks();
        List<SeasonalIngredient> inSeason = eligibleSeasonalIngredients();
        if (knowledge.isEmpty() || inSeason.isEmpty()) return Optional.empty();

        UserProfile profile = profile(openid);
        UserFoodPreference preference = validationPreference(openid, profile);
        List<Recipe> candidates = rankedCandidates(profile, inSeason);
        for (int first = 0; first < candidates.size(); first++) {
            for (int second = first + 1; second < candidates.size(); second++) {
                for (int third = second + 1; third < candidates.size(); third++) {
                    List<Recipe> meals = List.of(candidates.get(first), candidates.get(second), candidates.get(third));
                    if (validator.validate(meals, preference, knowledge).valid()) {
                        return save(openid, date, meals, knowledge.get(0).getVersion());
                    }
                }
            }
        }
        return Optional.empty();
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
            for (Recipe candidate : rankedCandidates(profile, eligibleSeasonalIngredients())) {
                if (existingNames.contains(candidate.getName())) continue;
                meals.set(mealIndex, candidate);
                if (validator.validate(meals, preference, knowledge).valid()) {
                    stored.get().setPlanJson(json.writeValueAsString(meals));
                    plans.save(stored.get());
                    return Optional.of(new PlanView(date, List.copyOf(meals), knowledge.get(0).getVersion()));
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
        Set<String> blocked = new HashSet<>();
        blocked.addAll(profile.rejectedDishes());
        blocked.addAll(profile.avoidDishes());
        Set<String> loved = new HashSet<>(profile.lovedDishes());
        Set<String> recent = new HashSet<>(profile.recentDishes());
        return recipes.findAll().stream()
                .filter(recipe -> inSeason.stream().anyMatch(item -> text(recipe).contains(item.getName())))
                .filter(recipe -> !blocked.contains(recipe.getName()))
                .sorted(Comparator
                        .comparing((Recipe recipe) -> !loved.contains(recipe.getName()))
                        .thenComparing(recipe -> recent.contains(recipe.getName())))
                .toList();
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
            return Optional.of(new PlanView(date, meals, version));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private Optional<PlanView> read(DailyMealPlan row) {
        try {
            return Optional.of(new PlanView(row.getPlanDate(), json.readValue(row.getPlanJson(),
                    new TypeReference<List<Recipe>>() { }), "saved"));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private String text(Recipe recipe) {
        return (recipe.getName() == null ? "" : recipe.getName()) + " "
                + (recipe.getIngredients() == null ? "" : recipe.getIngredients());
    }

    public record PlanView(LocalDate date, List<Recipe> meals, String knowledgePackVersion) { }
}
