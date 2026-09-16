package com.moodrecipe.backend.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 端到端验收：用真实模型跑一遍，判断"现在到底是模型调用还是真智能体"。
 *
 * 五级检查：配置 → 纯文本 → 结构化 JSON → 工具注册 → 完整规划闭环。
 * 任何一级不通过都会给出可展示的中文原因，不靠"看起来能跑"自欺欺人。
 */
@Service
public class AgentAcceptanceService {

    public record Check(String name, boolean passed, String detail, long latencyMs) {}

    public record Report(boolean agentReady, String model, String promptVersion,
                         List<Check> checks, String summary) {}

    private final LlmClient llm;
    private final ToolRegistry registry;
    private final MenuPlannerAgent planner;
    private final AgentMemoryStore store;
    private final ObjectMapper json;

    public AgentAcceptanceService(LlmClient llm, ToolRegistry registry, MenuPlannerAgent planner,
                                  AgentMemoryStore store, ObjectMapper json) {
        this.llm = llm;
        this.registry = registry;
        this.planner = planner;
        this.store = store;
        this.json = json;
    }

    public Report verify(String openid) {
        List<Check> checks = new ArrayList<>();
        boolean configured = llm.isConfigured();
        checks.add(new Check("模型配置", configured,
                configured ? "模型 " + llm.model() + " 已配置" : "缺少 AGNES_API_KEY 或模型名", 0L));
        if (!configured) {
            return new Report(false, "", AgentPrompts.VERSION, checks,
                    "模型未配置，当前只能运行在确定性降级模式。");
        }

        long start = System.currentTimeMillis();
        LlmResult chat = llm.complete(LlmRequest.text("acceptance-chat", AgentPrompts.system(),
                "用一句话自我介绍，说明你是谁。", 0.6, 120, TimeoutTier.FAST));
        checks.add(new Check("纯文本对话", chat.ok() && !chat.text().isBlank(),
                chat.ok() ? trim(chat.text(), 80) : chat.reason(), System.currentTimeMillis() - start));

        start = System.currentTimeMillis();
        LlmResult structured = llm.complete(LlmRequest.json("acceptance-json", AgentPrompts.system(),
                "只输出一个 JSON 对象：{\"ok\":true,\"name\":\"锅仔\"}", 0.2, 120, TimeoutTier.FAST));
        checks.add(new Check("结构化输出", structured.ok() && parsesAsJson(structured.text()),
                structured.ok() ? trim(structured.text(), 80) : structured.reason(),
                System.currentTimeMillis() - start));

        List<LlmToolSpec> specs = registry.specs();
        checks.add(new Check("工具注册", !specs.isEmpty(),
                "已注册 " + specs.size() + " 个工具：" + specs.stream().map(LlmToolSpec::name)
                        .reduce((a, b) -> a + "、" + b).orElse("无"), 0L));

        start = System.currentTimeMillis();
        MenuPlannerAgent.PlanResult plan = planner.plan(new MenuPlannerAgent.PlanRequest(
                openid, List.of(0), 1, "BALANCED", "DAILY", "验收用的一次性规划"));
        boolean planned = !plan.days().isEmpty() && plan.quality().score() > 0;
        checks.add(new Check("规划闭环", planned,
                "得分 " + plan.quality().score() + "；引用记忆 " + plan.memoryUsed().size() + " 条；降级原因："
                        + (plan.degradeReasons().isEmpty() ? "无" : String.join("；", plan.degradeReasons()))
                        + "；轨迹 " + plan.trace().size() + " 步",
                System.currentTimeMillis() - start));

        boolean ready = checks.stream().allMatch(Check::passed);
        String summary = ready
                ? "模型可用、结构化输出可用、工具齐全、规划闭环通过，当前是真正的智能体。"
                : "仍有环节未通过：" + checks.stream().filter(check -> !check.passed())
                .map(check -> check.name() + "（" + check.detail() + "）")
                .reduce((a, b) -> a + "；" + b).orElse("未知");
        return new Report(ready, llm.model(), AgentPrompts.VERSION, checks, summary);
    }

    private boolean parsesAsJson(String text) {
        try {
            JsonNode node = json.readTree(text == null ? "" : text.trim());
            return node != null && node.isObject();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String trim(String value, int max) {
        if (value == null) return "";
        String text = value.trim().replace("\n", " ");
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
