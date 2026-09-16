package com.moodrecipe.backend.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.moodrecipe.backend.agent.AgentJson;
import com.moodrecipe.backend.agent.AgentTool;
import com.moodrecipe.backend.agent.MenuQualityScorer;
import com.moodrecipe.backend.agent.ToolContext;
import com.moodrecipe.backend.agent.ToolResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 合并食材、统计复用率，给出"哪几样可以一次买够"的买菜建议。 */
@Service
public class ConsolidateIngredientsTool implements AgentTool {

    @Override
    public String name() {
        return "consolidate_ingredients";
    }

    @Override
    public String description() {
        return "合并一周菜单里的食材，统计每种食材出现在几天，给出买菜清单和复用建议。";
    }

    @Override
    public String parametersJson() {
        return """
                {"type":"object","properties":{
                  "days":{"type":"array","items":{"type":"object","properties":{
                    "weekday":{"type":"integer"},
                    "dishes":{"type":"array","items":{"type":"object","properties":{
                      "name":{"type":"string"},
                      "ingredients":{"type":"array","items":{"type":"string"}}
                    }}}
                  }}}
                }}""";
    }

    @Override
    public ToolResult run(String argumentsJson, ToolContext context) {
        JsonNode args = AgentJson.parse(context.json(), argumentsJson);
        Map<String, Set<Integer>> byDay = new LinkedHashMap<>();
        JsonNode daysNode = args.path("days");
        if (daysNode.isArray()) {
            for (JsonNode day : daysNode) {
                int weekday = day.path("weekday").asInt(0);
                for (MenuQualityScorer.DishInput dish : AgentDishes.read(context.json(), day.path("dishes"))) {
                    for (String raw : dish.ingredients()) {
                        String key = MenuQualityScorer.normalizeIngredient(raw);
                        if (key.isBlank()) continue;
                        byDay.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(weekday);
                    }
                }
            }
        }
        List<Map<String, Object>> items = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        for (Map.Entry<String, Set<Integer>> entry : byDay.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", entry.getKey());
            item.put("days", entry.getValue().size());
            item.put("category", category(entry.getKey()));
            items.add(item);
            if (entry.getValue().size() >= 2 && suggestions.size() < 6) {
                suggestions.add(entry.getKey() + "出现在 " + entry.getValue().size() + " 天，可以一次买够");
            }
        }
        double reuseRate = items.isEmpty() ? 0d
                : items.stream().filter(item -> (Integer) item.get("days") >= 2).count() / (double) items.size();
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("items", items);
        output.put("reuseRate", Math.round(reuseRate * 100d) / 100d);
        output.put("suggestions", suggestions);
        return ToolResult.ok(context.json(), output);
    }

    private static String category(String name) {
        if (name.matches(".*(鸡|肉|鱼|虾|蛋|豆).*")) return "肉蛋豆";
        if (name.matches(".*(米|面|粉|馒头|土豆).*")) return "主食";
        if (name.matches(".*(盐|油|酱|醋|料酒|糖|生抽|老抽).*")) return "调料";
        return "蔬菜";
    }
}
