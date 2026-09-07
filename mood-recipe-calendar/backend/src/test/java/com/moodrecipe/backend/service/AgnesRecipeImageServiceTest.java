package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.entity.Recipe;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgnesRecipeImageServiceTest {

    private final AgnesRecipeImageService service = new AgnesRecipeImageService(
            new ObjectMapper(), "test-key", "https://example.com/images", "agnes-image-2.5-flash", "1K", "4:3");

    @Test
    void buildsDocumentedPayloadAndReadsHttpsResult() {
        Recipe recipe = new Recipe();
        recipe.setName("番茄牛腩");
        recipe.setDescription("酸甜暖胃的家常菜");
        recipe.setIngredients("[\"牛腩 500克\",\"番茄 3个\"]");

        Map<String, Object> body = service.requestBody(recipe);

        assertEquals("agnes-image-2.5-flash", body.get("model"));
        assertEquals("1K", body.get("size"));
        assertEquals("4:3", body.get("ratio"));
        assertEquals(Map.of("response_format", "url"), body.get("extra_body"));
        assertTrue(body.get("prompt").toString().contains("番茄牛腩"));
        assertEquals("https://cdn.example.com/dish.png",
                service.imageUrl("{\"data\":[{\"url\":\"https://cdn.example.com/dish.png\"}]}").orElseThrow());
    }

    @Test
    void rejectsMissingOrInsecureImageUrls() {
        assertTrue(service.imageUrl("{\"data\":[]}").isEmpty());
        assertTrue(service.imageUrl("{\"data\":[{\"url\":\"http://example.com/dish.png\"}]}").isEmpty());
    }
}
