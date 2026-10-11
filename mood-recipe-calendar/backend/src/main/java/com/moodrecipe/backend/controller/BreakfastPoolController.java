package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.service.BreakfastPoolSeeder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 早餐组合菜谱池的手动运维入口。
 * 池子由 BreakfastPoolSeeder 在应用启动时自动补齐；此接口用于生成服务曾不可用、
 * 需要立即补池的场景。target 上限 30，避免一次打爆生图额度。
 */
@RestController
@RequestMapping("/api/breakfast-pool")
public class BreakfastPoolController {

    private final BreakfastPoolSeeder seeder;

    public BreakfastPoolController(BreakfastPoolSeeder seeder) {
        this.seeder = seeder;
    }

    @PostMapping("/seed")
    public ApiResponse<Map<String, Object>> seed(@RequestParam(defaultValue = "12") int target) {
        int safeTarget = Math.max(1, Math.min(target, 30));
        int saved = seeder.seedIfNeeded(safeTarget);
        return ApiResponse.ok(Map.of(
                "target", safeTarget,
                "saved", saved,
                "poolSize", seeder.breakfastPoolSize()));
    }
}
