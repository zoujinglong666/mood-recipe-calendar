package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.AppClock;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.service.DailyMealPlanService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** 独立于今日单菜的健康成年人一日三餐计划入口。 */
@RestController
@RequestMapping("/api/daily-meal-plan")
public class DailyMealPlanController {
    private final DailyMealPlanService plans;
    private final UsageQuotaService quotas;
    /** 运营埋点（可选）：换菜是签到三餐券的核心消费动作 */
    private OperationalEventService operationalEvents;

    public DailyMealPlanController(DailyMealPlanService plans, UsageQuotaService quotas) {
        this.plans = plans;
        this.quotas = quotas;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setOperationalEvents(OperationalEventService operationalEvents) {
        this.operationalEvents = operationalEvents;
    }

    @GetMapping
    public ApiResponse<?> get(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
                              @RequestParam(required = false) String date) {
        try {
            LocalDate target = date == null || date.isBlank() ? AppClock.today() : LocalDate.parse(date);
            return plans.plan(openid, target).map(ApiResponse::ok)
                    .orElseGet(() -> ApiResponse.error(422, "暂时无法生成合格的三餐计划，请调整忌口或稍后再试"));
        } catch (DateTimeParseException e) {
            return ApiResponse.error(400, "日期格式应为 YYYY-MM-DD");
        }
    }

    @PostMapping("/{mealIndex}/replace")
    public ApiResponse<?> replace(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
                                  @PathVariable int mealIndex, @RequestParam(required = false) String date) {
        // 换菜是三餐券的消费动作：会员不限次；非会员未签到 1 次、签到抽中三餐券 2 次，当日有效
        try {
            quotas.consume(openid, UsageQuotaService.Feature.DAILY_MEAL_PLAN);
        } catch (IllegalStateException e) {
            return ApiResponse.error(403, e.getMessage());
        }
        try {
            LocalDate target = date == null || date.isBlank() ? AppClock.today() : LocalDate.parse(date);
            var result = plans.replace(openid, target, mealIndex).map(ApiResponse::ok)
                    .orElseGet(() -> ApiResponse.error(422, "暂时没有符合条件的替换菜，请稍后再试"));
            if (operationalEvents != null && result.getCode() == 0) {
                try { operationalEvents.record("MEAL_PLAN_REPLACE", "INFO", openid, null, "meal:" + mealIndex); } catch (RuntimeException ignored) { }
            }
            return result;
        } catch (DateTimeParseException e) { return ApiResponse.error(400, "日期格式应为 YYYY-MM-DD"); }
    }

    /** 三餐卡封面懒生成：该餐没有真实图时补拍专属封面并回写（与周计划的 ensureCover 同套路）。 */
    @PostMapping("/{mealIndex}/cover")
    public ApiResponse<?> cover(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
                                @PathVariable int mealIndex, @RequestParam(required = false) String date) {
        try {
            LocalDate target = date == null || date.isBlank() ? AppClock.today() : LocalDate.parse(date);
            return plans.ensureCover(openid, target, mealIndex).map(ApiResponse::ok)
                    .orElseGet(() -> ApiResponse.error(404, "这道餐暂时不需要补图"));
        } catch (DateTimeParseException e) { return ApiResponse.error(400, "日期格式应为 YYYY-MM-DD"); }
    }
}
