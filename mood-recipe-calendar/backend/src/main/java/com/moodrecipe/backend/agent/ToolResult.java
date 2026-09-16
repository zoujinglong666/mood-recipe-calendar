package com.moodrecipe.backend.agent;

import com.fasterxml.jackson.databind.ObjectMapper;

/** 工具执行结果。工具永不抛异常到循环外，失败也以结构化结果返回给模型看。 */
public record ToolResult(String outputJson, boolean failed, String error) {

    public static ToolResult ok(String outputJson) {
        return new ToolResult(outputJson, false, "");
    }

    public static ToolResult ok(ObjectMapper json, Object value) {
        try {
            return new ToolResult(json.writeValueAsString(value), false, "");
        } catch (Exception ex) {
            return failed("结果序列化失败：" + ex.getMessage());
        }
    }

    public static ToolResult failed(String error) {
        return new ToolResult("{\"error\":\"" + (error == null ? "unknown" : error.replace("\"", "'")) + "\"}", true, error);
    }
}
