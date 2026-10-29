package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.service.nutrition.NutritionCoachService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * [2026-10-28] AI 营养师：基于身体数据/健康目标/偏好与当日三餐，给出每餐营养方案与整日对照。
 * 身体数据不全时返回 NEEDS_INPUT（前端引导去问卷补齐），不做任何猜测。
 */
@RestController
@RequestMapping("/api/nutrition")
public class NutritionCoachController {

    private final NutritionCoachService coach;

    public NutritionCoachController(NutritionCoachService coach) {
        this.coach = coach;
    }

    @GetMapping("/coach")
    public ApiResponse<NutritionCoachService.CoachView> coach(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        return ApiResponse.ok(coach.coach(openid));
    }
}
