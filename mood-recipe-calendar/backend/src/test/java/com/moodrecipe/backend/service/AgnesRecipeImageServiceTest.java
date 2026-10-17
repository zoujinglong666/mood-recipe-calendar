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
        assertTrue(body.get("prompt").toString().contains("只出现完成后的这道菜"));
        assertTrue(!body.get("prompt").toString().contains("牛腩 500克"));
        assertTrue(!body.get("prompt").toString().contains("番茄 3个"));
        assertEquals("https://cdn.example.com/dish.png",
                service.imageUrl("{\"data\":[{\"url\":\"https://cdn.example.com/dish.png\"}]}").orElseThrow());
    }

    @Test
    void rejectsMissingOrInsecureImageUrls() {
        assertTrue(service.imageUrl("{\"data\":[]}").isEmpty());
        assertTrue(service.imageUrl("{\"data\":[{\"url\":\"http://example.com/dish.png\"}]}").isEmpty());
    }

    @Test
    void comboNameRendersTwoSeparateBowls() {
        Recipe recipe = new Recipe();
        recipe.setName("鲜肉馄饨 · 配米糊");

        String prompt = service.requestBody(recipe).get("prompt").toString();

        // 两样食物都进画面，且要求分碗并列
        assertTrue(prompt.contains("鲜肉馄饨"));
        assertTrue(prompt.contains("米糊"));
        assertTrue(prompt.contains("绝不装进同一个盘子"));
        // 连接词「配」被剥掉，不把「配米糊」当整体词喂给模型
        assertTrue(!prompt.contains("配米糊"));
        // 生肉防线：菜名里的「鲜肉」必须被解释为馅料，不得画成生肉
        assertTrue(prompt.contains("严禁出现生肉块、生肉馅"));
    }

    @Test
    void comboPartsSplitsAndStripsConnectives() {
        assertEquals(java.util.List.of("鲜肉馄饨", "米糊"), AgnesRecipeImageService.comboParts("鲜肉馄饨·配米糊"));
        assertEquals(java.util.List.of("鲜虾肠粉", "牛奶"), AgnesRecipeImageService.comboParts("鲜虾肠粉 · 配牛奶"));
        assertEquals(java.util.List.of("番茄牛腩"), AgnesRecipeImageService.comboParts("番茄牛腩"));
        assertTrue(AgnesRecipeImageService.comboParts(null).isEmpty());
        assertTrue(AgnesRecipeImageService.comboParts("  ").isEmpty());
    }

    @Test
    void textureCueMatchesDishType() {
        // 汤类：油星 + 逆光蒸汽；粥糊：米油；蒸点：半透明皮；饭：锅气；兜底：酱汁挂壁
        assertTrue(AgnesRecipeImageService.textureCue("鲜肉馄饨").contains("蒸汽被逆光照亮"));
        assertTrue(AgnesRecipeImageService.textureCue("南瓜米糊").contains("米油"));
        assertTrue(AgnesRecipeImageService.textureCue("鲜肉小笼包").contains("半透明"));
        assertTrue(AgnesRecipeImageService.textureCue("酱油焖饭").contains("锅气"));
        assertTrue(AgnesRecipeImageService.textureCue("番茄牛腩").contains("酱汁油亮挂壁"));
        assertTrue(AgnesRecipeImageService.textureCue(null).contains("酱汁油亮挂壁"));
    }

    @Test
    void promptCarriesPhysicalAppetiteSignals() {
        Recipe recipe = new Recipe();
        recipe.setName("鲜肉馄饨 · 配米糊");
        String prompt = service.requestBody(recipe).get("prompt").toString();
        // 器材语言 + 逆光蒸汽是真实感骨架，必须在 prompt 里
        assertTrue(prompt.contains("85mm"));
        assertTrue(prompt.contains("蒸汽被逆光照亮"));
        // 组合餐的质地描述按主菜（左边）取，不是饮品
        assertTrue(prompt.contains("隐约可见馅色"));
    }
}
