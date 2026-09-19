package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.service.CookingAgentService;
import com.moodrecipe.backend.service.CookingLearningService;
import com.moodrecipe.backend.service.WechatContentSafetyService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cooking-agent")
public class CookingAgentController {
    private final CookingAgentService agent;
    private final CookingLearningService learning;
    private final WechatContentSafetyService contentSafety;

    public CookingAgentController(CookingAgentService agent, CookingLearningService learning,
                                  WechatContentSafetyService contentSafety) {
        this.agent = agent; this.learning = learning; this.contentSafety = contentSafety;
    }

    @PostMapping("/turn")
    public ApiResponse<?> turn(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
                               @RequestBody CookingAgentService.TurnRequest request) {
        if (request == null || request.recipeId() == null) return ApiResponse.error(400, "缺少菜谱信息");
        if (request.message() != null && request.message().length() > 500) return ApiResponse.error(400, "一次最多输入500个字");
        if (!contentSafety.allowsText(openid, request.message())) return ApiResponse.error(400, "文字未通过安全检查");
        try { return ApiResponse.ok(agent.turn(openid, request)); }
        catch (IllegalArgumentException exception) { return ApiResponse.error(400, exception.getMessage()); }
    }

    @PostMapping("/feedback")
    public ApiResponse<?> feedback(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
                                   @RequestBody FeedbackRequest request) {
        if (request == null) return ApiResponse.error(400, "反馈不能为空");
        try {
            learning.record(openid, request.recipeId(), request.stepIndex(), request.stepType(), request.eventType());
            return ApiResponse.ok();
        } catch (IllegalArgumentException exception) { return ApiResponse.error(400, exception.getMessage()); }
    }

    @DeleteMapping("/learning")
    public ApiResponse<?> clear(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        learning.clear(openid); return ApiResponse.ok();
    }

    public record FeedbackRequest(Long recipeId, Integer stepIndex, String stepType, String eventType) {}
}
