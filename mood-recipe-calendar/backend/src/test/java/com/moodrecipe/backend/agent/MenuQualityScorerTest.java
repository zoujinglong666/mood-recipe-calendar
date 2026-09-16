package com.moodrecipe.backend.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 菜单质量校验是"规划之后必须过的那道关"，这些用例就是它的验收标准。 */
class MenuQualityScorerTest {

    private final MenuQualityScorer scorer = new MenuQualityScorer();

    @Test
    void treatAvoidedIngredientAsHardViolation() {
        MenuQualityScorer.MenuQuality quality = scorer.evaluate(
                List.of(day(0, dish("香菜牛肉", List.of("香菜", "牛肉")))),
                new MenuQualityScorer.Constraints(Set.of("香菜"), Set.of(), null, false, false,
                        "BALANCED", "DAILY", Set.of(), Set.of(), null, false));

        assertTrue(quality.hasHard());
        assertTrue(quality.hard().stream().anyMatch(issue -> "AVOID_INGREDIENT".equals(issue.code())));
        assertTrue(quality.score() < 100);
    }

    @Test
    void treatAllergenAsHardViolation() {
        MenuQualityScorer.MenuQuality quality = scorer.evaluate(
                List.of(day(0, dish("花生焖猪蹄", List.of("花生", "猪蹄")))),
                new MenuQualityScorer.Constraints(Set.of(), Set.of("花生"), null, false, false,
                        "BALANCED", "DAILY", Set.of(), Set.of(), null, false));

        assertTrue(quality.hard().stream().anyMatch(issue -> "ALLERGEN".equals(issue.code())));
    }

    /** 用户说不吃辣时，辣菜必须被判为硬性问题，而不是"建议改进"。 */
    @Test
    void blocksSpicyDishesWhenUserCannotEatSpicy() {
        MenuQualityScorer.MenuQuality quality = scorer.evaluate(
                List.of(day(0, dish("麻辣水煮鱼", List.of("鱼", "花椒")))),
                new MenuQualityScorer.Constraints(Set.of(), Set.of(), "不吃辣", false, false,
                        "BALANCED", "DAILY", Set.of(), Set.of(), null, false));

        assertTrue(quality.hard().stream().anyMatch(issue -> "SPICE_CONFLICT".equals(issue.code())));
    }

    /** 学习闭环：反馈过"不想做/没做成"的菜，不许再排进来。 */
    @Test
    void blocksDishesUserAlreadyRejected() {
        MenuQualityScorer.MenuQuality quality = scorer.evaluate(
                List.of(day(0, dish("红烧肉", List.of("五花肉")))),
                new MenuQualityScorer.Constraints(Set.of(), Set.of(), null, false, false,
                        "BALANCED", "DAILY", Set.of(), Set.of("红烧肉"), null, false));

        assertTrue(quality.hard().stream().anyMatch(issue -> "REJECTED_DISH".equals(issue.code())));
    }

    @Test
    void blocksDishesJustCookedRecently() {
        MenuQualityScorer.MenuQuality quality = scorer.evaluate(
                List.of(day(0, dish("番茄炒蛋", List.of("番茄", "鸡蛋")))),
                new MenuQualityScorer.Constraints(Set.of(), Set.of(), null, false, false,
                        "BALANCED", "DAILY", Set.of("番茄炒蛋"), Set.of(), null, false));

        assertTrue(quality.hard().stream().anyMatch(issue -> "REPEAT_RECENT".equals(issue.code())));
    }

    @Test
    void blocksSpicyAndDuplicateDishesForFamilyWithChild() {
        MenuQualityScorer.MenuQuality quality = scorer.evaluate(
                List.of(day(0, dish("麻辣香锅", List.of("藕片")), dish("麻辣香锅", List.of("藕片")))),
                new MenuQualityScorer.Constraints(Set.of(), Set.of(), "微辣", false, true,
                        "BALANCED", "DAILY", Set.of(), Set.of(), null, false));

        assertTrue(quality.hard().stream().anyMatch(issue -> "CHILD_SPICY".equals(issue.code())));
        assertTrue(quality.hard().stream().anyMatch(issue -> "DUPLICATE_DISH".equals(issue.code())));
    }

    /** 营养结构与食材复用属于软性问题：要扣分、要提示，但不触发重排。 */
    @Test
    void reportsNutritionAndReuseAsSoftIssues() {
        MenuQualityScorer.MenuQuality quality = scorer.evaluate(
                List.of(day(0, dish("红烧肉", List.of("五花肉 500g"))),
                        day(1, dish("酱牛肉", List.of("牛腱 300g")))),
                new MenuQualityScorer.Constraints(Set.of(), Set.of(), null, false, false,
                        "BALANCED", "DAILY", Set.of(), Set.of(), null, false));

        assertFalse(quality.hasHard());
        assertTrue(quality.soft().stream().anyMatch(issue -> "NO_VEGETABLE".equals(issue.code())));
        assertTrue(quality.soft().stream().anyMatch(issue -> "LOW_REUSE".equals(issue.code())));
        assertTrue(quality.score() < 100);
        assertFalse(quality.violationBrief().isBlank());
    }

    @Test
    void emptyMenuIsAlwaysRejected() {
        MenuQualityScorer.MenuQuality quality = scorer.evaluate(List.of(), MenuQualityScorer.Constraints.empty());
        assertTrue(quality.hasHard());
        assertEquals(0, quality.score());
    }

    private MenuQualityScorer.DayInput day(int weekday, MenuQualityScorer.DishInput... dishes) {
        return new MenuQualityScorer.DayInput(weekday, List.of(dishes));
    }

    private MenuQualityScorer.DishInput dish(String name, List<String> ingredients) {
        return new MenuQualityScorer.DishInput(name, "MAIN", ingredients, 30, "简单");
    }
}
