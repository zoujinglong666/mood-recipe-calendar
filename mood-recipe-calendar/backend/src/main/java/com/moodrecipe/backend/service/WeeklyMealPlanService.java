package com.moodrecipe.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.entity.WeeklyMealPlan;
import com.moodrecipe.backend.repository.WeeklyMealPlanRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class WeeklyMealPlanService {
    private final WeeklyMealPlanRepository plans;
    private final GuozaiAgent agent;
    private final AgnesRecipeImageService images;
    private final WechatSubscriptionMessageService subscriptions;
    private final ObjectMapper json;

    public WeeklyMealPlanService(WeeklyMealPlanRepository plans, GuozaiAgent agent, AgnesRecipeImageService images, WechatSubscriptionMessageService subscriptions, ObjectMapper json) {
        this.plans = plans;
        this.agent = agent;
        this.images = images;
        this.subscriptions = subscriptions;
        this.json = json;
    }

    /** 每次生成保留旧计划，历史页可直接回看，不会覆盖收藏。 */
    public PlanView generate(String openid, GenerateRequest request) {
        int days = Math.max(3, Math.min(request.days(), 7));
        int dishesPerDay = request.dishesPerDay() > 0
                ? Math.max(1, Math.min(request.dishesPerDay(), 3))
                : request.people() >= 3 ? 2 : 1;
        List<Recipe> source = agent.planWeeklyMenu(openid, days, dishesPerDay, request.healthGoal());
        if (source.isEmpty()) throw new IllegalStateException("没有找到符合你口味和忌口的菜谱，请先完善锅仔记忆");
        List<PlanDay> result = new ArrayList<>();
        for (int i = 0; i < days; i++) result.add(toDay(source, i, dishesPerDay, request.healthGoal()));
        WeeklyMealPlan plan = new WeeklyMealPlan();
        plan.setOpenid(openid);
        plan.setPlanJson(write(result));
        plan.setShoppingJson(write(merge(result, List.of())));
        PlanView view = view(plans.save(plan));
        if (request.sendNotification()) subscriptions.sendWeeklyPlanCompleted(openid, view.id());
        return view;
    }

    public Optional<PlanView> current(String openid) {
        return plans.findTopByOpenidOrderByCreatedAtDesc(openid).map(this::view);
    }

    public List<PlanSummary> history(String openid) {
        return plans.findTop20ByOpenidOrderByCreatedAtDesc(openid).stream()
                .sorted(Comparator.comparing(WeeklyMealPlan::isFavorite).reversed())
                .map(this::summary).toList();
    }

    public Optional<PlanView> get(String openid, Long id) {
        return owned(openid, id).map(this::view);
    }

    public Optional<PlanView> toggleFavorite(String openid, Long id) {
        return owned(openid, id).map(plan -> {
            plan.setFavorite(!plan.isFavorite());
            return view(plans.save(plan));
        });
    }

    /** 图片只在用户看到该卡片时生成，失败时保留静态图或无图状态。 */
    public Optional<PlanView> ensureCover(String openid, Long id, int index) {
        return owned(openid, id).map(plan -> {
            WeeklyMealPlan saved = plan;
            List<PlanDay> days = readDays(plan.getPlanJson());
            if (index < 0 || index >= days.size()) throw new IllegalArgumentException("计划日期无效");
            PlanDay day = days.get(index);
            if (day.imageUrl() == null || day.imageUrl().isBlank()) {
                Recipe coverRecipe = new Recipe();
                coverRecipe.setName(dishesFor(day).get(0).name());
                coverRecipe.setDescription(day.healthTip());
                coverRecipe.setIngredients(write(day.ingredients()));
                images.generateCover(coverRecipe).ifPresent(url -> days.set(index, day.withImage(url)));
                plan.setPlanJson(write(days));
                saved = plans.save(plan);
            }
            return view(saved);
        });
    }

    public Optional<PlanView> replaceDay(String openid, Long id, int index) {
        return owned(openid, id).map(plan -> {
            List<PlanDay> days = readDays(plan.getPlanJson());
            if (index < 0 || index >= days.size()) throw new IllegalArgumentException("计划日期无效");
            int dishesPerDay = Math.max(1, dishesFor(days.get(index)).size());
            List<Recipe> source = agent.planWeeklyMenu(openid, 1, dishesPerDay, "BALANCED");
            if (source.isEmpty()) throw new IllegalStateException("没有找到符合你口味和忌口的替换菜谱");
            days.set(index, toDay(source, 0, dishesPerDay, "BALANCED"));
            plan.setPlanJson(write(days));
            plan.setShoppingJson(write(merge(days, readShopping(plan.getShoppingJson()))));
            return view(plans.save(plan));
        });
    }

    public Optional<PlanView> toggleShopping(String openid, Long id, String name) {
        return owned(openid, id).map(plan -> {
            List<ShoppingItem> items = readShopping(plan.getShoppingJson());
            items.stream().filter(item -> item.name().equals(name)).findFirst().ifPresent(item -> item.purchased = !item.purchased);
            plan.setShoppingJson(write(items));
            return view(plans.save(plan));
        });
    }

    private Optional<WeeklyMealPlan> owned(String openid, Long id) {
        return plans.findById(id).filter(plan -> plan.getOpenid().equals(openid));
    }

    private PlanDay toDay(List<Recipe> source, int index, int dishesPerDay, String healthGoal) {
        List<PlanDish> dishes = new ArrayList<>();
        for (int offset = 0; offset < dishesPerDay; offset++) {
            Recipe recipe = source.get((index * dishesPerDay + offset) % source.size());
            dishes.add(toDish(recipe));
        }
        PlanDish mainDish = dishes.get(0);
        String dishName = dishes.stream().map(PlanDish::name).reduce((first, second) -> first + " · " + second).orElse("今晚的菜单");
        return new PlanDay("周" + "一二三四五六日".charAt(index), dishName, mainDish.ingredients(), mainDish.steps(),
                index == 0 ? "这周会优先复用常见蔬菜和调料。" : "和前几天共用部分调料，少买一点也够用。",
                "FITNESS".equals(healthGoal) ? "搭配优质蛋白、主食和蔬菜。" : "LEAN".equals(healthGoal) ? "搭配蔬菜、优质蛋白和少油做法。" : "搭配一份主食和蔬菜，吃得更完整。",
                null, mainDish.fallbackImageUrl(), dishes);
    }

    private PlanDish toDish(Recipe recipe) {
        List<String> ingredients = ingredients(recipe.getIngredients());
        List<String> steps = steps(recipe.getSteps());
        if (steps.isEmpty()) steps = List.of("食材洗净切好。", "锅中少油加热。", "按食材易熟程度依次下锅。", "调味后炒熟即可。");
        return new PlanDish(recipe.getName(), ingredients, steps.stream().limit(5).toList(), recipe.getImage());
    }

    private List<ShoppingItem> merge(List<PlanDay> days, List<ShoppingItem> old) {
        Map<String, Boolean> bought = new HashMap<>();
        old.forEach(item -> bought.put(item.name(), item.purchased));
        Map<String, Integer> counts = new LinkedHashMap<>();
        days.forEach(day -> dishesFor(day).forEach(dish -> dish.ingredients().forEach(raw -> counts.merge(normalize(raw), 1, Integer::sum))));
        return counts.entrySet().stream().map(entry -> new ShoppingItem(entry.getKey(), category(entry.getKey()), entry.getValue() + " 份", bought.getOrDefault(entry.getKey(), false))).toList();
    }

    private String category(String name) {
        if (name.matches(".*(鸡|肉|鱼|虾|蛋|豆腐|豆).*$")) return "肉蛋豆";
        if (name.matches(".*(米|面|粉|馒头|土豆).*$")) return "主食";
        if (name.matches(".*(盐|油|酱|醋|料酒|糖).*$")) return "调料";
        return "蔬菜";
    }

    private String normalize(String raw) { return raw.replaceAll("[0-9０-９]+(?:g|克|个|根|颗|块|勺|适量|份)?", "").replaceAll("[，,、].*", "").trim(); }
    private List<PlanDish> dishesFor(PlanDay day) {
        if (day.dishes() != null && !day.dishes().isEmpty()) return day.dishes();
        return List.of(new PlanDish(day.dishName(), day.ingredients(), day.steps(), day.fallbackImageUrl()));
    }
    private List<String> ingredients(String value) { return readStrings(value); }
    private List<String> steps(String value) { return readStrings(value); }
    private List<String> readStrings(String value) { try { return json.readValue(value, new TypeReference<List<String>>() {}); } catch (Exception e) { return value == null || value.isBlank() ? List.of() : List.of(value); } }
    private List<PlanDay> readDays(String value) { try { return json.readValue(value, new TypeReference<List<PlanDay>>() {}); } catch (Exception e) { return List.of(); } }
    private List<ShoppingItem> readShopping(String value) { try { return json.readValue(value, new TypeReference<List<ShoppingItem>>() {}); } catch (Exception e) { return List.of(); } }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException("计划保存失败"); } }
    private PlanView view(WeeklyMealPlan plan) { return new PlanView(plan.getId(), readDays(plan.getPlanJson()), readShopping(plan.getShoppingJson()), plan.isFavorite(), plan.getCreatedAt()); }
    private PlanSummary summary(WeeklyMealPlan plan) { return new PlanSummary(plan.getId(), plan.getCreatedAt(), plan.isFavorite(), readDays(plan.getPlanJson())); }

    public record GenerateRequest(int people, int days, String healthGoal, boolean sendNotification, int dishesPerDay) {}
    public record PlanView(Long id, List<PlanDay> days, List<ShoppingItem> shopping, boolean favorite, LocalDateTime createdAt) {}
    public record PlanSummary(Long id, LocalDateTime createdAt, boolean favorite, List<PlanDay> days) {}
    public record PlanDay(String day, String dishName, List<String> ingredients, List<String> steps, String reuseHint, String healthTip, String imageUrl, String fallbackImageUrl, List<PlanDish> dishes) {
        PlanDay withImage(String imageUrl) { return new PlanDay(day, dishName, ingredients, steps, reuseHint, healthTip, imageUrl, fallbackImageUrl, dishes); }
    }
    public record PlanDish(String name, List<String> ingredients, List<String> steps, String fallbackImageUrl) {}
    public static class ShoppingItem { public String name; public String category; public String quantity; public boolean purchased; public ShoppingItem() {} ShoppingItem(String n, String c, String q, boolean p) { name = n; category = c; quantity = q; purchased = p; } public String name() { return name; } }
}
