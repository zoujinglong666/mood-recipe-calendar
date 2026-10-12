package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.service.BreakfastPoolSeeder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 早餐组合菜谱池的手动运维入口。
 * 池子由 BreakfastPoolSeeder 在应用启动时自动补齐；此接口用于生成服务曾不可用、
 * 需要立即补池的场景。target 上限 30，避免一次打爆生图额度。
 *
 * 安全：该接口在鉴权白名单内（无用户登录态），但会触发付费 LLM 生图，
 * 因此必须携带运维令牌 X-Ops-Token（环境变量 OPS_TOKEN 配置）；未配置令牌时一律拒绝。
 */
@RestController
@RequestMapping("/api/breakfast-pool")
public class BreakfastPoolController {

    private final BreakfastPoolSeeder seeder;
    private final String opsToken;

    public BreakfastPoolController(BreakfastPoolSeeder seeder,
                                   @Value("${ops.token:}") String opsToken) {
        this.seeder = seeder;
        this.opsToken = opsToken;
    }

    @PostMapping("/seed")
    public ApiResponse<Map<String, Object>> seed(@RequestParam(defaultValue = "12") int target,
                                                 @RequestHeader(value = "X-Ops-Token", required = false) String token) {
        if (opsToken == null || opsToken.isBlank() || token == null || !opsToken.equals(token))
            return ApiResponse.error(403, "无效的运维令牌");
        int safeTarget = Math.max(1, Math.min(target, 30));
        int saved = seeder.seedIfNeeded(safeTarget);
        return ApiResponse.ok(Map.of(
                "target", safeTarget,
                "saved", saved,
                "poolSize", seeder.breakfastPoolSize()));
    }
}
