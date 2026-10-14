package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.agent.DialogueAgent;
import com.moodrecipe.backend.agent.DialogueState;
import com.moodrecipe.backend.service.WeeklyMealPlanService;
import com.moodrecipe.backend.service.WeeklyPlanJobService;
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
    private final WeeklyPlanJobService weeklyPlanJobs;
    @Autowired public WeeklyMealPlanController(WeeklyMealPlanService plans, GuozaiAgent agent, DialogueAgent mealAgent,
                                    WechatContentSafetyService contentSafety, UsageQuotaService quotas,
                                    AgentConversationService conversations, AgentReflectionService reflection,
                                    WeeklyPlanJobService weeklyPlanJobs) { this.plans = plans; this.agent = agent; this.mealAgent = mealAgent; this.contentSafety = contentSafety; this.quotas = quotas; this.conversations = conversations; this.reflection = reflection; this.weeklyPlanJobs = weeklyPlanJobs; }
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
        // 配额前置扣除（瞬时，不阻塞连接）；失败则在后台任务内释放，行为与同步版一致。
        // 真正的 LLM 排菜在后台虚拟线程跑，POST 立即返回任务 jobId，前端轮询进度，避免客户端超时断连导致 Broken pipe。
        try { quotas.consume(openid, UsageQuotaService.Feature.SIMPLE_WEEKLY_PLAN, null, request.requestId()); }
        catch (IllegalStateException e) { return ApiResponse.error(403, e.getMessage()); }
        WeeklyPlanJobService.JobView job = weeklyPlanJobs.start(openid, () -> {}, progress -> {
            try {
                return plans.generate(openid, request, progress).id();
            } catch (RuntimeException e) {
                quotas.release(openid, UsageQuotaService.Feature.SIMPLE_WEEKLY_PLAN);
                throw e;
            }
        });
        return ApiResponse.ok(job);
    }

    /** 轮询周菜单生成任务进度；成功后携带 planId，前端据此跳详情。 */
    @GetMapping("/generate/jobs/{jobId}")
    public ApiResponse<?> weeklyPlanJob(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @PathVariable String jobId) {
        return weeklyPlanJobs.find(jobId, openid)
                .map(ApiResponse::ok)
                .orElseGet(() -> ApiResponse.error(404, "任务不存在或已过期"));
    }
    @PostMapping("/agent-replies") public ApiResponse<?> agentReply(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @RequestBody AgentReplyRequest request) {
        try { quotas.requireMember(openid); } catch (IllegalStateException e) { return ApiResponse.error(403, e.getMessage()); }
        if (request == null || request.message() == null || request.message().isBlank() || request.message().length() > 300) return ApiResponse.error(400, "请说得具体一点");
        if (!contentSafety.allowsText(openid, request.message())) return ApiResponse.error(400, "文字未通过安全检查");
        String next = request.nextQuestion() == null || request.nextQuestion().isBlank() ? "这周几个人一起吃饭？" : request.nextQuestion().substring(0, Math.min(80, request.nextQuestion().length()));
        return ApiResponse.ok(new AgentReply(agent.replyToPlanningMessage(openid, request.message().trim(), next)));
    }
    /** 单轮携带的对话历史上限：超出部分自动裁剪（更早内容由 Compaction 摘要承接），不再拒绝请求。 */
    private static final int MAX_TURN_HISTORY = 240;

    @PostMapping("/agent-turns") public ApiResponse<?> agentTurn(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @RequestBody AgentTurnRequest request) {
        if (request != null && request.message() != null && request.message().length() > 300) return ApiResponse.error(400, "一次最多输入 300 个字");
        // 无缝长对话：历史超限不再判停，只保留最近一段；更早往来由 Compactor 压成摘要进模型背景
        if (request != null && request.history() != null && request.history().size() > MAX_TURN_HISTORY) {
            request = new AgentTurnRequest(request.message(), request.state(), request.conversationId(),
                    request.requestId(), request.preset(),
                    request.history().subList(request.history().size() - MAX_TURN_HISTORY, request.history().size()));
        }
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
            // 场景预设：一键带整套槽位进场（跳过逐项追问）；预设轮必须开新状态，
            // 不能被 conversations.state() 的 DB 旧状态覆盖（那是反复旧提问的温床）。
            DialogueState.AgentState serverState = presetState(request == null ? null : request.preset());
            if (serverState == null) {
                serverState = conversations.state(openid, conversationId,
                        request == null ? null : request.state());
            }
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
                                   String requestId, String preset,
                                   java.util.List<AgentConversationService.TranscriptMessage> history) {}
    public record AgentRatingRequest(String rating, String comment) {}

    /**
     * 场景预设 → 一整套初始槽位。字段填满让 gaps 为空，对话直接进 READY（生成入口），
     * 用户仍可通过 chips 或对话微调单项。
     * 冰箱清空不在此列：它走冰箱库存工具的专门问答路径，由前端发引导语触发。
     */
    private DialogueState.AgentState presetState(String preset) {
        if (preset == null || preset.isBlank()) return null;
        switch (preset.trim().toUpperCase()) {
            case "SINGLE":
                // 单人快煮：工作日晚饭，两道菜省钱不剩饭
                return new DialogueState.AgentState(1, java.util.List.of(0, 1, 2, 3, 4), 2,
                        "DAILY", "SAVE", false, false, "微辣", null, true, "一人食", java.util.List.of());
            case "COUPLE":
                // 情侣二人食：均衡搭配，三道菜有家常也有惊喜
                return new DialogueState.AgentState(2, java.util.List.of(0, 1, 2, 3, 4), 3,
                        "BALANCED", "DAILY", false, false, "微辣", null, true, "情侣二人食", java.util.List.of());
            case "BANQUET":
                // 宴席硬菜：周末一桌 10 道撑场面
                return new DialogueState.AgentState(8, java.util.List.of(5, 6), 10,
                        "TREAT", "TREAT", false, false, "能吃辣", null, true, "宴请", java.util.List.of());
            default:
                return null;
        }
    }
}
