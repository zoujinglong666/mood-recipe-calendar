package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.agent.AgentAcceptanceService;
import com.moodrecipe.backend.agent.AgentLearningService;
import com.moodrecipe.backend.agent.AgentMemoryStore;
import com.moodrecipe.backend.agent.MemoryItem;
import com.moodrecipe.backend.agent.UserProfile;
import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 智能体的可解释入口：记忆证据链、执行结果反馈、端到端验收。 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentMemoryStore store;
    private final AgentLearningService learning;
    private final AgentAcceptanceService acceptance;

    public AgentController(AgentMemoryStore store, AgentLearningService learning,
                           AgentAcceptanceService acceptance) {
        this.store = store;
        this.learning = learning;
        this.acceptance = acceptance;
    }

    @GetMapping("/memory")
    public ApiResponse<?> memory(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        UserProfile profile = store.profile(openid, AgentMemoryStore.Scene.WEEKLY_PLAN);
        return ApiResponse.ok(new MemoryView(profile.summary(),
                profile.memory().stream().map(AgentController::toView).toList(),
                profile.cuisineAffinity(), profile.skipQuestions(),
                profile.maxCookingMinutes(), profile.preferSimple(), profile.avoidDishes()));
    }

    @DeleteMapping("/memory/{key}")
    public ApiResponse<?> forget(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
                                 @PathVariable String key) {
        store.forget(openid, key);
        return ApiResponse.ok(new ForgetResult(key));
    }

    @PostMapping("/outcomes")
    public ApiResponse<?> outcome(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
                                  @RequestBody OutcomeRequest request) {
        if (request == null || request.planId() == null) return ApiResponse.error(400, "缺少 planId");
        if (request.dishName() == null || request.dishName().isBlank()) return ApiResponse.error(400, "缺少菜名");
        learning.recordOutcome(openid, new AgentLearningService.OutcomeInput(request.planId(),
                request.dayIndex(), request.dishIndex(), request.dishName().trim(),
                request.cooked(), request.leftover(), request.tooHard()));
        AgentLearningService.StrategyHints hints = learning.hints(openid);
        return ApiResponse.ok(new OutcomeAck(hints.cuisineAffinity(), List.copyOf(hints.skipQuestions()),
                hints.maxCookingMinutes(), hints.preferSimple(), hints.avoidDishes()));
    }

    @GetMapping("/acceptance")
    public ApiResponse<?> acceptance(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        return ApiResponse.ok(acceptance.verify(openid));
    }

    private static MemoryFact toView(MemoryItem item) {
        return new MemoryFact(item.key(), item.value(), Math.round(item.confidence() * 100d) / 100d,
                item.source(), item.evidence(), item.reason(),
                item.updatedAt() == null ? null : item.updatedAt().toString());
    }

    public record MemoryView(String summary, List<MemoryFact> facts, Map<String, Double> cuisineAffinity,
                             List<String> skipQuestions, Integer maxCookingMinutes,
                             boolean preferSimple, List<String> avoidDishes) {}

    public record MemoryFact(String key, String value, double confidence, String source,
                             String evidence, String reason, String updatedAt) {}

    public record ForgetResult(String forgotten) {}

    public record OutcomeRequest(Long planId, int dayIndex, int dishIndex, String dishName,
                                 Boolean cooked, Boolean leftover, Boolean tooHard) {}

    public record OutcomeAck(Map<String, Double> cuisineAffinity, List<String> skipQuestions,
                             Integer maxCookingMinutes, boolean preferSimple, List<String> avoidDishes) {}
}
