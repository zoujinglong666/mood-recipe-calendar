package com.moodrecipe.backend.service.nutrition;

import com.moodrecipe.backend.service.nutrition.NutritionTargetService.ActivityLevel;
import com.moodrecipe.backend.service.nutrition.NutritionTargetService.Goal;
import com.moodrecipe.backend.service.nutrition.NutritionTargetService.Inputs;
import com.moodrecipe.backend.service.nutrition.NutritionTargetService.Range;
import com.moodrecipe.backend.service.nutrition.NutritionTargetService.Sex;
import com.moodrecipe.backend.service.nutrition.NutritionTargetService.Target;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EER 移植交叉验证：期望值由源实现（aiiqc/nutrition-skill nutrition_core/targets.py，
 * 全 Decimal 精确算术）在 2026-10-28 实际运行生成，非手算。
 */
class NutritionTargetServiceTest {

    private final NutritionTargetService service = new NutritionTargetService();

    private Inputs inputs(Sex sex, int age, String h, String w, ActivityLevel act, boolean res, Goal goal) {
        return new Inputs(sex, age, new BigDecimal(h), new BigDecimal(w), act, res, goal);
    }

    @Test
    void male35_175_75_active_wellbeing_matchesPython() {
        // maintenance=2960.02 → energy 2810/2960/3110；蛋白(无阻力) 90/105/120
        Target t = service.compute(inputs(Sex.MALE, 35, "175", "75", ActivityLevel.ACTIVE, false, Goal.WELLBEING));
        assertThat(t.status()).isEqualTo(Target.OK);
        assertThat(t.bmi()).isEqualByComparingTo("24.49");
        assertThat(t.maintenanceKcal()).isEqualByComparingTo("2960.02");
        assertRange(t.energyKcal(), "2810", "2960", "3110");
        assertRange(t.proteinG(), "90", "105", "120");
        assertThat(t.energyOffsetPercent()).isEqualTo("0");
    }

    @Test
    void female28_160_55_lowActive_fatLoss_matchesPython() {
        // maintenance=2103.19，-10% → 1892.871 → 1890；min 1795.5 → 1800；蛋白(阻力) 77/88/110
        Target t = service.compute(inputs(Sex.FEMALE, 28, "160", "55", ActivityLevel.LOW_ACTIVE, true, Goal.FAT_LOSS));
        assertThat(t.status()).isEqualTo(Target.OK);
        assertThat(t.bmi()).isEqualByComparingTo("21.48");
        assertThat(t.maintenanceKcal()).isEqualByComparingTo("2103.19");
        assertRange(t.energyKcal(), "1800", "1890", "1980");
        assertRange(t.proteinG(), "77", "88", "110");
        assertThat(t.energyOffsetPercent()).isEqualTo("-10");
    }

    @Test
    void female52_158_62_veryActive_matchesPython() {
        // maintenance=2359.09 → energy 2240/2360/2480；蛋白(无阻力) 74/87/99
        Target t = service.compute(inputs(Sex.FEMALE, 52, "158", "62", ActivityLevel.VERY_ACTIVE, false, Goal.WELLBEING));
        assertThat(t.status()).isEqualTo(Target.OK);
        assertThat(t.bmi()).isEqualByComparingTo("24.84");
        assertThat(t.maintenanceKcal()).isEqualByComparingTo("2359.09");
        assertRange(t.energyKcal(), "2240", "2360", "2480");
        assertRange(t.proteinG(), "74", "87", "99");
    }

    @Test
    void male19_120_35_boundaryInputs_ok() {
        // 身高/体重压线仍可算：maintenance=1820.8 → energy 1730/1820/1910；蛋白 42/49/56
        Target t = service.compute(inputs(Sex.MALE, 19, "120", "35", ActivityLevel.INACTIVE, false, Goal.WELLBEING));
        assertThat(t.status()).isEqualTo(Target.OK);
        assertThat(t.bmi()).isEqualByComparingTo("24.31");
        assertThat(t.maintenanceKcal()).isEqualByComparingTo("1820.80");
        assertRange(t.energyKcal(), "1730", "1820", "1910");
        assertRange(t.proteinG(), "42", "49", "56");
    }

    @Test
    void muscleGain_requiresResistanceTraining() {
        Target t = service.compute(inputs(Sex.MALE, 45, "170", "90", ActivityLevel.INACTIVE, false, Goal.MUSCLE_GAIN));
        assertThat(t.status()).isEqualTo(Target.NOT_ELIGIBLE);
        assertThat(t.issues()).containsExactly("resistance_training_required");
        assertThat(t.bmi()).isEqualByComparingTo("31.14");
        assertThat(t.maintenanceKcal()).isEqualByComparingTo("2639.72");
    }

    @Test
    void ageOutside19to64_notEligible() {
        Target t = service.compute(inputs(Sex.FEMALE, 70, "160", "60", ActivityLevel.ACTIVE, false, Goal.WELLBEING));
        assertThat(t.status()).isEqualTo(Target.NOT_ELIGIBLE);
        assertThat(t.issues()).containsExactly("outside_supported_age");
    }

    @Test
    void fatLossWithLowBmi_notEligible() {
        Target t = service.compute(inputs(Sex.FEMALE, 30, "165", "50", ActivityLevel.ACTIVE, false, Goal.FAT_LOSS));
        assertThat(t.status()).isEqualTo(Target.NOT_ELIGIBLE);
        assertThat(t.issues()).containsExactly("outside_supported_bmi", "fat_loss_near_lower_bmi");
        assertThat(t.bmi()).isEqualByComparingTo("18.37");
    }

    @Test
    void missingFields_reportedNotGuessed() {
        Target t = service.compute(new Inputs(Sex.UNKNOWN, null, null, new BigDecimal("70"), null, null, null));
        assertThat(t.status()).isEqualTo(Target.NEEDS_INPUT);
        assertThat(t.missingFields()).containsExactlyInAnyOrder("sex", "age", "heightCm", "activity");
    }

    private void assertRange(Range range, String min, String target, String max) {
        assertThat(range.min()).isEqualByComparingTo(min);
        assertThat(range.target()).isEqualByComparingTo(target);
        assertThat(range.max()).isEqualByComparingTo(max);
    }
}
