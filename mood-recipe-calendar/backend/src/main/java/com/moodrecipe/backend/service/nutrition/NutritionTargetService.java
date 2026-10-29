package com.moodrecipe.backend.service.nutrition;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * [2026-10-28] NASEM 2023 成人 EER 能量目标计算（移植自 aiiqc/nutrition-skill nutrition_core/targets.py）。
 *
 * 方程：EER = a − b×年龄 + c×身高(cm) + d×体重(kg)，系数按「性别 × 活动等级」取独立组
 * （不是 BMR×PAL）。适用 19-64 岁一般成人；孕产、疾病、儿童青少年不在支持范围（返回 not_eligible）。
 *
 * 与 Python 源对齐的三个关键点：
 * 1. 全程 BigDecimal + HALF_UP：能量取整到 10 kcal、蛋白质取整到 1 g、BMI 保留 2 位；
 * 2. 蛋白质范围由「是否规律力量训练」选择 1.4/1.6/2.0 或 1.2/1.4/1.6 g/kg（按实测体重）；
 * 3. 资格门控照搬 _product_limits（年龄/身高体重/BMI/目标守卫/能量上下限/蛋白供能占比），
 *    任一命中即 not_eligible——观察类产品红线：宁可不算，不给可疑数值。
 */
@Service
public class NutritionTargetService {

    /** 活动等级：久坐 / 低活动 / 中活动 / 高活动（自报，非 PAL 乘数）。 */
    public enum ActivityLevel { INACTIVE, LOW_ACTIVE, ACTIVE, VERY_ACTIVE }

    /** 生理性别（仅决定方程系数组；UNKNOWN 时返回 NEEDS_INPUT）。 */
    public enum Sex { MALE, FEMALE, UNKNOWN }

    /** 目标：与 healthGoal 对齐（BALANCED→WELLBEING 0% / LEAN→FAT_LOSS −10% / FITNESS→MUSCLE_GAIN +5%）。 */
    public enum Goal { WELLBEING, FAT_LOSS, MUSCLE_GAIN }

    /** 目标输入：全部来自用户自报（冷启动问卷/偏好表）。 */
    public record Inputs(Sex sex, Integer age, BigDecimal heightCm, BigDecimal weightKg,
                         ActivityLevel activity, Boolean resistanceTraining, Goal goal) { }

    /** min/target/max 三元组（单位：能量 kcal，蛋白质 g）。 */
    public record Range(BigDecimal min, BigDecimal target, BigDecimal max) { }

    /**
     * 计算结果。status：
     * OK=有数值；NEEDS_INPUT=缺性别/年龄/身高/体重/活动任一项（missingFields 列出）；
     * NOT_ELIGIBLE=命中资格门控（issues 列出机器码，文案由前端映射）。
     */
    public record Target(String status, List<String> missingFields, List<String> issues,
                         BigDecimal bmi, BigDecimal maintenanceKcal,
                         Range energyKcal, Range proteinG, String energyOffsetPercent) {
        public static final String OK = "OK";
        public static final String NEEDS_INPUT = "NEEDS_INPUT";
        public static final String NOT_ELIGIBLE = "NOT_ELIGIBLE";
    }

    /** NASEM 2023 成人 EER 系数：(a, b, c, d)，已与 Health Canada 官方成人表核对（源仓库 docs/nutrition-rules.md）。 */
    private static final Map<Sex, Map<ActivityLevel, BigDecimal[]>> EER = Map.of(
            Sex.MALE, Map.of(
                    ActivityLevel.INACTIVE,     coeffs("753.07", "10.83", "6.50", "14.10"),
                    ActivityLevel.LOW_ACTIVE,   coeffs("581.47", "10.83", "8.30", "14.94"),
                    ActivityLevel.ACTIVE,       coeffs("1004.82", "10.83", "6.52", "15.91"),
                    ActivityLevel.VERY_ACTIVE,  coeffs("-517.88", "10.83", "15.61", "19.11")),
            Sex.FEMALE, Map.of(
                    ActivityLevel.INACTIVE,     coeffs("584.90", "7.01", "5.72", "11.71"),
                    ActivityLevel.LOW_ACTIVE,   coeffs("575.77", "7.01", "6.60", "12.14"),
                    ActivityLevel.ACTIVE,       coeffs("710.25", "7.01", "6.54", "12.34"),
                    ActivityLevel.VERY_ACTIVE,  coeffs("511.83", "7.01", "9.07", "12.56")));

    private static BigDecimal[] coeffs(String a, String b, String c, String d) {
        return new BigDecimal[]{new BigDecimal(a), new BigDecimal(b), new BigDecimal(c), new BigDecimal(d)};
    }

    /** 资格范围常量（与源 _product_limits 一致）。 */
    private static final int AGE_MIN = 19, AGE_MAX = 64;
    private static final BigDecimal HEIGHT_MIN = new BigDecimal("120"), HEIGHT_MAX = new BigDecimal("220");
    private static final BigDecimal WEIGHT_MIN = new BigDecimal("35"), WEIGHT_MAX = new BigDecimal("200");
    private static final BigDecimal BMI_MIN = new BigDecimal("18.5"), BMI_MAX = new BigDecimal("40");
    private static final BigDecimal FAT_LOSS_BMI_MIN = new BigDecimal("20");
    private static final BigDecimal MAINTENANCE_MIN = new BigDecimal("1400"), MAINTENANCE_MAX = new BigDecimal("5000");
    private static final BigDecimal ENERGY_FLOOR = new BigDecimal("1200");
    private static final BigDecimal PROTEIN_ENERGY_CAP = new BigDecimal("0.35");
    /** 能量目标偏移（%）：菜单编排容差，不是生理不确定度。 */
    private static final Map<Goal, Integer> GOAL_OFFSET = Map.of(
            Goal.WELLBEING, 0, Goal.FAT_LOSS, -10, Goal.MUSCLE_GAIN, 5);

    public Target compute(Inputs in) {
        List<String> missing = new ArrayList<>();
        if (in == null) return new Target(Target.NEEDS_INPUT, List.of("all"), List.of(), null, null, null, null, null);
        if (in.sex() == null || in.sex() == Sex.UNKNOWN) missing.add("sex");
        if (in.age() == null) missing.add("age");
        if (in.heightCm() == null) missing.add("heightCm");
        if (in.weightKg() == null) missing.add("weightKg");
        if (in.activity() == null) missing.add("activity");
        Goal goal = in.goal() == null ? Goal.WELLBEING : in.goal();
        if (!missing.isEmpty()) {
            return new Target(Target.NEEDS_INPUT, List.copyOf(missing), List.of(), null, null, null, null, null);
        }

        List<String> issues = new ArrayList<>();
        int age = in.age();
        BigDecimal height = in.heightCm(), weight = in.weightKg();
        boolean resistance = Boolean.TRUE.equals(in.resistanceTraining());

        if (age < AGE_MIN || age > AGE_MAX) issues.add("outside_supported_age");

        // EER = a − b×age + c×height + d×weight（BigDecimal 精确算术）
        BigDecimal[] k = EER.get(in.sex()).get(in.activity());
        BigDecimal maintenance = k[0]
                .subtract(k[1].multiply(BigDecimal.valueOf(age)))
                .add(k[2].multiply(height))
                .add(k[3].multiply(weight));
        BigDecimal bmi = weight.divide(height.divide(BigDecimal.valueOf(100), MathContext.DECIMAL64).pow(2),
                2, RoundingMode.HALF_UP);

        if (height.compareTo(HEIGHT_MIN) < 0 || height.compareTo(HEIGHT_MAX) > 0
                || weight.compareTo(WEIGHT_MIN) < 0 || weight.compareTo(WEIGHT_MAX) > 0) {
            issues.add("measurements_outside_product_range");
        }
        if (bmi.compareTo(BMI_MIN) < 0 || bmi.compareTo(BMI_MAX) >= 0) issues.add("outside_supported_bmi");
        if (goal == Goal.FAT_LOSS && bmi.compareTo(FAT_LOSS_BMI_MIN) < 0) issues.add("fat_loss_near_lower_bmi");
        if (goal == Goal.MUSCLE_GAIN && !resistance) issues.add("resistance_training_required");
        if (maintenance.compareTo(MAINTENANCE_MIN) < 0 || maintenance.compareTo(MAINTENANCE_MAX) > 0) {
            issues.add("energy_estimate_outside_product_range");
        }

        BigDecimal offsetPercent = BigDecimal.valueOf(GOAL_OFFSET.get(goal));
        BigDecimal energyTarget = round10(maintenance.multiply(BigDecimal.ONE.add(offsetPercent.divide(BigDecimal.valueOf(100)))));
        Range energy = new Range(round10(energyTarget.multiply(new BigDecimal("0.95"))),
                energyTarget, round10(energyTarget.multiply(new BigDecimal("1.05"))));

        BigDecimal[] proteinCoeffs = resistance
                ? new BigDecimal[]{new BigDecimal("1.4"), new BigDecimal("1.6"), new BigDecimal("2")}
                : new BigDecimal[]{new BigDecimal("1.2"), new BigDecimal("1.4"), new BigDecimal("1.6")};
        Range protein = new Range(roundG(weight.multiply(proteinCoeffs[0])),
                roundG(weight.multiply(proteinCoeffs[1])), roundG(weight.multiply(proteinCoeffs[2])));

        if (energy.min().compareTo(ENERGY_FLOOR) <= 0) issues.add("low_energy_requires_professional_support");
        if (protein.max().multiply(BigDecimal.valueOf(4))
                .compareTo(energy.min().multiply(PROTEIN_ENERGY_CAP)) > 0) {
            issues.add("protein_energy_balance_requires_review");
        }

        if (!issues.isEmpty()) {
            return new Target(Target.NOT_ELIGIBLE, List.of(), List.copyOf(issues), bmi,
                    maintenance.setScale(2, RoundingMode.HALF_UP), null, null, null);
        }
        return new Target(Target.OK, List.of(), List.of(), bmi,
                maintenance.setScale(2, RoundingMode.HALF_UP), energy, protein,
                offsetPercent.stripTrailingZeros().toPlainString());
    }

    /** 取整到最近 10 kcal（HALF_UP），与源 _round(x, "10") 一致。 */
    private static BigDecimal round10(BigDecimal value) {
        return value.divide(BigDecimal.TEN, 0, RoundingMode.HALF_UP).multiply(BigDecimal.TEN);
    }

    /** 取整到最近 1 g。 */
    private static BigDecimal roundG(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP);
    }
}
