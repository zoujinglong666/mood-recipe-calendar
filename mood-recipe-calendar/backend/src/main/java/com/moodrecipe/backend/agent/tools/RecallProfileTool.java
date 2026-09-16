package com.moodrecipe.backend.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.moodrecipe.backend.agent.AgentJson;
import com.moodrecipe.backend.agent.AgentMemoryStore;
import com.moodrecipe.backend.agent.AgentTool;
import com.moodrecipe.backend.agent.ToolContext;
import com.moodrecipe.backend.agent.ToolResult;
import com.moodrecipe.backend.agent.UserProfile;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 检索用户档案。返回的不只是"偏好文本"，还有每条记忆的置信度、来源、证据，
 * 让智能体之后能解释"为什么今天用了这条记忆"。
 */
@Service
public class RecallProfileTool implements AgentTool {

    private final AgentMemoryStore store;

    public RecallProfileTool(AgentMemoryStore store) {
        this.store = store;
    }

    @Override
    public String name() {
        return "recall_user_profile";
    }

    @Override
    public String description() {
        return "检索这位用户的长期档案：忌口、过敏、辣度、家庭成员、健康目标、预算、偏爱菜系、最近吃过的菜，以及每条记忆的证据与置信度。规划前必须先调用。";
    }

    @Override
    public String parametersJson() {
        return """
                {"type":"object","properties":{
                  "scene":{"type":"string","description":"DIALOGUE 或 WEEKLY_PLAN，默认 WEEKLY_PLAN"},
                  "limit":{"type":"integer","description":"最多返回多少条记忆，默认 10"}
                }}""";
    }

    @Override
    public ToolResult run(String argumentsJson, ToolContext context) {
        JsonNode args = AgentJson.parse(context.json(), argumentsJson);
        String sceneName = AgentJson.text(args, "scene");
        AgentMemoryStore.Scene scene = "DIALOGUE".equalsIgnoreCase(sceneName)
                ? AgentMemoryStore.Scene.DIALOGUE : AgentMemoryStore.Scene.WEEKLY_PLAN;
        int limit = Math.max(1, Math.min(args.path("limit").asInt(10), 20));

        UserProfile profile = store.profile(context.openid(), scene);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("summary", profile.summary());
        output.put("avoidIngredients", profile.avoidIngredients());
        output.put("allergens", profile.allergens());
        output.put("spiceLevel", profile.spiceLevel());
        output.put("hasElder", profile.hasElder());
        output.put("hasChild", profile.hasChild());
        output.put("healthGoal", profile.healthGoal());
        output.put("budget", profile.budget());
        output.put("people", profile.people());
        output.put("favoriteCuisines", profile.favoriteCuisines());
        output.put("recentDishes", profile.recentDishes());
        output.put("lovedDishes", profile.lovedDishes());
        output.put("rejectedDishes", profile.rejectedDishes());
        output.put("cuisineAffinity", profile.cuisineAffinity());
        output.put("skipQuestions", profile.skipQuestions());
        output.put("maxCookingMinutes", profile.maxCookingMinutes());
        output.put("preferSimple", profile.preferSimple());
        output.put("memory", profile.memory().stream().limit(limit).map(item -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("key", item.key());
            map.put("value", item.value());
            map.put("confidence", Math.round(item.confidence() * 100d) / 100d);
            map.put("source", item.source());
            map.put("evidence", item.evidence());
            map.put("reason", item.reason());
            return map;
        }).toList());
        return ToolResult.ok(context.json(), output);
    }
}
