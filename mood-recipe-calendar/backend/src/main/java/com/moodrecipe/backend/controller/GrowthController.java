package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.service.GrowthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 锅仔养成：等级 / 进度 / 皮肤解锁 / 最近投喂动态。行为事件由各业务链路挂钩写入。 */
@RestController
@RequestMapping("/api/growth")
public class GrowthController {

    private final GrowthService growth;

    public GrowthController(GrowthService growth) {
        this.growth = growth;
    }

    @GetMapping("/status")
    public ApiResponse<GrowthService.StatusView> status(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        return ApiResponse.ok(growth.status(openid));
    }
}
