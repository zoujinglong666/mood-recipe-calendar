package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.service.WeeklyThemeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 每周主题运营位：首页横幅数据源，点击带预设去周计划生成。 */
@RestController
@RequestMapping("/api/weekly-theme")
public class WeeklyThemeController {

    private final WeeklyThemeService themes;

    public WeeklyThemeController(WeeklyThemeService themes) {
        this.themes = themes;
    }

    @GetMapping
    public ApiResponse<WeeklyThemeService.WeeklyThemeView> current(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        return ApiResponse.ok(themes.current());
    }
}
