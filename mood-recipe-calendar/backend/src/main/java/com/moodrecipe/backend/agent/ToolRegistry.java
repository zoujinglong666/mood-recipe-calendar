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

    public ToolResult execute(String name, String argumentsJson, ToolContext context) {
        AgentTool tool = tools.get(name);
        if (tool == null) {
            return ToolResult.failed("没有名为 " + name + " 的工具，可用工具：" + String.join("、", tools.keySet()));
        }
        try {
            return tool.run(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson, context);
        } catch (Exception ex) {
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
