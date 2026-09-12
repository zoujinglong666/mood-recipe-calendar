package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.entity.WeeklyMealPlan;
import com.moodrecipe.backend.repository.WeeklyMealPlanRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WeeklyMealPlanServiceTest {

    @Test
    void preservesSelectedWeekdaysInsteadOfRenumberingThem() {
        WeeklyMealPlanRepository plans = mock(WeeklyMealPlanRepository.class);
        GuozaiAgent agent = mock(GuozaiAgent.class);
        Recipe recipe = recipe("番茄炒蛋");
        when(agent.planWeeklyMenu(anyString(), anyInt(), anyInt(), anyString())).thenReturn(List.of(recipe));
        when(plans.save(any(WeeklyMealPlan.class))).thenAnswer(invocation -> {
            WeeklyMealPlan saved = invocation.getArgument(0);
            saved.setId(1L);
            saved.setCreatedAt(LocalDateTime.now());
            return saved;
        });

        WeeklyMealPlanService service = new WeeklyMealPlanService(plans, agent,
                mock(AgnesRecipeImageService.class), mock(WechatSubscriptionMessageService.class), new ObjectMapper());

        WeeklyMealPlanService.PlanView plan = service.generate("user-1",
                new WeeklyMealPlanService.GenerateRequest(3, 3, List.of(0, 2, 5), "BALANCED", false, 2));

        assertEquals(List.of("周一", "周三", "周六"), plan.days().stream().map(WeeklyMealPlanService.PlanDay::day).toList());
    }

    private Recipe recipe(String name) {
        Recipe recipe = new Recipe();
        recipe.setName(name);
        recipe.setIngredients("[\"鸡蛋 2个\"]");
        recipe.setSteps("[\"炒熟即可\"]");
        return recipe;
    }
}
