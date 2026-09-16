package com.moodrecipe.backend.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.moodrecipe.backend.agent.AgentJson;
import com.moodrecipe.backend.agent.AgentTool;
import com.moodrecipe.backend.agent.ToolContext;
import com.moodrecipe.backend.agent.ToolResult;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.repository.RecipeRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 从菜谱库里检索候选菜，供模型在规划或换菜时挑选，而不是凭空编造菜名。 */
@Service
public class SearchRecipeTool implements AgentTool {

    private final RecipeRepository recipes;

    public SearchRecipeTool(RecipeRepository recipes) {
        this.recipes = recipes;
    }

    @Override
    public String name() {
        return "search_recipes";
    }

    @Override
    public String description() {
        return "在菜谱库中检索候选菜。需要真实存在的菜名、食材和做法时使用，可限定菜系、关键词和最长烹饪时间。";
    }

    @Override
    public String parametersJson() {
        return """
                {"type":"object","properties":{
                  "query":{"type":"string","description":"菜名或食材关键词，可为空"},
                  "cuisine":{"type":"string","description":"菜系，如赣菜、川菜、粤菜，可为空"},
                  "limit":{"type":"integer","description":"最多返回几条，默认 8"},
                  "maxMinutes":{"type":"integer","description":"最长烹饪时间，可为空"}
                }}""";
    }

    @Override
    public ToolResult run(String argumentsJson, ToolContext context) {
        JsonNode args = AgentJson.parse(context.json(), argumentsJson);
        String query = AgentJson.text(args, "query");
        String cuisine = AgentJson.text(args, "cuisine");
        int limit = Math.max(1, Math.min(args.path("limit").asInt(8), 20));
        int maxMinutes = args.path("maxMinutes").asInt(0);

        List<Recipe> pool = new ArrayList<>(recipes.findAiWithImages());
        pool.addAll(recipes.findAll());
        Map<String, Recipe> unique = new LinkedHashMap<>();
        for (Recipe recipe : pool) {
            if (recipe.getName() == null || recipe.getName().isBlank()) continue;
            unique.putIfAbsent(recipe.getName().trim(), recipe);
        }
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (Recipe recipe : unique.values()) {
            String text = (recipe.getName() == null ? "" : recipe.getName()) + " "
                    + (recipe.getDescription() == null ? "" : recipe.getDescription()) + " "
                    + (recipe.getIngredients() == null ? "" : recipe.getIngredients());
            if (!query.isBlank() && !text.contains(query)) continue;
            if (!cuisine.isBlank() && !text.contains(cuisine)) continue;
            if (maxMinutes > 0 && recipe.getCookingTime() != null && recipe.getCookingTime() > maxMinutes) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", recipe.getName());
            item.put("ingredients", AgentJson.readStrings(context.json(), recipe.getIngredients()));
            item.put("cookingTime", recipe.getCookingTime() == null ? 30 : recipe.getCookingTime());
            item.put("difficulty", recipe.getDifficulty() == null ? "简单" : recipe.getDifficulty());
            candidates.add(item);
            if (candidates.size() >= limit) break;
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("count", candidates.size());
        output.put("candidates", candidates);
        return ToolResult.ok(context.json(), output);
    }
}
