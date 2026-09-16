package com.moodrecipe.backend.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.moodrecipe.backend.agent.AgentJson;
import com.moodrecipe.backend.agent.AgentMemoryStore;
import com.moodrecipe.backend.agent.AgentTool;
import com.moodrecipe.backend.agent.ToolContext;
import com.moodrecipe.backend.agent.ToolResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 把这次对话里确认下来的事实写进长期记忆，带置信度、来源和证据。 */
@Service
public class RememberFactTool implements AgentTool {

    private static final Set<String> BASE_KEYS = Set.of(
            AgentMemoryStore.KEY_PEOPLE, AgentMemoryStore.KEY_DISHES, AgentMemoryStore.KEY_DAYS,
            AgentMemoryStore.KEY_SPICE, AgentMemoryStore.KEY_HOUSEHOLD, AgentMemoryStore.KEY_HEALTH_GOAL,
            AgentMemoryStore.KEY_BUDGET, AgentMemoryStore.KEY_CUISINE);

    private static final List<String> PREFIXES = List.of("affinity.", "dish.", "strategy.", "preference.");

    private final AgentMemoryStore store;

    public RememberFactTool(AgentMemoryStore store) {
        this.store = store;
    }

    @Override
    public String name() {
        return "remember_fact";
    }

    @Override
    public String description() {
        return "把用户在本次对话中确认的事实写入长期记忆。必须给出 key、value、confidence（0-1）和 evidence（依据用户原话或行为）。不确定就不要写。";
    }

    @Override
    public String parametersJson() {
        return """
                {"type":"object","properties":{
                  "facts":{"type":"array","items":{"type":"object","properties":{
                    "key":{"type":"string","description":"people / dishesPerDay / cookingDays / spice / household / healthGoal / budget / favoriteCuisine / affinity.菜系 / preference.xxx"},
                    "value":{"type":"string"},
                    "confidence":{"type":"number"},
                    "evidence":{"type":"string"},
                    "ttlDays":{"type":"integer"}
                  }}}
                }}""";
    }

    @Override
    public ToolResult run(String argumentsJson, ToolContext context) {
        JsonNode args = AgentJson.parse(context.json(), argumentsJson);
        JsonNode factsNode = args.path("facts");
        if (!factsNode.isArray() || factsNode.isEmpty()) {
            return ToolResult.failed("没有提供要记住的事实");
        }
        List<String> saved = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (JsonNode item : factsNode) {
            String key = item.path("key").asText("").trim();
            String value = item.path("value").asText("").trim();
            if (!allowed(key) || value.isEmpty()) {
                skipped.add(key.isEmpty() ? "(空 key)" : key);
                continue;
            }
            double confidence = Math.max(0.05, Math.min(item.path("confidence").asDouble(0.7), 0.95));
            String evidence = item.path("evidence").asText("本次对话确认").trim();
            Integer ttl = item.hasNonNull("ttlDays") ? item.path("ttlDays").asInt() : null;
            store.remember(new AgentMemoryStore.RememberCommand(context.openid(), key, value, confidence,
                    confidence >= 0.8 ? AgentMemoryStore.SRC_CHAT : AgentMemoryStore.SRC_INFERRED, evidence, ttl));
            saved.add(key);
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("saved", saved);
        output.put("skipped", skipped);
        return ToolResult.ok(context.json(), output);
    }

    private boolean allowed(String key) {
        if (key == null || key.isBlank()) return false;
        if (BASE_KEYS.contains(key)) return true;
        return PREFIXES.stream().anyMatch(key::startsWith);
    }
}
