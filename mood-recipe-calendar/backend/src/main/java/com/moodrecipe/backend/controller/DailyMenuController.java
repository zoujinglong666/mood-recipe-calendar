package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.service.DailyMenuService;
import com.moodrecipe.backend.service.UsageQuotaService;
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
    private final UsageQuotaService quotas;

    public DailyMenuController(DailyMenuService dailyMenuService, UsageQuotaService quotas) {
        this.dailyMenuService = dailyMenuService;
        this.quotas = quotas;
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

    /** 今日卡封面懒生成：今日菜没有真实图时补拍专属封面并回写菜谱表。 */
    @PostMapping("/cover")
    public ApiResponse<DailyMenuService.Board> cover(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        // 生图是链路里最贵的大模型调用：每日配额兜底（hasRealImage 幂等防重复，这里防批量代付）
        try {
            quotas.consume(openid, UsageQuotaService.Feature.COVER_GENERATE);
        } catch (IllegalStateException e) {
            return ApiResponse.error(429, e.getMessage());
        }
        var result = dailyMenuService.ensureCover(openid);
        // 无需补图（404 路径）不消耗额度
        if (result.isEmpty()) quotas.release(openid, UsageQuotaService.Feature.COVER_GENERATE);
        return result
                .map(ApiResponse::ok)
                .orElseGet(() -> ApiResponse.error(404, "这道菜暂时不需要补图"));
    }
}
