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
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Test
    void replacesInvalidPlannerJsonBeforeItCanReachTheClient() {
        WeeklyMealPlanRepository plans = mock(WeeklyMealPlanRepository.class);
        MenuPlannerAgent planner = mock(MenuPlannerAgent.class);
        GuozaiAgent agent = mock(GuozaiAgent.class);
        when(plans.save(any(WeeklyMealPlan.class))).thenAnswer(invocation -> {
            WeeklyMealPlan saved = invocation.getArgument(0);
            saved.setId(4L);
            saved.setCreatedAt(LocalDateTime.now());
            return saved;
        });
        when(planner.plan(any(MenuPlannerAgent.PlanRequest.class))).thenReturn(new MenuPlannerAgent.PlanResult(
                List.of(new MenuPlannerAgent.PlannedDay(0,
                        List.of(new MenuPlannerAgent.PlannedDish("ç•ªéŒŒ‚ç’ë›×", List.of("ç•ªéŒŒ"),
                                List.of("ç‚’ç†Ÿå‘³å³å¯"), 20, "简单", "MAIN", null)), "复用食材", "搭配蔬菜")),
                null, List.of(), List.of(), List.of(), "trace-bad"));
        when(agent.planWeeklyMenu(anyString(), anyInt(), anyInt(), anyString())).thenReturn(List.of(recipe("番茄炒蛋")));
        WeeklyMealPlanService service = new WeeklyMealPlanService(plans, agent, planner,
                mock(AgentMemoryStore.class), mock(AgnesRecipeImageService.class),
                mock(WechatSubscriptionMessageService.class), new ObjectMapper());

        WeeklyMealPlanService.PlanView plan = service.generate("user-1",
                new WeeklyMealPlanService.GenerateRequest(1, 1, List.of(0), "BALANCED", false, 1));

        assertEquals("番茄炒蛋", plan.days().get(0).dishes().get(0).name());
    }

    @Test
    void replacesQuestionMarkCorruptionBeforeItCanReachTheClient() {
        WeeklyMealPlanRepository plans = mock(WeeklyMealPlanRepository.class);
        MenuPlannerAgent planner = mock(MenuPlannerAgent.class);
        GuozaiAgent agent = mock(GuozaiAgent.class);
        when(plans.save(any(WeeklyMealPlan.class))).thenAnswer(invocation -> {
            WeeklyMealPlan saved = invocation.getArgument(0);
            saved.setId(5L);
            saved.setCreatedAt(LocalDateTime.now());
            return saved;
        });
        when(planner.plan(any(MenuPlannerAgent.PlanRequest.class))).thenReturn(new MenuPlannerAgent.PlanResult(
                List.of(new MenuPlannerAgent.PlannedDay(0,
                        List.of(new MenuPlannerAgent.PlannedDish("??????", List.of("????"),
                                List.of("????"), 20, "简单", "MAIN", null)), "复用食材", "搭配蔬菜")),
                null, List.of(), List.of(), List.of(), "trace-question-mark"));
        when(agent.planWeeklyMenu(anyString(), anyInt(), anyInt(), anyString())).thenReturn(List.of(recipe("番茄炒蛋")));
        WeeklyMealPlanService service = new WeeklyMealPlanService(plans, agent, planner,
                mock(AgentMemoryStore.class), mock(AgnesRecipeImageService.class),
                mock(WechatSubscriptionMessageService.class), new ObjectMapper());

        WeeklyMealPlanService.PlanView plan = service.generate("user-1",
                new WeeklyMealPlanService.GenerateRequest(1, 1, List.of(0), "BALANCED", false, 1));

        assertEquals("番茄炒蛋", plan.days().get(0).dishes().get(0).name());
    }

    private Recipe recipe(String name) {
        Recipe recipe = new Recipe();
        recipe.setName(name);
        recipe.setIngredients("[\"鸡蛋 2个\"]");
        recipe.setSteps("[\"炒熟即可\"]");
        return recipe;
    }

    /** 采购清单必须聚合真实用量（跨菜求和、单位归一），而不是只显示出现份数。 */
    @Test
    void aggregatesRealQuantitiesAcrossDishesAndFixesCategories() {
        List<WeeklyMealPlanService.PlanDish> dishes = List.of(
                new WeeklyMealPlanService.PlanDish("糖醋里脊", List.of("里脊肉 500g", "生抽 10ml", "白糖 30g"), List.of(), null, null),
                new WeeklyMealPlanService.PlanDish("鱼香肉丝", List.of("里脊肉 300g", "生抽 5 毫升", "白胡椒粉 2克"), List.of(), null, null));
        List<WeeklyMealPlanService.ShoppingItem> items = WeeklyMealPlanService.aggregateShopping(dishes, List.of());
        assertEquals("800克", quantityOf(items, "里脊肉"));
        assertEquals("15毫升", quantityOf(items, "生抽"));
        assertEquals("30克", quantityOf(items, "白糖"));
        assertEquals("调料", categoryOf(items, "生抽"));
        assertEquals("调料", categoryOf(items, "白胡椒粉"));
    }

    /** 没有数量、或部分带量部分不带量时，回退到出现份数，绝不出现"毫升"这类无数量的单位残留。 */
    @Test
    void fallsBackToPortionCountWhenQuantityMissingOrMixed() {
        List<WeeklyMealPlanService.PlanDish> dishes = List.of(
                new WeeklyMealPlanService.PlanDish("拍黄瓜", List.of("黄瓜 1根", "盐 适量"), List.of(), null, null),
                new WeeklyMealPlanService.PlanDish("凉拌豆腐", List.of("豆腐 1块", "盐 2克"), List.of(), null, null));
        List<WeeklyMealPlanService.ShoppingItem> items = WeeklyMealPlanService.aggregateShopping(dishes, List.of());
        assertEquals("1根", quantityOf(items, "黄瓜"));
        assertEquals("1块", quantityOf(items, "豆腐"));
        assertEquals("2 份", quantityOf(items, "盐"));
    }

    /** 中文数量（半根/一小把）要解析成真实数量；"猪里脊肉"与"猪里脊"必须合并为一条。 */
    @Test
    void parsesChineseAmountsAndMergesMeatNameVariants() {
        List<WeeklyMealPlanService.PlanDish> dishes = List.of(
                new WeeklyMealPlanService.PlanDish("鱼香肉丝", List.of("猪里脊肉 200克", "胡萝卜 半根", "木耳 一小把", "蒜 3瓣"), List.of(), null, null),
                new WeeklyMealPlanService.PlanDish("糖醋里脊", List.of("猪里脊 300g"), List.of(), null, null));
        List<WeeklyMealPlanService.ShoppingItem> items = WeeklyMealPlanService.aggregateShopping(dishes, List.of());
        assertEquals("500克", quantityOf(items, "猪里脊肉"));
        assertEquals("0.5根", quantityOf(items, "胡萝卜"));
        assertEquals("1把", quantityOf(items, "木耳"));
        assertEquals("3瓣", quantityOf(items, "蒜"));
        assertTrue(items.stream().noneMatch(item -> item.name.equals("猪里脊")),
                "不应出现猪里脊/猪里脊肉两条：" + items.stream().map(item -> item.name).toList());
    }

    private String quantityOf(List<WeeklyMealPlanService.ShoppingItem> items, String name) {
        for (WeeklyMealPlanService.ShoppingItem item : items) {
            if (item.name.equals(name)) return item.quantity;
        }
        throw new AssertionError("missing item: " + name);
    }

    private String categoryOf(List<WeeklyMealPlanService.ShoppingItem> items, String name) {
        for (WeeklyMealPlanService.ShoppingItem item : items) {
            if (item.name.equals(name)) return item.category;
        }
        throw new AssertionError("missing item: " + name);
    }
}
