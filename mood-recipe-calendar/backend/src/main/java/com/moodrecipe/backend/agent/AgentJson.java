package com.moodrecipe.backend.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/** 工具参数的轻量读取：任何脏输入都不会让工具崩掉。 */
public final class AgentJson {

    private AgentJson() {
    }

    public static JsonNode parse(ObjectMapper json, String raw) {
        try {
            JsonNode node = json.readTree(raw == null || raw.isBlank() ? "{}" : raw);
            return node == null || node.isMissingNode() ? json.createObjectNode() : node;
        } catch (Exception ex) {
            return json.createObjectNode();
        }
    }

    public static String text(JsonNode node, String field) {
        if (node == null) return "";
        String value = node.path(field).asText("");
        return value == null ? "" : value.trim();
    }

    public static List<String> strings(JsonNode node, String field) {
        List<String> values = new ArrayList<>();
        if (node == null) return values;
        JsonNode array = node.path(field);
        if (!array.isArray()) return values;
        for (JsonNode item : array) {
            String value = item.asText("").trim();
            if (!value.isEmpty()) values.add(value);
        }
        return values;
    }

    public static List<String> readStrings(ObjectMapper json, String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            JsonNode node = json.readTree(raw);
            if (node.isArray()) {
                List<String> values = new ArrayList<>();
                for (JsonNode item : node) {
                    String value = item.asText("").trim();
                    if (!value.isEmpty()) values.add(value);
                }
                return values;
            }
        } catch (Exception ignored) {
            // 不是 JSON 数组时按纯文本处理
        }
        return List.of(raw);
    }
}
