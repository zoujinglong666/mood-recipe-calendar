package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.agent.AgentMemoryStore;
import com.moodrecipe.backend.agent.MenuPlannerAgent;
import com.moodrecipe.backend.agent.MenuQualityScorer;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.entity.WeeklyMealPlan;
import com.moodrecipe.backend.repository.WeeklyMealPlanRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
                mock(MenuPlannerAgent.class), mock(AgentMemoryStore.class),
                mock(AgnesRecipeImageService.class), mock(WechatSubscriptionMessageService.class), new ObjectMapper());

        WeeklyMealPlanService.PlanView plan = service.generate("user-1",
                new WeeklyMealPlanService.GenerateRequest(3, 3, List.of(0, 2, 5), "BALANCED", false, 2));

        assertEquals(List.of("周一", "周三", "周六"), plan.days().stream().map(WeeklyMealPlanService.PlanDay::day).toList());
        assertNull(plan.agent().score(), "智能体不可用时应当如实暴露降级原因而不是伪装成高分");
        assertNotNull(plan.agent().degradeReasons());
    }

    /** 规划智能体给出结果时，必须原样采用它的日期与菜，并且把审计信息一起带回来。 */
    @Test
    void usesPlannerDaysAndSurfacesAudit() {
        WeeklyMealPlanRepository plans = mock(WeeklyMealPlanRepository.class);
        MenuPlannerAgent planner = mock(MenuPlannerAgent.class);
        when(plans.save(any(WeeklyMealPlan.class))).thenAnswer(invocation -> {
            WeeklyMealPlan saved = invocation.getArgument(0);
            saved.setId(2L);
            saved.setCreatedAt(LocalDateTime.now());
            return saved;
        });
        when(planner.plan(any(MenuPlannerAgent.PlanRequest.class))).thenReturn(new MenuPlannerAgent.PlanResult(
                List.of(new MenuPlannerAgent.PlannedDay(0,
                                List.of(new MenuPlannerAgent.PlannedDish("藜蒿炒腊肉", List.of("藜蒿", "腊肉"),
                                        List.of("腊肉先煸香"), 25, "简单", "MAIN", null)),
                                "复用腊味", "搭配一份主食"),
                        new MenuPlannerAgent.PlannedDay(2,
                                List.of(new MenuPlannerAgent.PlannedDish("宁都三杯鸡", List.of("鸡腿肉"),
                                        List.of("焖煮收汁"), 40, "中等", "MAIN", null)),
                                "和第一天共用调料", "搭配蔬菜")),
                new MenuQualityScorer.MenuQuality(88, List.of(),
                        new MenuQualityScorer.Stats(2, 2, 2, 0.6d, 0.5d, 32d, 0)),
                List.of(), List.of(), List.of("spice=微辣：用户在对话里说过"), "trace-1"));

        WeeklyMealPlanService service = new WeeklyMealPlanService(plans, mock(GuozaiAgent.class), planner,
                mock(AgentMemoryStore.class), mock(AgnesRecipeImageService.class),
                mock(WechatSubscriptionMessageService.class), new ObjectMapper());

        WeeklyMealPlanService.PlanView plan = service.generate("user-1",
                new WeeklyMealPlanService.GenerateRequest(8, 2, List.of(0, 2), "BALANCED", false, 9));

        ArgumentCaptor<MenuPlannerAgent.PlanRequest> request = ArgumentCaptor.forClass(MenuPlannerAgent.PlanRequest.class);
        verify(planner).plan(request.capture());

        assertEquals(List.of("周一", "周三"), plan.days().stream().map(WeeklyMealPlanService.PlanDay::day).toList());
        assertEquals(9, request.getValue().dishesPerDay(), "明确要求的宴席菜数不能被截成三道");
        assertEquals("藜蒿炒腊肉", plan.days().get(0).dishes().get(0).name());
        assertEquals(88, plan.agent().score());
        assertEquals("trace-1", plan.agent().traceId());
        assertEquals("复用腊味", plan.days().get(0).reuseHint());
    }

    @Test
    void todayBanquetOverridesOldMultiDaySelection() {
        WeeklyMealPlanRepository plans = mock(WeeklyMealPlanRepository.class);
        MenuPlannerAgent planner = mock(MenuPlannerAgent.class);
        when(plans.save(any(WeeklyMealPlan.class))).thenAnswer(invocation -> {
            WeeklyMealPlan saved = invocation.getArgument(0);
            saved.setId(3L);
            saved.setCreatedAt(LocalDateTime.now());
            return saved;
        });
        when(planner.plan(any(MenuPlannerAgent.PlanRequest.class))).thenReturn(new MenuPlannerAgent.PlanResult(
                List.of(new MenuPlannerAgent.PlannedDay(LocalDate.now().getDayOfWeek().getValue() - 1,
                        List.of(new MenuPlannerAgent.PlannedDish("清蒸鲈鱼", List.of("鲈鱼"), List.of("蒸熟"),
                                20, "简单", "MAIN", null)), "", "")),
                null, List.of(), List.of(), List.of(), "trace-2"));
        WeeklyMealPlanService service = new WeeklyMealPlanService(plans, mock(GuozaiAgent.class), planner,
                mock(AgentMemoryStore.class), mock(AgnesRecipeImageService.class),
                mock(WechatSubscriptionMessageService.class), new ObjectMapper());

        service.generate("user-1", new WeeklyMealPlanService.GenerateRequest(8, 2, List.of(5, 6),
                "BALANCED", false, 9, "DAILY", "我今天宴请客人"));

        ArgumentCaptor<MenuPlannerAgent.PlanRequest> request = ArgumentCaptor.forClass(MenuPlannerAgent.PlanRequest.class);
        verify(planner).plan(request.capture());
        assertEquals(List.of(LocalDate.now().getDayOfWeek().getValue() - 1), request.getValue().cookingDays());
    }

    private Recipe recipe(String name) {
        Recipe recipe = new Recipe();
        recipe.setName(name);
        recipe.setIngredients("[\"鸡蛋 2个\"]");
        recipe.setSteps("[\"炒熟即可\"]");
        return recipe;
    }
}
