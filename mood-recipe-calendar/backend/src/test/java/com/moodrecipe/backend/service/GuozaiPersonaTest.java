package com.moodrecipe.backend.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 推荐理由的「灵魂三问」契约（2026-10-23 打磨项①）：
 * description 曾退化为「30字以内的治愈理由」这类模板文案，是用户觉得推荐不走心的直接原因。
 * 这些断言锁死 prompt 必须逼模型回答三问，防止后人改 prompt 时把灵魂删掉。
 */
class GuozaiPersonaTest {

    private final GuozaiPersona persona = new GuozaiPersona();

    @Test
    void recipePromptDemandsThreeQuestionReason() {
        String prompt = persona.recipePrompt("疲惫", "最近常做番茄炒蛋，已连续记录 5 天", "微辣", "DINNER", null);

        // 三问契约：选菜前必须先回答
        assertTrue(prompt.contains("哪个真实细节相关"), "缺少「为什么是这道」的锚点");
        assertTrue(prompt.contains("为什么此刻合适"), "缺少「为什么是现在」的锚点");
        assertTrue(prompt.contains("为什么适合这位用户"), "缺少「为什么给你」的锚点");

        // description 规格：40-60 字两句话，而非 30 字治愈文案
        assertTrue(prompt.contains("40到60字"), "description 长度规格未升级");
        assertFalse(prompt.contains("30字以内的治愈理由"), "模板化 description 规格复活了");

        // 反模板纪律
        assertTrue(prompt.contains("不要列点、不要口号、不要复述分析原话"));
    }

    @Test
    void recipePromptKeepsMealTypeAndNutritionInjection() {
        String dinner = persona.recipePrompt("平静", "分析", "无明确偏好", "DINNER", "要点：荤素搭配");
        assertTrue(dinner.contains("晚餐"), "餐别标签丢失");
        assertTrue(dinner.contains("荤素搭配"), "营养知识未注入生成");

        String breakfast = persona.recipePrompt("平静", "分析", "无明确偏好", "BREAKFAST", null);
        assertTrue(breakfast.contains("【硬性要求】"), "早餐硬约束丢失");
    }

    @Test
    void recipePromptDelegatesShortFormToFullForm() {
        String shortForm = persona.recipePrompt("开心", "分析", "无");
        assertTrue(shortForm.contains("哪个真实细节相关"), "三问契约必须覆盖深度推荐链路");
    }
}
