package com.moodrecipe.backend.service;

import com.moodrecipe.backend.config.AppClock;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.agent.AgentMemoryStore;
import com.moodrecipe.backend.agent.MenuPlannerAgent;
import com.moodrecipe.backend.agent.MenuQualityScorer;
import com.moodrecipe.backend.entity.Recipe;
import com.moodrecipe.backend.entity.WeeklyMealPlan;
import com.moodrecipe.backend.repository.WeeklyMealPlanRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
public class WeeklyMealPlanService {
    private static final ZoneId CHINA_ZONE = AppClock.ZONE;
    private final WeeklyMealPlanRepository plans;
    private final GuozaiAgent agent;
    private final MenuPlannerAgent planner;
    private final AgentMemoryStore memory;
    private final AgnesRecipeImageService images;
    private final WechatSubscriptionMessageService subscriptions;
    private final ObjectMapper json;

    public WeeklyMealPlanService(WeeklyMealPlanRepository plans, GuozaiAgent agent, MenuPlannerAgent planner,
                                 AgentMemoryStore memory, AgnesRecipeImageService images,
                                 WechatSubscriptionMessageService subscriptions, ObjectMapper json) {
        this.plans = plans;
        this.agent = agent;
        this.planner = planner;
        this.memory = memory;
        this.images = images;
        this.subscriptions = subscriptions;
        this.json = json;
    }

    /** 每次生成保留旧计划，历史页可直接回看，不会覆盖收藏。 */
    public PlanView generate(String openid, GenerateRequest request) {
        return generate(openid, request, (stage, stepStatus, message) -> { });
    }

    /** 带进度回调的生成版本，供异步任务（WeeklyPlanJobService）上报阶段，避免前端长连接超时。 */
    public PlanView generate(String openid, GenerateRequest request, WeeklyPlanJobService.Progress progress) {
        progress.update(WeeklyPlanJobService.Stage.MEMORY, WeeklyPlanJobService.StepStatus.RUNNING, "读取你的口味记忆");
        List<Integer> cookingDays = cookingDays(request);
        int dishesPerDay = request.dishesPerDay() > 0
                ? Math.max(1, Math.min(request.dishesPerDay(), 20))
                : request.people() >= 3 ? 2 : 1;
        String budget = request.budget() == null ? "DAILY" : request.budget();
        String healthGoal = request.healthGoal() == null || request.healthGoal().isBlank()
                ? "BALANCED" : request.healthGoal();
        String notes = request.conversationNotes() == null ? "" : request.conversationNotes().trim();

        MenuPlannerAgent.PlanResult planned = planWithAgent(openid, cookingDays, dishesPerDay, healthGoal, budget, notes);
        List<PlanDay> result = planned == null || planned.days() == null || planned.days().isEmpty()
                ? legacyDays(openid, cookingDays, dishesPerDay, healthGoal, budget, notes)
                : fromPlanned(planned.days());
        if (!displayablePlan(result)) {
            result = legacyDays(openid, cookingDays, dishesPerDay, healthGoal, budget, notes);
        }
        if (!displayablePlan(result)) {
            throw new IllegalStateException("本次菜单数据异常，请重新生成");
        }
        rememberPlannedDishes(openid, result);

        progress.update(WeeklyPlanJobService.Stage.PLAN, WeeklyPlanJobService.StepStatus.RUNNING, "调用锅仔规划这一周");
        WeeklyMealPlan plan = new WeeklyMealPlan();
        plan.setOpenid(openid);
        plan.setPlanJson(write(result));
        plan.setShoppingJson(write(merge(result, List.of())));
        plan.setAgentJson(write(audit(planned)));
        progress.update(WeeklyPlanJobService.Stage.SAVE, WeeklyPlanJobService.StepStatus.RUNNING, "保存周菜单");
        PlanView view = view(plans.save(plan));
        if (request.sendNotification()) subscriptions.sendWeeklyPlanCompleted(openid, view.id());
        progress.update(WeeklyPlanJobService.Stage.FINALIZE, WeeklyPlanJobService.StepStatus.COMPLETED, "整理生成结果");
        return view;
    }

    /** 规划智能体是主力；它整体失败（而不是降级）时才退回旧的菜谱库排菜。 */
    private MenuPlannerAgent.PlanResult planWithAgent(String openid, List<Integer> cookingDays, int dishesPerDay,
                                                      String healthGoal, String budget, String notes) {
        try {
            return planner.plan(new MenuPlannerAgent.PlanRequest(
                    openid, cookingDays, dishesPerDay, healthGoal, budget, notes));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private List<PlanDay> fromPlanned(List<MenuPlannerAgent.PlannedDay> planned) {
        List<PlanDay> result = new ArrayList<>();
        for (int i = 0; i < planned.size(); i++) {
            MenuPlannerAgent.PlannedDay day = planned.get(i);
            if (day == null || day.dishes() == null || day.dishes().isEmpty()) continue;
            result.add(toDay(day, i));
        }
        return result;
    }

    private List<PlanDay> legacyDays(String openid, List<Integer> cookingDays, int dishesPerDay,
                                     String healthGoal, String budget, String notes) {
        List<Recipe> source = notes.isBlank()
                ? ("DAILY".equals(budget)
                    ? agent.planWeeklyMenu(openid, cookingDays.size(), dishesPerDay, healthGoal)
                    : agent.planWeeklyMenu(openid, cookingDays.size(), dishesPerDay, healthGoal, budget))
                : agent.planWeeklyMenu(openid, cookingDays.size(), dishesPerDay, healthGoal, budget, notes);
        if (source.isEmpty()) throw new IllegalStateException("没有找到符合你口味和忌口的菜谱，请先完善锅仔记忆");
        List<PlanDay> result = new ArrayList<>();
        for (int i = 0; i < cookingDays.size(); i++) {
            result.add(toDay(source, i, cookingDays.get(i), dishesPerDay, healthGoal));
        }
        return result;
    }

    /** 把本轮排的菜写进记忆，下一次排菜就知道该避开什么，形成闭环。 */
    private void rememberPlannedDishes(String openid, List<PlanDay> days) {
        List<String> names = days.stream().flatMap(day -> dishesFor(day).stream())
                .map(PlanDish::name).filter(name -> name != null && !name.isBlank())
                .distinct().limit(12).toList();
        if (names.isEmpty()) return;
        memory.remember(new AgentMemoryStore.RememberCommand(openid, AgentMemoryStore.KEY_RECENT_DISHES,
                String.join(",", names), 0.6, AgentMemoryStore.SRC_BEHAVIOR, "最近一次生成的周菜单", 21));
    }

    private PlanAudit audit(MenuPlannerAgent.PlanResult planned) {
        if (planned == null) {
            return new PlanAudit(null, List.of("规划智能体不可用，已退回菜谱库排菜"), List.of(), List.of(), null);
        }
        MenuQualityScorer.MenuQuality quality = planned.quality();
        return new PlanAudit(quality == null ? null : quality.score(),
                planned.degradeReasons() == null ? List.of() : planned.degradeReasons(),
                planned.memoryUsed() == null ? List.of() : planned.memoryUsed(),
                quality == null ? List.of() : quality.soft().stream()
                        .map(MenuQualityScorer.Issue::message).limit(5).toList(),
                planned.traceId());
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
        return ensureCover(openid, id, index, 0);
    }

    /** 一天多道菜时，每道菜各自保存封面，避免把配菜误用成主菜的图片。 */
    public Optional<PlanView> ensureCover(String openid, Long id, int index, int dishIndex) {
        return owned(openid, id).map(plan -> {
            WeeklyMealPlan saved = plan;
            List<PlanDay> days = readDays(plan.getPlanJson());
            if (index < 0 || index >= days.size()) throw new IllegalArgumentException("计划日期无效");
            PlanDay day = days.get(index);
            List<PlanDish> dishes = dishesFor(day);
            if (dishIndex < 0 || dishIndex >= dishes.size()) throw new IllegalArgumentException("菜品无效");
            PlanDish dish = dishes.get(dishIndex);
            if (dish.imageUrl() == null || dish.imageUrl().isBlank()) {
                Recipe coverRecipe = new Recipe();
                coverRecipe.setName(dish.name());
                coverRecipe.setDescription(day.healthTip());
                coverRecipe.setIngredients(write(dish.ingredients()));
                images.generateCover(coverRecipe).ifPresent(url -> {
                    List<PlanDish> updatedDishes = new ArrayList<>(dishes);
                    updatedDishes.set(dishIndex, dish.withImage(url));
                    PlanDay updatedDay = day.withDishes(updatedDishes);
                    days.set(index, dishIndex == 0 ? updatedDay.withImage(url) : updatedDay);
                });
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
            int weekday = weekdayIndex(days.get(index).day());
            int dishesPerDay = Math.max(1, dishesFor(days.get(index)).size());
            MenuPlannerAgent.PlanResult planned = planWithAgent(openid, List.of(weekday), dishesPerDay,
                    "BALANCED", "DAILY", "");
            if (planned != null && planned.days() != null && !planned.days().isEmpty()) {
                days.set(index, toDay(planned.days().get(0), index));
            } else {
                List<Recipe> source = agent.planWeeklyMenu(openid, 1, dishesPerDay, "BALANCED");
                if (source.isEmpty()) throw new IllegalStateException("没有找到符合你口味和忌口的替换菜谱");
                days.set(index, toDay(source, 0, weekday, dishesPerDay, "BALANCED"));
            }
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

    /** 规划智能体产出的一天：直接带上它给出的复用建议与健康提示，不再二次改写。 */
    private PlanDay toDay(MenuPlannerAgent.PlannedDay planned, int index) {
        List<PlanDish> dishes = planned.dishes().stream()
                .filter(dish -> dish != null && dish.name() != null && !dish.name().isBlank())
                .map(dish -> new PlanDish(dish.name(),
                        dish.ingredients() == null || dish.ingredients().isEmpty()
                                ? List.of(dish.name()) : CookingTextNormalizer.normalizeIngredients(dish.ingredients()),
                        CookingTextNormalizer.normalizeSteps(dish.steps()),
                        dish.fallbackImageUrl(), null))
                .toList();
        if (dishes.isEmpty()) throw new IllegalStateException("规划智能体没有给出可用菜品");
        PlanDish mainDish = dishes.get(0);
        String dishName = dishes.stream().map(PlanDish::name).reduce((first, second) -> first + " · " + second).orElse("今晚的菜单");
        return new PlanDay(dayName(planned.weekdayIndex()), dishName, mainDish.ingredients(), mainDish.steps(),
                planned.reuseHint() == null || planned.reuseHint().isBlank()
                        ? (index == 0 ? "这周会优先复用常见蔬菜和调料。" : "和前几天共用部分调料，少买一点也够用。")
                        : planned.reuseHint(),
                planned.healthTip() == null || planned.healthTip().isBlank()
                        ? "搭配一份主食和蔬菜，吃得更完整。" : planned.healthTip(),
                null, mainDish.fallbackImageUrl(), dishes);
    }

    private PlanDay toDay(List<Recipe> source, int index, int weekdayIndex, int dishesPerDay, String healthGoal) {
        List<PlanDish> dishes = new ArrayList<>();
        for (int offset = 0; offset < dishesPerDay; offset++) {
            Recipe recipe = source.get((index * dishesPerDay + offset) % source.size());
            dishes.add(toDish(recipe));
        }
        PlanDish mainDish = dishes.get(0);
        String dishName = dishes.stream().map(PlanDish::name).reduce((first, second) -> first + " · " + second).orElse("今晚的菜单");
        return new PlanDay(dayName(weekdayIndex), dishName, mainDish.ingredients(), mainDish.steps(),
                index == 0 ? "这周会优先复用常见蔬菜和调料。" : "和前几天共用部分调料，少买一点也够用。",
                "FITNESS".equals(healthGoal) ? "搭配优质蛋白、主食和蔬菜。" : "LEAN".equals(healthGoal) ? "搭配蔬菜、优质蛋白和少油做法。" : "搭配一份主食和蔬菜，吃得更完整。",
                null, mainDish.fallbackImageUrl(), dishes);
    }

    private PlanDish toDish(Recipe recipe) {
        List<String> ingredients = CookingTextNormalizer.normalizeIngredients(ingredients(recipe.getIngredients()));
        List<String> steps = CookingTextNormalizer.normalizeSteps(steps(recipe.getSteps()));
        // 步骤缺失时保持为空，由前端提示"暂无做法"，绝不套用通用模板冒充真实步骤。
        return new PlanDish(recipe.getName(), ingredients, steps.stream().limit(5).toList(), recipe.getImage(), null);
    }

    private List<ShoppingItem> merge(List<PlanDay> days, List<ShoppingItem> old) {
        List<PlanDish> dishes = new ArrayList<>();
        days.forEach(day -> dishes.addAll(dishesFor(day)));
        return aggregateShopping(dishes, old);
    }

    /** 一条食材串解析出的名称与用量："生抽 10 毫升" → 生抽/10/毫升；"盐 适量" → 盐/0/""。 */
    record IngredientAmount(String name, double amount, String unit) {}

    static final java.util.regex.Pattern AMOUNT = java.util.regex.Pattern.compile(
            "([0-9]+(?:\\.[0-9]+)?)\\s*(千克|公斤|毫升|大勺|小勺|茶匙|勺|克|kg|KG|Kg|g|G|ml|ML|Ml|升|个|只|根|片|块|瓣|颗|粒|条|段|朵|包|袋|盒|瓶|杯|罐|把|枚|张|份)?");

    private static String halfWidth(String text) {
        return java.util.regex.Pattern.compile("[０-９]").matcher(text)
                .replaceAll(match -> String.valueOf((char) ('0' + (match.group().charAt(0) - '０'))));
    }

    /** 中文数量：\"半根/一小把/两勺\" —— HowToCook 菜谱大量使用；\"一\"与单位之间的\"大/小\"是修饰不是食材。 */
    static final java.util.regex.Pattern CN_AMOUNT = java.util.regex.Pattern.compile(
            "([一两二三四五六七八九十半])\\s*[大小]?(千克|公斤|毫升|大勺|小勺|茶匙|勺|克|升|个|只|根|片|块|瓣|颗|粒|条|段|朵|包|袋|盒|瓶|杯|罐|把|撮|枚|张|份)?");

    static IngredientAmount parseIngredient(String raw) {
        String text = halfWidth(raw == null ? "" : raw.replaceAll("[，,、].*", "").trim());
        java.util.regex.Matcher matcher = AMOUNT.matcher(text);
        boolean arabic = matcher.find();
        double amount = 0;
        String unit = "";
        if (arabic) {
            try { amount = Double.parseDouble(matcher.group(1)); } catch (NumberFormatException ignored) { }
            unit = matcher.group(2) == null ? "" : matcher.group(2);
        } else {
            java.util.regex.Matcher cn = CN_AMOUNT.matcher(text);
            if (cn.find()) {
                amount = cnAmount(cn.group(1));
                unit = cn.group(2) == null ? "" : cn.group(2);
            }
        }
        String name = (arabic ? AMOUNT : CN_AMOUNT).matcher(text).replaceAll("")
                .replaceAll("适量|少许|若干|一些|半个?", "")
                .replaceAll("^[大小]?[块勺把撮段片条根瓣颗粒枚张个]", "")
                .replaceAll("[（）()\\[\\]【】\\s]", "").trim();
        return new IngredientAmount(name.isBlank() ? text.trim() : name, amount, normalizeUnit(unit));
    }

    private static double cnAmount(String text) {
        return switch (text) {
            case "半" -> 0.5;
            case "一" -> 1;
            case "二", "两" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            default -> 10;
        };
    }

    /** g/ml/kg 等拉丁单位归一到中文，保证跨菜谱可求和（10ml + 5 毫升 = 15 毫升）。 */
    static String normalizeUnit(String unit) {
        return switch (unit == null ? "" : unit) {
            case "g", "G" -> "克";
            case "ml", "ML", "Ml" -> "毫升";
            case "kg", "KG", "Kg" -> "千克";
            default -> unit == null ? "" : unit;
        };
    }

    /** 采购清单聚合：同名食材若都带同单位用量则求和成真实数量（"生抽 15毫升"），否则回退"出现份数"。 */
    static List<ShoppingItem> aggregateShopping(List<PlanDish> dishes, List<ShoppingItem> old) {
        Map<String, Boolean> bought = new HashMap<>();
        old.forEach(item -> bought.put(item.name(), item.purchased));
        Map<String, List<IngredientAmount>> grouped = new LinkedHashMap<>();
        dishes.forEach(dish -> dish.ingredients().forEach(raw -> {
            if (raw == null || raw.isBlank()) return;
            IngredientAmount parsed = parseIngredient(raw);
            if (!parsed.name().isBlank()) {
                grouped.computeIfAbsent(parsed.name(), key -> new ArrayList<>()).add(parsed);
            }
        }));
        // "猪里脊肉"与"猪里脊"是同一食材的两种写法，合并到长名，避免采购清单出现两条
        for (String key : new ArrayList<>(grouped.keySet())) {
            if (key.endsWith("肉")) {
                List<IngredientAmount> shortForm = grouped.remove(key.substring(0, key.length() - 1));
                if (shortForm != null) grouped.get(key).addAll(shortForm);
            }
        }
        return grouped.entrySet().stream().map(entry -> {
            List<IngredientAmount> items = entry.getValue();
            String unit = null;
            double total = 0;
            boolean allQuantified = !items.isEmpty();
            for (IngredientAmount item : items) {
                if (item.amount() <= 0) { allQuantified = false; break; }
                if (unit == null) unit = item.unit();
                else if (!unit.equals(item.unit())) { allQuantified = false; break; }
                total += item.amount();
            }
            String quantity = allQuantified && unit != null && !unit.isBlank()
                    ? formatAmount(total) + unit
                    : items.size() + " 份";
            return new ShoppingItem(entry.getKey(), category(entry.getKey()), quantity,
                    bought.getOrDefault(entry.getKey(), false));
        }).toList();
    }

    private static String formatAmount(double value) {
        double rounded = Math.round(value * 10) / 10.0;
        return rounded == Math.floor(rounded) ? String.valueOf((long) rounded) : String.valueOf(rounded);
    }

    private static String category(String name) {
        if (name.matches(".*(盐|糖|油|酱|醋|料酒|生抽|老抽|蚝油|味精|鸡精|胡椒粉|小苏打|淀粉|花椒|八角|桂皮|香叶|咖喱).*")) return "调料";
        if (name.matches(".*(鸡|肉|鱼|虾|蛋|奶|芝士|豆腐|豆干|腐竹|豆浆).*$")) return "肉蛋豆";
        if (name.matches(".*(米|面|粉|馒头|包子|饺子|面包|土豆|红薯|山药|玉米).*$")) return "主食";
        return "蔬菜";
    }
    private List<Integer> cookingDays(GenerateRequest request) {
        String notes = request.conversationNotes() == null ? "" : request.conversationNotes();
        boolean oneOffMeal = List.of("客人", "宾客", "宴请", "聚餐", "家宴", "请客", "招待", "酒席")
                .stream().anyMatch(notes::contains);
        if (oneOffMeal && notes.contains("今天")) {
            return List.of(AppClock.today().getDayOfWeek().getValue() - 1);
        }
        if (request.cookingDays() != null && !request.cookingDays().isEmpty()) {
            List<Integer> selected = request.cookingDays().stream().filter(day -> day >= 0 && day < 7).distinct().sorted().toList();
            if (oneOffMeal && !selected.isEmpty()) return List.of(selected.get(0));
            if (!selected.isEmpty()) return selected;
        }
        return java.util.stream.IntStream.range(0, Math.max(1, Math.min(request.days(), 7))).boxed().toList();
    }
    private int weekdayIndex(String day) { return "一二三四五六日".indexOf(day == null || day.length() < 2 ? "一" : day.substring(1)); }
    private String dayName(int index) { return "周" + "一二三四五六日".charAt(Math.max(0, Math.min(index, 6))); }
    private List<PlanDish> dishesFor(PlanDay day) {
        if (day.dishes() != null && !day.dishes().isEmpty()) return day.dishes();
        return List.of(new PlanDish(day.dishName(), day.ingredients(), day.steps(), day.fallbackImageUrl(), day.imageUrl()));
    }
    private List<String> ingredients(String value) { return readStrings(value); }
    private List<String> steps(String value) { return readStrings(value); }
    private List<String> readStrings(String value) { try { return json.readValue(value, new TypeReference<List<String>>() {}); } catch (Exception e) { return value == null || value.isBlank() ? List.of() : List.of(value); } }
    private List<PlanDay> readDays(String value) { try { return json.readValue(value, new TypeReference<List<PlanDay>>() {}); } catch (Exception e) { return List.of(); } }
    private boolean displayablePlan(List<PlanDay> days) {
        if (days == null || days.isEmpty()) return false;
        for (PlanDay day : days) {
            if (day == null || !MenuPlannerAgent.displayableText(day.day(), 12)
                    || !MenuPlannerAgent.displayableText(day.dishName(), 240)
                    || !MenuPlannerAgent.displayableText(day.reuseHint(), 240)
                    || !MenuPlannerAgent.displayableText(day.healthTip(), 240)) return false;
            List<PlanDish> dishes = dishesFor(day);
            if (dishes.isEmpty()) return false;
            for (PlanDish dish : dishes) {
                if (dish == null || !MenuPlannerAgent.displayableText(dish.name(), 40)
                        || !displayableTexts(dish.ingredients(), 80) || !displayableTexts(dish.steps(), 240)
                        || !RecipeSemanticValidator.isValid(dish.ingredients(), dish.steps())) return false;
            }
        }
        return true;
    }
    private boolean displayableTexts(List<String> values, int maxLength) {
        return values == null || values.stream().allMatch(value -> MenuPlannerAgent.displayableText(value, maxLength));
    }
    private List<ShoppingItem> readShopping(String value) {
        try {
            List<ShoppingItem> items = json.readValue(value, new TypeReference<List<ShoppingItem>>() {});
            if (items == null) return List.of();
            return items.stream()
                    .filter(item -> item != null
                            && MenuPlannerAgent.displayableText(item.name, 80)
                            && MenuPlannerAgent.displayableText(item.category, 20)
                            && MenuPlannerAgent.displayableText(item.quantity, 20))
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException("计划保存失败"); } }
    private PlanView view(WeeklyMealPlan plan) { return new PlanView(plan.getId(), readDays(plan.getPlanJson()), readShopping(plan.getShoppingJson()), plan.isFavorite(), plan.getCreatedAt(), readAudit(plan.getAgentJson())); }
    private PlanSummary summary(WeeklyMealPlan plan) { return new PlanSummary(plan.getId(), plan.getCreatedAt(), plan.isFavorite(), readDays(plan.getPlanJson())); }
    private PlanAudit readAudit(String value) { try { return value == null || value.isBlank() ? null : json.readValue(value, PlanAudit.class); } catch (Exception e) { return null; } }

    public record GenerateRequest(int people, int days, List<Integer> cookingDays, String healthGoal,
                                  boolean sendNotification, int dishesPerDay, String budget, String conversationNotes,
                                  String requestId) {
        public GenerateRequest(int people, int days, List<Integer> cookingDays, String healthGoal,
                               boolean sendNotification, int dishesPerDay) {
            this(people, days, cookingDays, healthGoal, sendNotification, dishesPerDay, "DAILY", "", null);
        }
        public GenerateRequest(int people, int days, List<Integer> cookingDays, String healthGoal,
                               boolean sendNotification, int dishesPerDay, String budget) {
            this(people, days, cookingDays, healthGoal, sendNotification, dishesPerDay, budget, "", null);
        }
        public GenerateRequest(int people, int days, List<Integer> cookingDays, String healthGoal,
                               boolean sendNotification, int dishesPerDay, String budget, String conversationNotes) {
            this(people, days, cookingDays, healthGoal, sendNotification, dishesPerDay, budget, conversationNotes, null);
        }
    }
    public record PlanView(Long id, List<PlanDay> days, List<ShoppingItem> shopping, boolean favorite,
                           LocalDateTime createdAt, PlanAudit agent) {}
    /** 这一版菜单是怎么来的：质量分、哪里降级了、用了哪些记忆、还剩哪些软性问题。 */
    public record PlanAudit(Integer score, List<String> degradeReasons, List<String> memoryUsed,
                            List<String> issues, String traceId) {}
    public record PlanSummary(Long id, LocalDateTime createdAt, boolean favorite, List<PlanDay> days) {}
    public record PlanDay(String day, String dishName, List<String> ingredients, List<String> steps, String reuseHint, String healthTip, String imageUrl, String fallbackImageUrl, List<PlanDish> dishes) {
        PlanDay withImage(String imageUrl) { return new PlanDay(day, dishName, ingredients, steps, reuseHint, healthTip, imageUrl, fallbackImageUrl, dishes); }
        PlanDay withDishes(List<PlanDish> dishes) { return new PlanDay(day, dishName, ingredients, steps, reuseHint, healthTip, imageUrl, fallbackImageUrl, dishes); }
    }
    public record PlanDish(String name, List<String> ingredients, List<String> steps, String fallbackImageUrl, String imageUrl) {
        PlanDish withImage(String imageUrl) { return new PlanDish(name, ingredients, steps, fallbackImageUrl, imageUrl); }
    }
    public static class ShoppingItem { public String name; public String category; public String quantity; public boolean purchased; public ShoppingItem() {} ShoppingItem(String n, String c, String q, boolean p) { name = n; category = c; quantity = q; purchased = p; } public String name() { return name; } }
}
