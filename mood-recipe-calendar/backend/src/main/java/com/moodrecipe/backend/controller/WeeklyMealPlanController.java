package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.agent.DialogueAgent;
import com.moodrecipe.backend.agent.DialogueState;
import com.moodrecipe.backend.service.WeeklyMealPlanService;
import com.moodrecipe.backend.service.GuozaiAgent;
import com.moodrecipe.backend.service.WechatContentSafetyService;
import com.moodrecipe.backend.service.UsageQuotaService;
import com.moodrecipe.backend.service.AgentConversationService;
import com.moodrecipe.backend.agent.AgentReflectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/weekly-plans")
public class WeeklyMealPlanController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WeeklyMealPlanController.class);
    private final WeeklyMealPlanService plans;
    private final GuozaiAgent agent;
    private final DialogueAgent mealAgent;
    private final WechatContentSafetyService contentSafety;
    private final UsageQuotaService quotas;
    private final AgentConversationService conversations;
    private final AgentReflectionService reflection;
    @Autowired public WeeklyMealPlanController(WeeklyMealPlanService plans, GuozaiAgent agent, DialogueAgent mealAgent,
                                    WechatContentSafetyService contentSafety, UsageQuotaService quotas,
                                    AgentConversationService conversations, AgentReflectionService reflection) { this.plans = plans; this.agent = agent; this.mealAgent = mealAgent; this.contentSafety = contentSafety; this.quotas = quotas; this.conversations = conversations; this.reflection = reflection; }
    @GetMapping("/current") public ApiResponse<?> current(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) { return plans.current(openid).map(ApiResponse::ok).orElseGet(() -> ApiResponse.error(404, "还没有本周计划")); }
    @GetMapping("/history") public ApiResponse<?> history(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) { return ApiResponse.ok(plans.history(openid)); }
    @GetMapping("/{id}") public ApiResponse<?> get(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @PathVariable Long id) { return plans.get(openid, id).map(ApiResponse::ok).orElseGet(() -> ApiResponse.error(404, "计划不存在")); }
    @GetMapping("/quota") public ApiResponse<UsageQuotaService.View> quota(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) { return ApiResponse.ok(quotas.view(openid, UsageQuotaService.Feature.SIMPLE_WEEKLY_PLAN)); }
    @GetMapping("/agent-quota") public ApiResponse<UsageQuotaService.View> agentQuota(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) { return ApiResponse.ok(quotas.view(openid, UsageQuotaService.Feature.AGENT_CONVERSATION)); }
    @GetMapping("/agent-conversations/current") public ApiResponse<?> currentAgentConversation(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        return ApiResponse.ok(conversations.latest(openid).orElse(null));
    }
    /** 开启新对话：归档当前 ACTIVE 会话；前端清空本地后从新问候开始，历史对话不再被自动恢复。 */
    @PostMapping("/agent-conversations/reset") public ApiResponse<?> resetAgentConversation(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        boolean hadActive = conversations.archiveActive(openid);
        log.info("[agent-reset] openid={} hadActiveConversation={} 开启新对话{}", openid, hadActive, hadActive ? "（已归档旧会话）" : "（无 ACTIVE 会话，空转）");
        return ApiResponse.ok(true);
    }
    @PostMapping("/generate") public ApiResponse<?> generate(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @RequestBody WeeklyMealPlanService.GenerateRequest request) {
        if (request == null || request.people() < 1) return ApiResponse.error(400, "请填写用餐人数");
        if (!contentSafety.allowsText(openid, request.conversationNotes())) return ApiResponse.error(400, "文字未通过安全检查");
        try { quotas.consume(openid, UsageQuotaService.Feature.SIMPLE_WEEKLY_PLAN, null, request.requestId()); }
        catch (IllegalStateException e) { return ApiResponse.error(403, e.getMessage()); }
        try {
            return ApiResponse.ok(plans.generate(openid, request));
        } catch (RuntimeException e) {
            quotas.release(openid, UsageQuotaService.Feature.SIMPLE_WEEKLY_PLAN);
            return ApiResponse.error(500, "菜单生成失败，请稍后重试");
        }
    }
    @PostMapping("/agent-replies") public ApiResponse<?> agentReply(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @RequestBody AgentReplyRequest request) {
        try { quotas.requireMember(openid); } catch (IllegalStateException e) { return ApiResponse.error(403, e.getMessage()); }
        if (request == null || request.message() == null || request.message().isBlank() || request.message().length() > 300) return ApiResponse.error(400, "请说得具体一点");
        if (!contentSafety.allowsText(openid, request.message())) return ApiResponse.error(400, "文字未通过安全检查");
        String next = request.nextQuestion() == null || request.nextQuestion().isBlank() ? "这周几个人一起吃饭？" : request.nextQuestion().substring(0, Math.min(80, request.nextQuestion().length()));
        return ApiResponse.ok(new AgentReply(agent.replyToPlanningMessage(openid, request.message().trim(), next)));
    }
    @PostMapping("/agent-turns") public ApiResponse<?> agentTurn(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @RequestBody AgentTurnRequest request) {
        if (request != null && request.message() != null && request.message().length() > 300) return ApiResponse.error(400, "一次最多输入 300 个字");
        if (request != null && request.history() != null && request.history().size() > 80) return ApiResponse.error(400, "对话记录过长，请重新开始一轮");
        if (request != null && !contentSafety.allowsText(openid, request.message())) return ApiResponse.error(400, "文字未通过安全检查");
        boolean consumed = false;
        if (!quotas.member(openid)) {
            if (request == null || request.conversationId() == null || request.conversationId().isBlank()) return ApiResponse.error(400, "缺少对话会话标识");
            try { quotas.consume(openid, UsageQuotaService.Feature.AGENT_CONVERSATION, request.conversationId().trim(), request.requestId()); consumed = true; }
            catch (IllegalStateException e) { return ApiResponse.error(403, e.getMessage()); }
        }
        try {
            String conversationId = request == null ? null : request.conversationId();
            String previousAction = conversations.lastAction(openid, conversationId);
            DialogueState.AgentState serverState = conversations.state(openid, conversationId,
                    request == null ? null : request.state());
            DialogueState.Turn turn = mealAgent.turn(openid, request == null ? "" : request.message(), serverState,
                    previousAction, request == null ? null : request.history());
            conversations.save(openid, conversationId, turn, request == null ? null : request.history());
            return ApiResponse.ok(turn);
        } catch (RuntimeException e) {
            // 与 /generate 对齐：智能体执行失败立即释放预占额度，避免"模型挂了还白扣当天唯一一次"
            if (consumed) quotas.release(openid, UsageQuotaService.Feature.AGENT_CONVERSATION);
            return ApiResponse.error(500, "锅仔刚刚走神了，请稍后再试");
        }
    }
    /** 会话评分：不满意/一般会触发锅仔异步复盘，把教训写进记忆，下一轮对话生效（自进化）。 */
    @PostMapping("/agent-feedback") public ApiResponse<?> agentFeedback(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @RequestBody AgentRatingRequest request) {
        if (request == null || request.rating() == null || !Set.of("满意", "一般", "不满意").contains(request.rating())) return ApiResponse.error(400, "评分无效");
        reflection.reflectOnRatingAsync(openid, request.rating(), request.comment());
        return ApiResponse.ok();
    }
    @PostMapping("/{id}/favorite") public ApiResponse<?> favorite(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @PathVariable Long id) { return plans.toggleFavorite(openid, id).map(ApiResponse::ok).orElseGet(() -> ApiResponse.error(404, "计划不存在")); }
    @PostMapping("/{id}/days/{index}/cover") public ApiResponse<?> cover(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @PathVariable Long id, @PathVariable int index) { return plans.ensureCover(openid, id, index).map(ApiResponse::ok).orElseGet(() -> ApiResponse.error(404, "计划不存在")); }
    @PostMapping("/{id}/days/{index}/cover/{dishIndex}") public ApiResponse<?> dishCover(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @PathVariable Long id, @PathVariable int index, @PathVariable int dishIndex) { return plans.ensureCover(openid, id, index, dishIndex).map(ApiResponse::ok).orElseGet(() -> ApiResponse.error(404, "计划不存在")); }
    @PostMapping("/{id}/days/{index}/replace") public ApiResponse<?> replace(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @PathVariable Long id, @PathVariable int index) { try { quotas.requireMember(openid); } catch (IllegalStateException e) { return ApiResponse.error(403, e.getMessage()); } return plans.replaceDay(openid, id, index).map(ApiResponse::ok).orElseGet(() -> ApiResponse.error(404, "计划不存在")); }
    @PostMapping("/{id}/shopping/{name}") public ApiResponse<?> toggle(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @PathVariable Long id, @PathVariable String name) { return plans.toggleShopping(openid, id, name).map(ApiResponse::ok).orElseGet(() -> ApiResponse.error(404, "购物项不存在")); }
    public record AgentReplyRequest(String message, String nextQuestion) {}
    public record AgentReply(String reply) {}
    public record AgentTurnRequest(String message, DialogueState.AgentState state, String conversationId,
                                   String requestId, java.util.List<AgentConversationService.TranscriptMessage> history) {}
    public record AgentRatingRequest(String rating, String comment) {}
}
