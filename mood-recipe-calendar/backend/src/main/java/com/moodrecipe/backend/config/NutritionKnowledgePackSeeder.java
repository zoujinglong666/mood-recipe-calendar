package com.moodrecipe.backend.config;

import com.moodrecipe.backend.config.AppClock;
import com.moodrecipe.backend.entity.NutritionKnowledgePack;
import com.moodrecipe.backend.entity.SeasonalIngredient;
import com.moodrecipe.backend.repository.NutritionKnowledgePackRepository;
import com.moodrecipe.backend.repository.SeasonalIngredientRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 首批仅包含产品自有、健康成年人适用的日常规则；不导入任何指南原文或慢病建议。
 * 空表初始化后由运营/审核流程维护，避免部署覆盖人工审核过的数据。
 */
@Component
public class NutritionKnowledgePackSeeder implements ApplicationRunner {

    private static final String PRODUCT_SOURCE = "mood-recipe-calendar:healthy-adult-v1";
    private final NutritionKnowledgePackRepository packs;
    private final SeasonalIngredientRepository ingredients;

    public NutritionKnowledgePackSeeder(NutritionKnowledgePackRepository packs,
                                        SeasonalIngredientRepository ingredients) {
        this.packs = packs;
        this.ingredients = ingredients;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (packs.findAll().isEmpty()) packs.saveAll(List.of(healthyAdultPack()));
        if (ingredients.findAll().isEmpty()) ingredients.saveAll(autumnIngredients());
    }

    private NutritionKnowledgePack healthyAdultPack() {
        NutritionKnowledgePack pack = new NutritionKnowledgePack();
        pack.setCode("healthy-adult-balanced-meals");
        pack.setVersion("1.0.0");
        pack.setTitle("日常三餐基础搭配");
        pack.setAudience("健康成年人");
        pack.setRecommendation("每餐优先组合主食、蔬菜和蛋白质来源，并在一天内尽量变换核心食材。");
        pack.setIngredientNotes("优先选择用户所在地当季候选；用户过敏、忌口和明确不喜欢的食材始终排除。");
        pack.setHardConstraints("不适用于疾病饮食、孕产妇、儿童或其他需要专业个体化建议的人群。");
        pack.setExplanation("这是日常饮食建议，用于帮助安排多样化的一日三餐，不替代医疗或营养治疗建议。");
        pack.setSourceKind("PRODUCT_ORIGINAL");
        pack.setSourceReference(PRODUCT_SOURCE);
        pack.setLicenseStatus(NutritionKnowledgePack.COMMERCIALLY_USABLE);
        pack.setReviewedAt(AppClock.now());
        pack.setEnabled(true);
        return pack;
    }

    private List<SeasonalIngredient> autumnIngredients() {
        return List.of(
                ingredient("西兰花", 9, 11, "菜花、油麦菜"),
                ingredient("菠菜", 10, 12, "小白菜、油麦菜"),
                ingredient("南瓜", 9, 12, "红薯、玉米"),
                ingredient("莲藕", 9, 12, "山药、白萝卜"));
    }

    private SeasonalIngredient ingredient(String name, int startMonth, int endMonth, String substitutes) {
        SeasonalIngredient ingredient = new SeasonalIngredient();
        ingredient.setName(name);
        ingredient.setRegion("全国通用");
        ingredient.setStartMonth(startMonth);
        ingredient.setEndMonth(endMonth);
        ingredient.setSubstitutes(substitutes);
        ingredient.setSourceReference(PRODUCT_SOURCE);
        ingredient.setLicenseStatus(NutritionKnowledgePack.COMMERCIALLY_USABLE);
        ingredient.setEnabled(true);
        return ingredient;
    }
}
