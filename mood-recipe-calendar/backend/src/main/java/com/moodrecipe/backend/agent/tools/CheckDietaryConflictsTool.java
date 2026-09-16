package com.moodrecipe.backend.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.moodrecipe.backend.agent.AgentJson;
import com.moodrecipe.backend.agent.AgentMemoryStore;
import com.moodrecipe.backend.agent.AgentTool;
import com.moodrecipe.backend.agent.MenuQualityScorer;
import com.moodrecipe.backend.agent.ToolContext;
import com.moodrecipe.backend.agent.ToolResult;
import com.moodrecipe.backend.agent.UserProfile;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 校验一批菜是否触犯忌口、过敏、辣度和老人小孩适配，返回可追责的具体条目。 */
@Service
public class CheckDietaryConflictsTool implements AgentTool {

    private static final Set<String> DIETARY_CODES = Set.of(
            "AVOID_INGREDIENT", "ALLERGEN", "SPICE_CONFLICT",
            "ELDER_SPICY", "ELDER_TEXTURE", "CHILD_SPICY", "CHILD_RISK");

    private final AgentMemoryStore store;
    private final MenuQualityScorer scorer = new MenuQualityScorer();

    public CheckDietaryConflictsTool(AgentMemoryStore store) {
        this.store = store;
    }

    @Override
    public String name() {
        return "check_dietary_conflicts";
    }

    @Override
    public String description() {
        return "检查一批菜名与食材是否违反用户的忌口、过敏、辣度要求，以及是否适合家里的老人和小孩。";
    }

    @Override
    public String parametersJson() {
        return """
                {"type":"object","properties":{
                  "dishes":{"type":"array","description":"待检查的菜","items":{"type":"object","properties":{
                    "name":{"type":"string"},
                    "ingredients":{"type":"array","items":{"type":"string"}},
                    "cookingTime":{"type":"integer"},
                    "difficulty":{"type":"string"}
                  }}}
                }}""";
    }

    @Override
    public ToolResult run(String argumentsJson, ToolContext context) {
        JsonNode args = AgentJson.parse(context.json(), argumentsJson);
        List<MenuQualityScorer.DishInput> dishes = AgentDishes.read(context.json(), args.path("dishes"));
        if (dishes.isEmpty()) {
            return ToolResult.failed("没有提供需要检查的菜");
        }
        UserProfile profile = store.profile(context.openid(), AgentMemoryStore.Scene.WEEKLY_PLAN);
        MenuQualityScorer.MenuQuality quality = scorer.evaluate(
                List.of(new MenuQualityScorer.DayInput(0, dishes)),
                MenuQualityScorer.Constraints.from(profile));

        List<Map<String, Object>> violations = quality.issues().stream()
                .filter(issue -> DIETARY_CODES.contains(issue.code()))
                .map(issue -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("code", issue.code());
                    map.put("severity", issue.severity().name());
                    map.put("dish", issue.dish());
                    map.put("message", issue.message());
                    return map;
                }).toList();

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("clean", violations.isEmpty());
        output.put("violations", violations);
        output.put("checkedAgainst", profile.summary());
        return ToolResult.ok(context.json(), output);
    }
}
