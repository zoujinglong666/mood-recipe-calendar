package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.agent.DialogueAgent;
import com.moodrecipe.backend.agent.DialogueState;
import com.moodrecipe.backend.service.WeeklyMealPlanService;
import com.moodrecipe.backend.service.GuozaiAgent;
import com.moodrecipe.backend.service.WechatContentSafetyService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/weekly-plans")
public class WeeklyMealPlanController {
    private final WeeklyMealPlanService plans;
    private final GuozaiAgent agent;
    private final DialogueAgent mealAgent;
    private final WechatContentSafetyService contentSafety;
    public WeeklyMealPlanController(WeeklyMealPlanService plans, GuozaiAgent agent, DialogueAgent mealAgent,
                                    WechatContentSafetyService contentSafety) { this.plans = plans; this.agent = agent; this.mealAgent = mealAgent; this.contentSafety = contentSafety; }
    @GetMapping("/current") public ApiResponse<?> current(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) { return plans.current(openid).map(ApiResponse::ok).orElseGet(() -> ApiResponse.error(404, "还没有本周计划")); }
    @GetMapping("/history") public ApiResponse<?> history(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) { return ApiResponse.ok(plans.history(openid)); }
    @GetMapping("/{id}") public ApiResponse<?> get(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @PathVariable Long id) { return plans.get(openid, id).map(ApiResponse::ok).orElseGet(() -> ApiResponse.error(404, "计划不存在")); }
    @PostMapping("/generate") public ApiResponse<?> generate(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @RequestBody WeeklyMealPlanService.GenerateRequest request) { return request == null || request.people() < 1 ? ApiResponse.error(400, "请填写用餐人数") : !contentSafety.allowsText(openid, request.conversationNotes()) ? ApiResponse.error(400, "文字未通过安全检查") : ApiResponse.ok(plans.generate(openid, request)); }
    @PostMapping("/agent-replies") public ApiResponse<?> agentReply(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @RequestBody AgentReplyRequest request) {
        if (request == null || request.message() == null || request.message().isBlank() || request.message().length() > 300) return ApiResponse.error(400, "请说得具体一点");
        if (!contentSafety.allowsText(openid, request.message())) return ApiResponse.error(400, "文字未通过安全检查");
        String next = request.nextQuestion() == null || request.nextQuestion().isBlank() ? "这周几个人一起吃饭？" : request.nextQuestion().substring(0, Math.min(80, request.nextQuestion().length()));
        return ApiResponse.ok(new AgentReply(agent.replyToPlanningMessage(openid, request.message().trim(), next)));
    }
    @PostMapping("/agent-turns") public ApiResponse<?> agentTurn(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @RequestBody AgentTurnRequest request) {
        if (request != null && request.message() != null && request.message().length() > 300) return ApiResponse.error(400, "一次最多输入 300 个字");
        if (request != null && !contentSafety.allowsText(openid, request.message())) return ApiResponse.error(400, "文字未通过安全检查");
        return ApiResponse.ok(mealAgent.turn(openid, request == null ? "" : request.message(), request == null ? null : request.state()));
    }
    @PostMapping("/{id}/favorite") public ApiResponse<?> favorite(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @PathVariable Long id) { return plans.toggleFavorite(openid, id).map(ApiResponse::ok).orElseGet(() -> ApiResponse.error(404, "计划不存在")); }
    @PostMapping("/{id}/days/{index}/cover") public ApiResponse<?> cover(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @PathVariable Long id, @PathVariable int index) { return plans.ensureCover(openid, id, index).map(ApiResponse::ok).orElseGet(() -> ApiResponse.error(404, "计划不存在")); }
    @PostMapping("/{id}/days/{index}/cover/{dishIndex}") public ApiResponse<?> dishCover(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @PathVariable Long id, @PathVariable int index, @PathVariable int dishIndex) { return plans.ensureCover(openid, id, index, dishIndex).map(ApiResponse::ok).orElseGet(() -> ApiResponse.error(404, "计划不存在")); }
    @PostMapping("/{id}/days/{index}/replace") public ApiResponse<?> replace(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @PathVariable Long id, @PathVariable int index) { return plans.replaceDay(openid, id, index).map(ApiResponse::ok).orElseGet(() -> ApiResponse.error(404, "计划不存在")); }
    @PostMapping("/{id}/shopping/{name}") public ApiResponse<?> toggle(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @PathVariable Long id, @PathVariable String name) { return plans.toggleShopping(openid, id, name).map(ApiResponse::ok).orElseGet(() -> ApiResponse.error(404, "购物项不存在")); }
    public record AgentReplyRequest(String message, String nextQuestion) {}
    public record AgentReply(String reply) {}
    public record AgentTurnRequest(String message, DialogueState.AgentState state) {}
}
