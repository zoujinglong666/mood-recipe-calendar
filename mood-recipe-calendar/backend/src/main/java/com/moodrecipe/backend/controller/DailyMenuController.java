package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.service.DailyMenuService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 今日菜单（TodayBoard）：打开即有的零输入每日答案。 */
@RestController
@RequestMapping("/api/daily-menu")
public class DailyMenuController {

    private final DailyMenuService dailyMenuService;

    public DailyMenuController(DailyMenuService dailyMenuService) {
        this.dailyMenuService = dailyMenuService;
    }

    /** 今日菜单：当日缓存命中秒回；首次进入挑选并落缓存。 */
    @GetMapping
    public ApiResponse<DailyMenuService.Board> today(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        return dailyMenuService.today(openid)
                .map(ApiResponse::ok)
                .orElseGet(() -> ApiResponse.error(404,
                        "锅仔的菜谱池暂时没有符合你忌口的菜，请到锅仔记忆里调整后再来"));
    }

    /** 换一道：排除当前这道重新挑选；没有别的可换时保留原菜单。 */
    @PostMapping("/refresh")
    public ApiResponse<DailyMenuService.Board> refresh(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        return dailyMenuService.refresh(openid)
                .map(ApiResponse::ok)
                .orElseGet(() -> ApiResponse.error(404, "今天已经没有别的菜可换啦，明天锅仔再想新的"));
    }
}
