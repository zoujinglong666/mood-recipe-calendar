package com.moodrecipe.backend.config;

import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.repository.RecipeRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 把随版本发布的菜谱目录幂等补齐到数据库，不改已有主键。 */
@Component
public class RecipeCatalogSeeder implements ApplicationRunner {
    static final String RESOURCE = "recipe-catalog-v2.psv";
    private static final String FALLBACK_IMAGE = "/static/dish_tomato_beef.png";
    private final RecipeRepository recipes;

    public RecipeCatalogSeeder(RecipeRepository recipes) {
        this.recipes = recipes;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        List<Recipe> changed = new ArrayList<>();
        for (CatalogItem item : loadCatalog()) {
            Recipe recipe = recipes.findByName(item.name()).orElseGet(Recipe::new);
            if (recipe.getId() == null || isPlaceholder(recipe)) {
                apply(recipe, item);
                changed.add(recipe);
            }
        }
        recipes.saveAll(changed);

        // 旧版 117 道菜使用了“主料/配菜”占位文本；保留主键，只替换占位内容。
        List<Recipe> repaired = recipes.findAll().stream().filter(this::isPlaceholder).toList();
        repaired.forEach(this::repairLegacy);
        recipes.saveAll(repaired);
    }

    static List<CatalogItem> loadCatalog() throws IOException {
        Map<String, CatalogItem> unique = new LinkedHashMap<>();
        ClassPathResource resource = new ClassPathResource(RESOURCE);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] part = line.split("\\|", -1);
                if (part.length != 8) throw new IllegalStateException("菜谱目录字段数错误: " + line);
                CatalogItem item = new CatalogItem(part[0], part[1], Integer.parseInt(part[2]), part[3],
                        part[4], part[5], part[6], part[7]);
                if (unique.putIfAbsent(item.name(), item) != null) {
                    throw new IllegalStateException("菜谱名称重复: " + item.name());
                }
            }
        }
        return List.copyOf(unique.values());
    }

    private void apply(Recipe recipe, CatalogItem item) {
        recipe.setName(item.name());
        recipe.setMoodTags(item.moods());
        recipe.setCookingTime(item.minutes());
        recipe.setDifficulty(item.difficulty());
        recipe.setDescription(item.description());
        recipe.setSeason("四季");
        recipe.setImage(item.image().isBlank() ? FALLBACK_IMAGE : item.image());
        recipe.setIngredients(item.ingredients());
        recipe.setSteps(item.steps());
    }

    private boolean isPlaceholder(Recipe recipe) {
        String ingredients = recipe.getIngredients() == null ? "" : recipe.getIngredients();
        return ingredients.contains("主料") || ingredients.contains("配菜") || ingredients.contains("主食 1 份");
    }

    private void repairLegacy(Recipe recipe) {
        String name = recipe.getName() == null ? "家常小炒" : recipe.getName();
        List<String> foods = LEGACY_FOODS.entrySet().stream()
                .filter(entry -> name.contains(entry.getKey()))
                .map(Map.Entry::getValue).distinct().limit(3).toList();
        if (foods.isEmpty()) foods = List.of("鸡蛋 2个", "小白菜 200g");
        List<String> ingredients = new ArrayList<>(foods);
        ingredients.add("蒜 5g");
        ingredients.add("生抽 10ml");
        ingredients.add("食用油 10ml");
        recipe.setIngredients(json(ingredients));
        recipe.setSteps(json(List.of(
                "将" + String.join("、", foods) + "洗净并切成适合入口的大小。",
                "锅中放食用油烧至五成热，放入蒜末炒出香味。",
                "加入主要食材翻炒或加少量清水焖至完全熟透。",
                "加入生抽翻匀，尝味后即可装盘。")));
    }

    private String json(List<String> values) {
        return "[\"" + String.join("\",\"", values) + "\"]";
    }

    static record CatalogItem(String name, String moods, int minutes, String difficulty,
                              String description, String image, String ingredients, String steps) { }

    private static final Map<String, String> LEGACY_FOODS = Map.ofEntries(
            Map.entry("番茄", "番茄 2个"), Map.entry("鸡蛋", "鸡蛋 3个"), Map.entry("蛋", "鸡蛋 3个"),
            Map.entry("鸡", "鸡肉 300g"), Map.entry("鸭", "鸭肉 300g"), Map.entry("牛", "牛肉 250g"),
            Map.entry("羊", "羊肉 250g"), Map.entry("排骨", "排骨 350g"), Map.entry("肉", "猪瘦肉 250g"),
            Map.entry("虾", "虾仁 200g"), Map.entry("鱼", "鱼肉 300g"), Map.entry("豆腐", "豆腐 300g"),
            Map.entry("土豆", "土豆 2个"), Map.entry("茄子", "茄子 2根"), Map.entry("白菜", "白菜 300g"),
            Map.entry("西兰花", "西兰花 250g"), Map.entry("黄瓜", "黄瓜 2根"), Map.entry("木耳", "泡发木耳 150g"),
            Map.entry("香菇", "鲜香菇 180g"), Map.entry("萝卜", "白萝卜 300g"), Map.entry("南瓜", "南瓜 300g"),
            Map.entry("面", "鲜面条 250g"), Map.entry("饭", "熟米饭 300g"), Map.entry("粥", "大米 100g"),
            Map.entry("豆角", "豆角 250g"), Map.entry("芹菜", "芹菜 200g"), Map.entry("菠菜", "菠菜 200g"),
            Map.entry("藕", "莲藕 250g"), Map.entry("山药", "山药 250g"), Map.entry("玉米", "甜玉米 1根"));
}
