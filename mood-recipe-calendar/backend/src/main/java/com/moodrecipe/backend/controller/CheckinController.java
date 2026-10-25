package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.service.CheckinService;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 每日签到：签到记录 + 当日随机赠礼类型预览。
 * 周边商城（商品/订单）已下线。
 */
@RestController
@RequestMapping("/api/checkin")
public class CheckinController {

    private final CheckinService checkinService;

    public CheckinController(CheckinService checkinService) {
        this.checkinService = checkinService;
    }

    /** 今日签到 POST /api/checkin */
    @PostMapping
    public ApiResponse<Map<String, Object>> checkin(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        return ApiResponse.ok(checkinService.checkin(openid));
    }

    /** 签到状态 GET /api/checkin/status */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> checkinStatus(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        return ApiResponse.ok(checkinService.checkinStatus(openid));
    }
}
