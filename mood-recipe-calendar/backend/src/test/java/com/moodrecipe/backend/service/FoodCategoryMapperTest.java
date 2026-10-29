package com.moodrecipe.backend.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 食物类别映射回归：重点防「单字关键词误命中」——历史上「西」「豆」「蛋」
 * 一类的单字曾把西红柿/土豆/蟹味菇算错类别，直接污染多样性计数。
 */
class FoodCategoryMapperTest {

    @Test
    void mixedDishHitsMultipleCategories() {
        // 番茄鸡蛋牛肉面 → 主食 + 瓜茄 + 蛋类 + 畜禽肉
        Set<String> hit = FoodCategoryMapper.categoriesOf("番茄鸡蛋牛肉面", "番茄,鸡蛋,牛肉,面条");
        assertThat(hit).contains("米面主食", "瓜茄类", "蛋类", "畜禽肉");
    }

    @Test
    void falsePositiveCompoundsStripped() {
        // 蟹味菇剥离去重后不再命中「蟹」→ 不误判水产；菌菇本身识别不出（宁漏计不虚高）
        assertThat(FoodCategoryMapper.categoriesOf("蟹味菇炒肉片", ""))
                .contains("畜禽肉")
                .doesNotContain("水产", "根茎菌藻");
        assertThat(FoodCategoryMapper.categoriesOf("蟹黄豆腐", ""))
                .contains("豆制品")
                .doesNotContain("水产");
    }

    @Test
    void potatoIsTuberNotBean() {
        Set<String> hit = FoodCategoryMapper.categoriesOf("土豆炖牛肉", "土豆,牛肉,胡萝卜");
        assertThat(hit).contains("杂粮薯类", "畜禽肉", "根茎菌藻")
                .doesNotContain("豆制品");
    }

    @Test
    void seafoodAndDairyRecognized() {
        Set<String> hit = FoodCategoryMapper.categoriesOf("三文鱼牛奶燕麦粥", "三文鱼,牛奶,燕麦");
        assertThat(hit).contains("水产", "奶类", "杂粮薯类");
    }

    @Test
    void unknownIngredientsReturnEmpty_lowerBoundOnly() {
        // 识别不出就为空：多样性是下界，只低估不虚高
        assertThat(FoodCategoryMapper.categoriesOf(null, null)).isEmpty();
        assertThat(FoodCategoryMapper.categoriesOf("", "  ")).isEmpty();
        assertThat(FoodCategoryMapper.categoriesOf("神秘料理", ".xyz")).isEmpty();
    }

    @Test
    void taxonomySizeSupportsWeeklyTarget() {
        // 14 个细类支撑「每周 25 种」的口径需要靠类别×天数累计，类别数本身须 > 10 才有意义
        assertThat(FoodCategoryMapper.allCategories().size()).isGreaterThan(10);
    }
}
