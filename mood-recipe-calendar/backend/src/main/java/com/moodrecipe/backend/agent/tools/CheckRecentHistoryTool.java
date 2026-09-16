package com.moodrecipe.backend.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.moodrecipe.backend.agent.AgentJson;
import com.moodrecipe.backend.agent.AgentTool;
import com.moodrecipe.backend.agent.ToolContext;
import com.moodrecipe.backend.agent.ToolResult;
import com.moodrecipe.backend.entity.UserRecord;
import com.moodrecipe.backend.repository.RecipeInteractionRepository;
import com.moodrecipe.backend.repository.RecipeRepository;
import com.moodrecipe.backend.repository.UserRecordRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 查最近吃过什么、喜欢什么、拒绝过什么，避免周菜单把上周的菜再排一遍。 */
@Service
public class CheckRecentHistoryTool implements AgentTool {

    private final UserRecordRepository records;
    private final RecipeInteractionRepository interactions;
    private final RecipeRepository recipes;

    public CheckRecentHistoryTool(UserRecordRepository records, RecipeInteractionRepository interactions,
                                  RecipeRepository recipes) {
        this.records = records;
        this.interactions = interactions;
        this.recipes = recipes;
    }

    @Override
    public String name() {
        return "check_recent_history";
    }

    @Override
    public String description() {
        return "查询用户最近吃过、喜欢过和明确拒绝过的菜，用于避免重复推荐。";
    }

    @Override
    public String parametersJson() {
        return """
                {"type":"object","properties":{
                  "days":{"type":"integer","description":"往前看多少天，默认 14"}
                }}""";
    }

    @Override
    public ToolResult run(String argumentsJson, ToolContext context) {
        JsonNode args = AgentJson.parse(context.json(), argumentsJson);
        int days = Math.max(1, Math.min(args.path("days").asInt(14), 60));
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(days);

        List<String> recentDishes = new ArrayList<>();
        for (UserRecord record : records.findTop30ByOpenidOrderByCreatedAtDesc(context.openid())) {
            String date = record.getRecordDate();
            String dish = record.getDishName();
            if (date == null || dish == null || dish.isBlank()) continue;
            try {
                LocalDate value = LocalDate.parse(date);
                if (!value.isBefore(from) && !value.isAfter(today)) recentDishes.add(dish.trim());
            } catch (RuntimeException ignored) {
                // 日期不合法就跳过
            }
        }

        var history = interactions.findTop30ByOpenidOrderByCreatedAtDesc(context.openid());
        Set<Long> ids = new HashSet<>();
        history.forEach(item -> {
            if (item.getRecipeId() != null) ids.add(item.getRecipeId());
        });
        Map<Long, String> names = new HashMap<>();
        if (!ids.isEmpty()) {
            recipes.findAllById(ids).forEach(recipe -> {
                if (recipe.getName() != null) names.put(recipe.getId(), recipe.getName());
            });
        }
        List<String> loved = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        history.forEach(item -> {
            String name = names.get(item.getRecipeId());
            if (name == null || name.isBlank()) return;
            if ("LIKE".equals(item.getAction()) || "MADE".equals(item.getAction())) {
                if (!loved.contains(name)) loved.add(name);
            } else if ("DISLIKE".equals(item.getAction())) {
                if (!rejected.contains(name)) rejected.add(name);
            }
        });

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("days", days);
        output.put("recentDishes", recentDishes.stream().distinct().limit(12).toList());
        output.put("lovedDishes", loved.stream().limit(8).toList());
        output.put("rejectedDishes", rejected.stream().limit(8).toList());
        output.put("hint", "recentDishes 里的菜不要再排进本周菜单；rejectedDishes 是用户明确拒绝过的。");
        return ToolResult.ok(context.json(), output);
    }
}
