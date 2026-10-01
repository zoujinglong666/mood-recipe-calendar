package com.moodrecipe.backend.agent;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 工具注册表：模型只能调用这里登记过的工具，且任何异常都被收敛成失败结果。 */
@Service
public class ToolRegistry {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    public ToolRegistry(List<AgentTool> registered) {
        if (registered != null) {
            registered.forEach(tool -> tools.put(tool.name(), tool));
        }
    }

    public List<LlmToolSpec> specs() {
        return tools.values().stream()
                .map(tool -> new LlmToolSpec(tool.name(), tool.description(), tool.parametersJson()))
                .toList();
    }

    public List<LlmToolSpec> specs(Set<String> names) {
        return tools.values().stream()
                .filter(tool -> names == null || names.isEmpty() || names.contains(tool.name()))
                .map(tool -> new LlmToolSpec(tool.name(), tool.description(), tool.parametersJson()))
                .toList();
    }

    /**
     * 只读/查询类工具执行失败时自动重试一次，避免偶发网络/DB 抖动导致整轮 Agent 失败。
     * 写库类工具（如 remember_fact）刻意排除：重试会重复入库，与按钮防抖节流目标相悖。
     */
    private static final Set<String> READONLY_RETRYABLE = Set.of(
            "search_recipes", "recall_user_profile", "check_recent_history",
            "check_dietary_conflicts", "score_menu_plan", "consolidate_ingredients");

    public ToolResult execute(String name, String argumentsJson, ToolContext context) {
        AgentTool tool = tools.get(name);
        if (tool == null) {
            return ToolResult.failed("没有名为 " + name + " 的工具，可用工具：" + String.join("、", tools.keySet()));
        }
        String args = argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson;
        try {
            return tool.run(args, context);
        } catch (Exception ex) {
            if (READONLY_RETRYABLE.contains(name)) {
                try {
                    return tool.run(args, context);
                } catch (Exception retryEx) {
                    // 重试仍失败，收敛为失败结果，交由 AgentLoop 反馈给模型
                }
            }
            return ToolResult.failed("工具 " + name + " 执行失败：" + ex.getMessage());
        }
    }

    /** 供 JSON 工具协议（供应商不支持 function calling 时）使用的文本声明。 */
    public String describeForPrompt(Set<String> names) {
        StringBuilder text = new StringBuilder();
        specs(names).forEach(spec -> text.append("- ")
                .append(spec.name()).append("：").append(spec.description())
                .append(" 参数 schema=").append(spec.parametersJson()).append('\n'));
        return text.toString();
    }
}
