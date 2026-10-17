package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.service.SeasoningTimingService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 调味时序提示：「什么时候放调料」——做菜教程页按食材与步骤文本匹配。 */
@RestController
@RequestMapping("/api/seasoning-tips")
public class SeasoningTimingController {

    private final SeasoningTimingService seasoningTiming;

    public SeasoningTimingController(SeasoningTimingService seasoningTiming) {
        this.seasoningTiming = seasoningTiming;
    }

    /** ingredients/steps 任传或都传；服务端做关键词匹配，返回命中的调料时机提示。 */
    @PostMapping
    public ApiResponse<Map<String, Object>> tips(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @RequestBody(required = false) TipsRequest request) {
        List<SeasoningTimingService.Tip> tips = seasoningTiming.tips(
                request == null ? null : request.ingredients(),
                request == null ? null : request.steps());
        return ApiResponse.ok(Map.of("tips", tips));
    }

    public record TipsRequest(List<String> ingredients, List<String> steps) {}
}
