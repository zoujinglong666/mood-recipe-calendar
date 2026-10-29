package com.moodrecipe.backend.service.nutrition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.agent.AgentOutputGate;
import com.moodrecipe.backend.agent.LlmClient;
import com.moodrecipe.backend.agent.LlmResult;
import com.moodrecipe.backend.agent.LlmRequest;
import com.moodrecipe.backend.agent.TimeoutTier;
import com.moodrecipe.backend.controller.UserFoodPreferenceController;
import com.moodrecipe.backend.entity.UserFoodPreference;
import com.moodrecipe.backend.repository.UserFoodPreferenceRepository;
import com.moodrecipe.backend.service.DailyMealPlanService;
import com.moodrecipe.backend.service.DailyMealPlanService.PlanView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * [2026-10-28] AI 营养师：模拟真实营养专家，基于身体数据、健康目标、饮食偏好与过敏限制，
 * 对当日三餐给出每餐营养方案（份量建议 + 四宏估算 + 搭配理由）与整日目标对照分析。
 *
 * 数据全部来自已有真源：目标算自 NutritionTargetService（NASEM 2023 EER），三餐来自
 * DailyMealPlanService（安全/忌口/营养三重校验），指南依据来自知识包 RAG。
 * LLM 只做「解读与建议」这一层，不做决策——它改不了菜单，也改不了目标。
 *
 * 红线（与全站一致）：观察不诊断。禁医疗词汇、禁补充剂推荐、禁体重承诺；
 * LLM 输出过 AgentOutputGate + 违禁词硬校验，任一不过即降级规则分析，绝不带病输出。
 */
@Service
public class NutritionCoachService {

    private static final Logger log = LoggerFactory.getLogger(NutritionCoachService.class);

    /** 医疗/诊断类违禁词：命中任一即整包降级规则分析（宁可平淡，不可越界）。 */
    private static final List<String> BANNED_TERMS = List.of(
            "诊断", "治疗", "治愈", "处方", "药物", "用药", "病情", "患者", "症状",
            "糖尿病", "高血压", "高血糖", "降血糖", "降血压", "血脂", "胰岛素", "卡路里赤字", "临床");

    private final UserFoodPreferenceRepository preferences;
    private final DailyMealPlanService mealPlans;
    private final NutritionTargetService nutritionTargets;
    private final LlmClient llm;
    private final ObjectMapper json;

    /** 输出门控可选：为空时只做违禁词硬校验。 */
    private AgentOutputGate outputGate;

    @Autowired(required = false)
    void setOutputGate(AgentOutputGate outputGate) {
        this.outputGate = outputGate;
    }

    public NutritionCoachService(UserFoodPreferenceRepository preferences, DailyMealPlanService mealPlans,
                                 NutritionTargetService nutritionTargets, LlmClient llm, ObjectMapper json) {
        this.preferences = preferences;
        this.mealPlans = mealPlans;
        this.nutritionTargets = nutritionTargets;
        this.llm = llm;
        this.json = json;
    }

    // ==== 输出结构 ====

    /** 每餐方案。kcal/宏量为估算值（estimated=true），前端展示需带「约」字。 */
    public record MealAnalysis(int index, String name, String portion,
                               Integer kcal, Integer proteinG, Integer carbsG, Integer fatG,
                               String reason, String goalTip, boolean estimated) { }

    /** 整日对照：估算总热量 vs EER 目标区间。 */
    public record DailyAnalysis(Integer totalKcal, Integer targetKcal, String balance, String gapTip) { }

    /**
     * status：OK（有目标有三餐）/ NEEDS_INPUT（身体数据不全，target.missingFields 说明缺什么）
     * / NO_PLAN（今日三餐生成失败）。source：LLM / RULE。
     */
    public record CoachView(String status, String message, NutritionTargetService.Target target, String source,
                            List<MealAnalysis> meals, DailyAnalysis daily) { }

    /**
     * 当日营养师分析：目标 → 三餐 → 逐餐方案 → 整日对照。
     * LLM 失败/越界一律降级规则分析，接口永远有可用输出。
     */
    public CoachView coach(String openid) {
        UserFoodPreference pref = preferences.findByOpenid(openid).orElseGet(UserFoodPreference::new);
        NutritionTargetService.Target target = nutritionTargets.compute(toInputs(pref));
        if (!"OK".equals(target.status())) {
            return new CoachView("NEEDS_INPUT",
                    "把身高体重这些补充完整，营养师就能算出你每天需要的能量和蛋白质啦。",
                    target, "NONE", List.of(), null);
        }
        PlanView plan = mealPlans.plan(openid, java.time.LocalDate.now()).orElse(null);
        if (plan == null || plan.meals().size() != 3) {
            return new CoachView("NO_PLAN", "今日三餐还没排好，先生成一份再来找营养师。",
                    target, "NONE", List.of(), null);
        }
        if (llm.isConfigured()) {
            try {
                CoachView view = llmAnalysis(pref, target, plan);
                if (view != null) return view;
            } catch (Exception exception) {
                log.warn("营养师 LLM 分析失败，降级规则分析: {}", exception.getClass().getSimpleName());
            }
        }
        return ruleBased(target, plan);
    }

    // ==== LLM 路径 ====

    private CoachView llmAnalysis(UserFoodPreference pref, NutritionTargetService.Target target, PlanView plan)
            throws com.fasterxml.jackson.core.JsonProcessingException {
        String user = buildUserPrompt(target, plan);
        String system = J("""
                你是一位经验丰富的注册营养师（RDN），在"锅仔"小程序里为用户解读当日三餐。
                你只基于系统给出的数据说话：菜品、食材、膳食指南要点、能量与蛋白质目标。
                每条输出必须中文、口语化、一句话以内，语气温暖不啰嗦。
                硬性红线：不做诊断、不提疾病与症状、不推荐保健品或补充剂、不承诺减重数字、
                不使用"治疗/血糖/血压/临床"等医疗词汇。你是在陪用户好好吃饭，不是在看病。
                份量用家常量词（一碗/一掌心/一拳头/两筷子），热量与宏量为估算值，取整数。
                只输出 JSON，不要任何多余文字。结构：
                {"meals":[{"index":0,"portion":"...","kcal":0,"proteinG":0,"carbsG":0,"fatG":0,"reason":"...","goalTip":"..."}],
                 "daily":{"totalKcal":0,"balance":"...","gapTip":"..."}}
                meals 必须恰好 3 项，index 依次 0/1/2，name 不需要你输出（系统已有）。""");
        LlmResult result = llm.complete(
                LlmRequest.json("nutrition-coach-daily", system, user, 0.4, 1400, TimeoutTier.STANDARD));
        if (!result.ok()) return null;
        String text = result.text();
        if (text == null || text.isBlank()) return null;
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        JsonNode root = json.readTree(text.substring(start, end + 1));
        JsonNode mealsNode = root.path("meals");
        if (!mealsNode.isArray() || mealsNode.size() != 3) return null;

        List<MealAnalysis> meals = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            JsonNode item = mealsNode.get(i);
            if (item == null || !item.isObject()) return null;
            String portion = clean(item.path("portion").asText(""));
            String reason = clean(item.path("reason").asText(""));
            String goalTip = clean(item.path("goalTip").asText(""));
            if (portion.isEmpty() || reason.isEmpty()) return null;
            meals.add(new MealAnalysis(i,
                    plan.meals().get(i).getName(),
                    portion,
                    clamp(item.path("kcal").asInt(0), 80, 1600),
                    clamp(item.path("proteinG").asInt(0), 0, 120),
                    clamp(item.path("carbsG").asInt(0), 0, 250),
                    clamp(item.path("fatG").asInt(0), 0, 120),
                    reason, goalTip, true));
        }
        JsonNode dailyNode = root.path("daily");
        String balance = clean(dailyNode.path("balance").asText(""));
        String gapTip = clean(dailyNode.path("gapTip").asText(""));
        if (balance.isEmpty() || gapTip.isEmpty()) return null;
        DailyAnalysis daily = new DailyAnalysis(
                clamp(dailyNode.path("totalKcal").asInt(0), 300, 5000),
                target.energyKcal() == null ? null : target.energyKcal().target().intValue(),
                balance, gapTip);
        return new CoachView("OK", "营养师已就今日三餐给出分析", target, "LLM", meals, daily);
    }

    /** 交给营养师的数据包：目标 + 三餐 + 指南要点 + 偏好，全部来自真源，LLM 不参与构造。 */
    private String buildUserPrompt(NutritionTargetService.Target target, PlanView plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("【能量与蛋白质目标】");
        if (target.maintenanceKcal() != null) sb.append("维持热量约 ").append(target.maintenanceKcal()).append(" 千卡；");
        if (target.energyKcal() != null) sb.append("今日目标 ").append(target.energyKcal().target().intValue())
                .append(" 千卡（区间 ").append(target.energyKcal().min().intValue()).append("-").append(target.energyKcal().max().intValue()).append("）；");
        if (target.proteinG() != null) sb.append("蛋白质 ").append(target.proteinG().min())
                .append("-").append(target.proteinG().max().intValue()).append(" 克（目标 ").append(target.proteinG().target().intValue()).append(" 克）；");
        if (target.bmi() != null) sb.append("BMI ").append(target.bmi());
        sb.append("\n【今日三餐】");
        String[] labels = {"早餐", "午餐", "晚餐"};
        for (int i = 0; i < 3; i++) {
            var recipe = plan.meals().get(i);
            sb.append("\n").append(labels[i]).append("：").append(recipe.getName())
                    .append("（食材：").append(recipe.getIngredients() == null ? "未标注" : recipe.getIngredients()).append("）");
            var basis = plan.guidelineBasis().get(i);
            if (basis != null && !basis.content().isBlank()) {
                sb.append("。指南依据：").append(basis.content());
            }
        }
        sb.append("\n【整日结构自评】").append(plan.nutrition().headline()).append(plan.nutrition().tip());
        return sb.toString();
    }

    // ==== 规则降级路径（无 LLM / LLM 越界时，接口依然有可用的营养解读） ====

    private CoachView ruleBased(NutritionTargetService.Target target, PlanView plan) {
        String[] labels = {"早餐", "午餐", "晚餐"};
        int[] baseKcal = {400, 650, 550};
        List<MealAnalysis> meals = new ArrayList<>();
        int total = 0;
        for (int i = 0; i < 3; i++) {
            var recipe = plan.meals().get(i);
            int kcal = baseKcal[i];
            total += kcal;
            var basis = plan.guidelineBasis().get(i);
            String reason = (basis != null && !basis.content().isBlank())
                    ? basis.content()
                    : "按《中国居民膳食指南》的" + labels[i] + "搭配原则来安排。";
            meals.add(new MealAnalysis(i, recipe.getName(), defaultPortion(i), kcal, null, null, null,
                    reason, goalTipFor(target), true));
        }
        DailyAnalysis daily = new DailyAnalysis(total,
                target.energyKcal() == null ? null : target.energyKcal().target().intValue(),
                "按三餐结构估算，供参考。",
                plan.nutrition().tip());
        return new CoachView("OK", "营养师已就今日三餐给出分析", target, "RULE", meals, daily);
    }

    private String defaultPortion(int mealIndex) {
        return switch (mealIndex) {
            case 0 -> "一份主食 + 一杯奶或一个蛋";
            case 1 -> "一碗主食 + 一掌心肉蛋 + 两拳蔬菜";
            default -> "半碗主食 + 一掌心蛋白 + 清淡蔬菜";
        };
    }

    private String goalTipFor(NutritionTargetService.Target target) {
        String offset = target.energyOffsetPercent() == null ? "" : target.energyOffsetPercent();
        if (offset.contains("-")) return "这一餐按减脂节奏走，主食别超额。";
        if (offset.contains("+")) return "记得把蛋白质那份吃完，练后恢复靠它。";
        return "好好吃完整顿，均衡比什么都重要。";
    }

    // ==== 公共 ====

    /** 与 UserFoodPreferenceController /nutrition-target 相同的画像映射（保持口径一致）。 */
    private NutritionTargetService.Inputs toInputs(UserFoodPreference p) {
        NutritionTargetService.Goal goal = switch (p.getHealthGoal() == null ? "BALANCED" : p.getHealthGoal()) {
            case "LEAN" -> NutritionTargetService.Goal.FAT_LOSS;
            case "FITNESS" -> NutritionTargetService.Goal.MUSCLE_GAIN;
            default -> NutritionTargetService.Goal.WELLBEING;
        };
        return new NutritionTargetService.Inputs(
                "MALE".equals(p.getGender()) ? NutritionTargetService.Sex.MALE
                        : "FEMALE".equals(p.getGender()) ? NutritionTargetService.Sex.FEMALE
                        : NutritionTargetService.Sex.UNKNOWN,
                p.getAge(), p.getHeightCm(), p.getWeightKg(),
                p.getActivityLevel() == null ? null : NutritionTargetService.ActivityLevel.valueOf(p.getActivityLevel()),
                p.getResistanceTraining(), goal);
    }

    /** 违禁词硬校验 + 输出门控微调；命中违禁词返回 null（调用方降级），绝不带病输出。 */
    private String clean(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) return "";
        String lower = value.toLowerCase(Locale.ROOT);
        for (String banned : BANNED_TERMS) {
            if (lower.contains(banned)) return null;
        }
        if (outputGate != null) {
            value = outputGate.fix(value).text();
        }
        return value;
    }

    private static Integer clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String J(String text) {
        return text;
    }
}
