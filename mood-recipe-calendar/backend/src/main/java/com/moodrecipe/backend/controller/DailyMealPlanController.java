package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
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

/** 独立于今日单菜的健康成年人一日三餐计划入口。 */
@RestController
@RequestMapping("/api/daily-meal-plan")
public class DailyMealPlanController {
    private final DailyMealPlanService plans;

    public DailyMealPlanController(DailyMealPlanService plans) { this.plans = plans; }

    @GetMapping
    public ApiResponse<?> get(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
                              @RequestParam(required = false) String date) {
        try {
            LocalDate target = date == null || date.isBlank() ? LocalDate.now() : LocalDate.parse(date);
            return plans.plan(openid, target).map(ApiResponse::ok)
                    .orElseGet(() -> ApiResponse.error(422, "暂时无法生成合格的三餐计划，请调整忌口或稍后再试"));
        } catch (Exception e) {
            return ApiResponse.error(400, "日期格式应为 YYYY-MM-DD");
        }
    }

    @PostMapping("/{mealIndex}/replace")
    public ApiResponse<?> replace(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
                                  @PathVariable int mealIndex, @RequestParam(required = false) String date) {
        try {
            LocalDate target = date == null || date.isBlank() ? LocalDate.now() : LocalDate.parse(date);
            return plans.replace(openid, target, mealIndex).map(ApiResponse::ok)
                    .orElseGet(() -> ApiResponse.error(422, "暂时没有符合条件的替换菜，请稍后再试"));
        } catch (Exception e) { return ApiResponse.error(400, "日期格式应为 YYYY-MM-DD"); }
    }
}
