package com.moodrecipe.backend.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.agent.AgentJson;
import com.moodrecipe.backend.agent.MenuQualityScorer;

import java.util.ArrayList;
import java.util.List;

/** 把模型给出的任意 JSON 菜数组解析成质量校验能吃的输入。 */
final class AgentDishes {

    private AgentDishes() {
    }

    static List<MenuQualityScorer.DishInput> read(ObjectMapper json, JsonNode array) {
        List<MenuQualityScorer.DishInput> dishes = new ArrayList<>();
        if (array == null || !array.isArray()) return dishes;
        for (JsonNode item : array) {
            String name = item.path("name").asText("").trim();
            if (name.isEmpty()) continue;
            dishes.add(new MenuQualityScorer.DishInput(
                    name,
                    item.path("role").asText("MAIN"),
                    AgentJson.strings(item, "ingredients"),
                    item.hasNonNull("cookingTime") ? item.path("cookingTime").asInt(30) : 30,
                    item.path("difficulty").asText("简单")));
        }
        return dishes;
    }
}
