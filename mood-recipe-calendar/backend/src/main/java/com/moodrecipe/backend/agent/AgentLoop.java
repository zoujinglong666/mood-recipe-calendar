package com.moodrecipe.backend.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 智能体的工具调用循环：思考 → 选工具 → 执行 → 观察 → 再思考，直到给出结论。
 *
 * 两条通道都支持：
 * 1. 供应商支持 function calling 时走原生 tool_calls；
 * 2. 不支持时走 JSON 工具协议（模型输出 {"toolCalls":[...]}），因此换供应商不会让智能体退化成纯文本输出。
 */
@Service
public class AgentLoop {

    private final LlmClient llm;
    private final ToolRegistry registry;
    private final ObjectMapper json;

    public AgentLoop(LlmClient llm, ToolRegistry registry, ObjectMapper json) {
        this.llm = llm;
        this.registry = registry;
        this.json = json;
    }

    public record Invocation(String tool, String argsJson) {}

    public record RunSpec(String purpose,
                          String goal,
                          Set<String> toolNames,
                          int maxSteps,
                          double temperature,
                          int maxTokens,
                          TimeoutTier tier,
                          ToolContext context) {}

    public record Outcome(String finalAnswer,
                          List<String> observations,
                          List<String> toolsUsed,
                          int steps,
                          boolean completed,
                          String failure,
                          long latencyMs) {

        public static Outcome failed(String failure, long latencyMs) {
            return new Outcome("", List.of(), List.of(), 0, false, failure, latencyMs);
        }
    }

    public Outcome run(RunSpec spec) {
        long start = System.currentTimeMillis();
        if (!llm.isConfigured()) {
            return Outcome.failed("模型未配置，无法执行工具调用循环", 0L);
        }
        List<LlmToolSpec> specs = registry.specs(spec.toolNames());
        List<LlmMessage> messages = new ArrayList<>();
        messages.add(LlmMessage.user(AgentPrompts.toolProtocol(registry.describeForPrompt(spec.toolNames()), spec.goal())));
        List<String> observations = new ArrayList<>();
        List<String> toolsUsed = new ArrayList<>();
        int maxSteps = Math.max(1, Math.min(spec.maxSteps(), 8));

        for (int step = 1; step <= maxSteps; step++) {
            LlmRequest request = new LlmRequest(spec.purpose(), AgentPrompts.system(), List.copyOf(messages),
                    spec.temperature(), spec.maxTokens(), spec.tier(), true, specs);
            LlmResult result = llm.complete(request);
            if (!result.ok()) {
                return new Outcome("", observations, toolsUsed, step - 1, false,
                        "第 " + step + " 步模型调用失败：" + result.reason(), System.currentTimeMillis() - start);
            }
            LlmResponse response = result.value().orElseThrow();
            List<Invocation> calls = invocations(response);
            String finalAnswer = finalAnswer(response.content());
            if (calls.isEmpty()) {
                if (!finalAnswer.isBlank() || step == maxSteps) {
                    return new Outcome(finalAnswer, observations, toolsUsed, step, !finalAnswer.isBlank(),
                            finalAnswer.isBlank() ? "达到步数上限仍未给出结论" : "",
                            System.currentTimeMillis() - start);
                }
                messages.add(LlmMessage.user("你没有给出工具调用也没有给出结论，请继续按协议输出 JSON。"));
                continue;
            }
            messages.add(LlmMessage.assistant(response.content()));
            for (Invocation call : calls) {
                long toolStart = System.currentTimeMillis();
                ToolResult toolResult = registry.execute(call.tool(), call.argsJson(), spec.context());
                long cost = System.currentTimeMillis() - toolStart;
                String summary = call.tool() + " → " + AgentPrompts.budget(toolResult.outputJson(), 200);
                observations.add(summary);
                toolsUsed.add(call.tool());
                messages.add(LlmMessage.user("工具 " + call.tool() + " 返回："
                        + AgentPrompts.budget(toolResult.outputJson(), 2000)
                        + (toolResult.failed() ? "（执行失败：" + toolResult.error() + "）" : "")));
                if (cost > 2_000L) {
                    observations.add(call.tool() + " 耗时 " + cost + "ms");
                }
            }
        }
        return new Outcome("", observations, toolsUsed, maxSteps, false, "达到工具调用步数上限",
                System.currentTimeMillis() - start);
    }

    private List<Invocation> invocations(LlmResponse response) {
        List<Invocation> calls = new ArrayList<>();
        if (response.hasToolCalls()) {
            for (LlmToolCall call : response.toolCalls()) {
                calls.add(new Invocation(call.name(), call.argumentsJson()));
            }
        }
        if (!calls.isEmpty()) return calls;
        try {
            JsonNode root = json.readTree(stripFence(response.content()));
            if (!root.isObject()) return calls;
            JsonNode array = root.path("toolCalls");
            if (!array.isArray()) return calls;
            for (JsonNode item : array) {
                String tool = item.path("tool").asText(item.path("name").asText("")).trim();
                if (tool.isEmpty()) continue;
                JsonNode args = item.path("args");
                if (args.isMissingNode() || !args.isObject()) args = item.path("arguments");
                calls.add(new Invocation(tool, args.isMissingNode() ? "{}" : args.toString()));
            }
        } catch (Exception ignored) {
            // 内容不是 JSON 时不算工具调用
        }
        return calls;
    }

    private String finalAnswer(String content) {
        try {
            JsonNode root = json.readTree(stripFence(content));
            if (root.isObject() && root.hasNonNull("finalAnswer")) {
                return root.path("finalAnswer").asText("").trim();
            }
        } catch (Exception ignored) {
            // 不是 JSON 就把整段当作结论
        }
        return content == null ? "" : content.trim();
    }

    private static String stripFence(String content) {
        if (content == null) return "";
        return content.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
    }
}
