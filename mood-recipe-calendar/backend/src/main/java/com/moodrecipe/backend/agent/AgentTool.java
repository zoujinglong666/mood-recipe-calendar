package com.moodrecipe.backend.agent;

/** 智能体可调用的工具。声明用 JSON Schema，执行返回 JSON 字符串结果。 */
public interface AgentTool {

    String name();

    String description();

    /** JSON Schema（object 类型）字符串。 */
    String parametersJson();

    ToolResult run(String argumentsJson, ToolContext context);
}
