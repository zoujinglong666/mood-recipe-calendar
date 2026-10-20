package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.service.DailyMealPlanService;
import com.moodrecipe.backend.service.UsageQuotaService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyMealPlanControllerTest {
    private final DailyMealPlanService plans = mock(DailyMealPlanService.class);
    private final UsageQuotaService quotas = mock(UsageQuotaService.class);
    private final DailyMealPlanController controller = new DailyMealPlanController(plans, quotas);

    /** PlanView 是 final record，无法被 Mockito mock，这里用真实实例（空三餐/空依据/无快照）即可满足断言。 */
    private static final DailyMealPlanService.PlanView SAMPLE_PLAN =
            new DailyMealPlanService.PlanView(LocalDate.now(), List.of(), "v1", List.of(), null);

    @Test
    void reshuffleConsumesQuotaAndReturnsPlan() {
        when(quotas.consume("user-1", UsageQuotaService.Feature.DAILY_MEAL_PLAN_RESHUFFLE)).thenReturn(null);
        when(quotas.view("user-1", UsageQuotaService.Feature.DAILY_MEAL_PLAN_RESHUFFLE))
                .thenReturn(new UsageQuotaService.View(false, 1, 0, 1, "2026-10-21T00:00:00+08:00"));
        when(plans.reshuffle(eq("user-1"), any(LocalDate.class)))
                .thenReturn(Optional.of(SAMPLE_PLAN));

        ApiResponse<?> resp = controller.reshuffle("user-1", null);
        assertEquals(0, resp.getCode());
        verify(plans).reshuffle(eq("user-1"), any(LocalDate.class));
    }

    @Test
    void reshuffleReturns403WhenQuotaExhausted() {
        doThrow(new IllegalStateException("今日换一整天次数已用完，看广告可再换一次"))
                .when(quotas).consume("user-1", UsageQuotaService.Feature.DAILY_MEAL_PLAN_RESHUFFLE);

        ApiResponse<?> resp = controller.reshuffle("user-1", null);
        assertEquals(403, resp.getCode());
        verify(plans, never()).reshuffle(anyString(), any(LocalDate.class));
    }

    @Test
    void quotaEndpointReturnsView() {
        UsageQuotaService.View view = new UsageQuotaService.View(false, 1, 0, 1, "2026-10-21T00:00:00+08:00");
        when(quotas.view("user-1", UsageQuotaService.Feature.DAILY_MEAL_PLAN_RESHUFFLE)).thenReturn(view);

        ApiResponse<?> resp = controller.quota("user-1");
        assertEquals(0, resp.getCode());
    }
}
