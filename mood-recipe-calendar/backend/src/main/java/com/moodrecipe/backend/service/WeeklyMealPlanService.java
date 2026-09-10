package com.moodrecipe.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.entity.WeeklyMealPlan;
import com.moodrecipe.backend.repository.RecipeRepository;
import com.moodrecipe.backend.repository.WeeklyMealPlanRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class WeeklyMealPlanService {
    private final WeeklyMealPlanRepository plans;
    private final RecipeRepository recipes;
    private final ObjectMapper json;

    public WeeklyMealPlanService(WeeklyMealPlanRepository plans, RecipeRepository recipes, ObjectMapper json) {
        this.plans = plans; this.recipes = recipes; this.json = json;
    }

    public PlanView generate(String openid, GenerateRequest request) {
        int days = Math.max(3, Math.min(request.days(), 7));
        List<Recipe> source = recipes.findAll();
        if (source.isEmpty()) throw new IllegalStateException("菜谱库还在准备中，请稍后重试");
        List<PlanDay> result = new ArrayList<>();
        for (int i = 0; i < days; i++) result.add(toDay(source.get(i % source.size()), i, request.people(), request.healthGoal()));
        WeeklyMealPlan plan = plans.findTopByOpenidOrderByUpdatedAtDesc(openid).orElseGet(WeeklyMealPlan::new);
        plan.setOpenid(openid); plan.setPlanJson(write(result)); plan.setShoppingJson(write(merge(result, List.of())));
        return view(plans.save(plan));
    }

    public Optional<PlanView> current(String openid) { return plans.findTopByOpenidOrderByUpdatedAtDesc(openid).map(this::view); }

    public Optional<PlanView> replaceDay(String openid, Long id, int index) {
        return plans.findById(id).filter(p -> p.getOpenid().equals(openid)).map(plan -> {
            List<PlanDay> days = readDays(plan.getPlanJson());
            if (index < 0 || index >= days.size()) throw new IllegalArgumentException("计划日期无效");
            List<Recipe> source = recipes.findAll();
            if (source.isEmpty()) throw new IllegalStateException("菜谱库还在准备中，请稍后重试");
            days.set(index, toDay(source.get((index + 1) % source.size()), index, 2, "BALANCED"));
            plan.setPlanJson(write(days)); plan.setShoppingJson(write(merge(days, readShopping(plan.getShoppingJson()))));
            return view(plans.save(plan));
        });
    }

    public Optional<PlanView> toggleShopping(String openid, Long id, String name) {
        return plans.findById(id).filter(p -> p.getOpenid().equals(openid)).map(plan -> {
            List<ShoppingItem> items = readShopping(plan.getShoppingJson());
            items.stream().filter(i -> i.name().equals(name)).findFirst().ifPresent(i -> i.purchased = !i.purchased);
            plan.setShoppingJson(write(items)); return view(plans.save(plan));
        });
    }

    private PlanDay toDay(Recipe recipe, int index, int people, String healthGoal) {
        List<String> ingredients = ingredients(recipe.getIngredients());
        List<String> steps = steps(recipe.getSteps());
        if (steps.isEmpty()) steps = List.of("食材洗净切好。", "锅中少油加热。", "按食材易熟程度依次下锅。", "调味后炒熟即可。");
        return new PlanDay("周" + "一二三四五六日".charAt(index), recipe.getName(), ingredients,
                steps.stream().limit(5).toList(), index == 0 ? "这周会优先复用常见蔬菜和调料。" : "和前几天共用部分调料，少买一点也够用。",
                "FITNESS".equals(healthGoal) ? "搭配优质蛋白、主食和蔬菜。" : "LEAN".equals(healthGoal) ? "搭配蔬菜、优质蛋白和少油做法。" : "搭配一份主食和蔬菜，吃得更完整。"
        );
    }
    private List<ShoppingItem> merge(List<PlanDay> days, List<ShoppingItem> old) {
        Map<String, Boolean> bought = new HashMap<>(); old.forEach(i -> bought.put(i.name(), i.purchased));
        Map<String, Integer> counts = new LinkedHashMap<>();
        days.forEach(d -> d.ingredients().forEach(raw -> counts.merge(normalize(raw), 1, Integer::sum)));
        return counts.entrySet().stream().map(e -> new ShoppingItem(e.getKey(), category(e.getKey()), e.getValue() + " 份", bought.getOrDefault(e.getKey(), false))).toList();
    }
    private String category(String name) {
        if (name.matches(".*(鸡|肉|鱼|虾|蛋|豆腐|豆).*$")) return "肉蛋豆";
        if (name.matches(".*(米|面|粉|馒头|土豆).*$")) return "主食";
        if (name.matches(".*(盐|油|酱|醋|料酒|糖).*$")) return "调料";
        return "蔬菜";
    }
    private String normalize(String raw) { return raw.replaceAll("[0-9０-９]+(?:g|克|个|根|颗|块|勺|适量|份)?", "").replaceAll("[，,、].*", "").trim(); }
    private List<String> ingredients(String value) { return readStrings(value); }
    private List<String> steps(String value) { return readStrings(value); }
    private List<String> readStrings(String value) { try { return json.readValue(value, new TypeReference<List<String>>() {}); } catch (Exception e) { return value == null || value.isBlank() ? List.of() : List.of(value); } }
    private List<PlanDay> readDays(String value) { try { return json.readValue(value, new TypeReference<List<PlanDay>>() {}); } catch (Exception e) { return List.of(); } }
    private List<ShoppingItem> readShopping(String value) { try { return json.readValue(value, new TypeReference<List<ShoppingItem>>() {}); } catch (Exception e) { return List.of(); } }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException("计划保存失败"); } }
    private PlanView view(WeeklyMealPlan plan) { return new PlanView(plan.getId(), readDays(plan.getPlanJson()), readShopping(plan.getShoppingJson())); }

    public record GenerateRequest(int people, int days, String healthGoal) {}
    public record PlanView(Long id, List<PlanDay> days, List<ShoppingItem> shopping) {}
    public record PlanDay(String day, String dishName, List<String> ingredients, List<String> steps, String reuseHint, String healthTip) {}
    public static class ShoppingItem { public String name; public String category; public String quantity; public boolean purchased; public ShoppingItem() {} ShoppingItem(String n,String c,String q,boolean p){name=n;category=c;quantity=q;purchased=p;} public String name(){return name;} }
}
