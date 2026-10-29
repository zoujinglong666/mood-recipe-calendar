package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.Recipe;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * [2026-10-28] 封面 prompt 改造回归：场景轮换、步骤融合、动作特写的人物豁免。
 * 背景：旧版场景词写死一套（原木桌+陶瓷+40°），三餐封面像同一张桌子拍的；
 * 且完全不读 steps，砂锅炖菜也端端正正摆盘子里，没有食欲。
 */
class AgnesRecipeImagePromptTest {

    @Test
    void propsCueMatchesDishType() {
        // 来源①道具系统：道具是「能马上吃」的叙事证据
        assertTrue(AgnesRecipeImageService.propsCue(recipe(1L, "番茄牛腩汤", "炖四十分钟")).contains("汤勺"));
        assertTrue(AgnesRecipeImageService.propsCue(recipe(2L, "酱油炒饭", "翻炒出锅")).contains("木筷"));
        assertTrue(AgnesRecipeImageService.propsCue(recipe(3L, "香煎鸡翅", "小火煎至金黄")).contains("蘸酱"));
        assertEquals("", AgnesRecipeImageService.propsCue(recipe(4L, "白灼芥蓝", "焯水后淋上酱汁")));
    }

    @Test
    void fullPromptAbsorbsAllSources() {
        AgnesRecipeImageService service = new AgnesRecipeImageService(new com.fasterxml.jackson.databind.ObjectMapper(),
                "k", "http://localhost", "m", "1024x1024", "1:1");
        String prompt = service.promptForTest(recipe(9L, "玉米排骨汤", "砂锅炖一小时"));
        assertTrue(prompt.contains("三分法")); // 来源①构图法则
        assertTrue(prompt.contains("汤勺")); // 来源①道具系统
        assertTrue(prompt.contains("不自然的对称摆盘")); // 来源①反向清单
        assertTrue(prompt.contains("Portra 400")); // 来源①胶片调色
        assertTrue(prompt.contains("砂锅")); // 来源②/③步骤融合（特征器物优先于泛化动作）
    }

    private Recipe recipe(long id, String name, String steps) {
        Recipe r = new Recipe();
        r.setId(id);
        r.setName(name);
        r.setSteps(steps);
        return r;
    }

    @Test
    void sceneIsStablePerRecipe() {
        // 同一道菜两次取场景必须一致（封面风格不能重拍就漂移）
        Recipe r = recipe(42L, "番茄牛腩", "炖熟即可");
        assertEquals(AgnesRecipeImageService.sceneFor(r), AgnesRecipeImageService.sceneFor(r));
    }

    @Test
    void scenesSpreadAcrossDifferentRecipes() {
        // 一批不同 id 的菜应分散到至少 3 种场景——分散不开就还是「同一张桌子」
        Set<String> scenes = new HashSet<>();
        for (long id = 1; id <= 60; id++) {
            scenes.add(AgnesRecipeImageService.sceneFor(recipe(id, "菜" + id, "炒熟即可")));
        }
        assertTrue(scenes.size() >= 3, "场景分散不足，实际只用了 " + scenes.size() + " 种");
    }

    @Test
    void stepCueMatchesCookingMethod() {
        assertEquals(AgnesRecipeImageService.sceneFor(recipe(1L, "x", "炖熟即可")) != null, true);
        assertTrue(AgnesRecipeImageService.stepCue(recipe(1L, "小鸡炖蘑菇", "热锅下油，炖四十分钟")).contains("砂锅"));
        assertTrue(AgnesRecipeImageService.stepCue(recipe(2L, "清蒸鲈鱼", "上锅蒸八分钟")).contains("蒸笼"));
        assertTrue(AgnesRecipeImageService.stepCue(recipe(3L, "青椒炒肉", "热锅下油，大火翻炒两分钟")).contains("铁锅"));
        assertTrue(AgnesRecipeImageService.stepCue(recipe(4L, "香煎豆腐", "小火煎至两面金黄")).contains("煎锅"));
        // 无烹饪关键词时给空串（保守下界，不硬塞）
        assertEquals("", AgnesRecipeImageService.stepCue(recipe(5L, "神秘料理", "处理好即可")));
    }

    @Test
    void fullPromptContainsKeySignals() {
        AgnesRecipeImageService service = new AgnesRecipeImageService(new com.fasterxml.jackson.databind.ObjectMapper(),
                "k", "http://localhost", "m", "1024x1024", "1:1");
        Recipe r = recipe(7L, "小鸡炖蘑菇", "大火翻炒，炖四十分钟");
        String prompt = service.promptForTest(r);
        assertTrue(prompt.contains("Portra 400")); // 胶片色调（拟真度）
        assertTrue(prompt.contains("砂锅")); // 步骤融合
        assertTrue(prompt.contains("AI 味的均匀油光")); // 反向清单四件套
        assertFalse(prompt.contains("令人有食欲")); // 不用模糊形容词，用物理信号
    }
}
