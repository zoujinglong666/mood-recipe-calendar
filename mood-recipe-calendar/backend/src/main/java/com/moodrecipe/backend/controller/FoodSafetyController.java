package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.service.FoodSafetyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 食品安全知识接口：食材挑选技巧（采购清单）、烹调警示与慎食人群（菜谱/烹饪页）、
 * 高风险搭配校验（推荐结果自检展示）。知识库为服务端内置，升级无需发版。
 */
@RestController
@RequestMapping("/api/food-safety")
public class FoodSafetyController {

    private final FoodSafetyService foodSafety;

    public FoodSafetyController(FoodSafetyService foodSafety) {
        this.foodSafety = foodSafety;
    }

    /**
     * 按食材名查询安全提示（逗号分隔）。
     * type=PICK 挑选技巧 / COOK 烹调警示 / AVOID 慎食人群 / COMBO 搭配风险。
     */
    @GetMapping("/tips")
    public ApiResponse<Map<String, Object>> tips(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @RequestParam(defaultValue = "") String names) {
        List<String> items = Arrays.stream(names.split("[,，]"))
                .map(String::trim).filter(s -> !s.isEmpty()).limit(60).toList();
        List<FoodSafetyService.FoodTip> tips = new ArrayList<>();
        tips.addAll(foodSafety.pickTips(items));
        tips.addAll(foodSafety.cookingTips(items));
        foodSafety.riskyCombos(items)
                .forEach(text -> tips.add(new FoodSafetyService.FoodTip("搭配提醒", "COMBO", text)));
        return ApiResponse.ok(Map.of("tips", tips));
    }
}
