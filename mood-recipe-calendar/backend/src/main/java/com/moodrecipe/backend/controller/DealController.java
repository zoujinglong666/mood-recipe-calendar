package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.service.JutuikeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/**
 * [2026-10-28] 优惠入口（聚推客 CPS）：活动列表 + 转链。
 *
 * 未配置 apikey 时统一返回 503，前端把入口隐藏——功能缺席不报错。
 * sid 用 openid 的 SHA-256 前 16 位：跟单归因可用，且不把原始身份发给第三方。
 */
@RestController
@RequestMapping("/api/deals")
public class DealController {

    private final JutuikeService jutuike;

    public DealController(JutuikeService jutuike) {
        this.jutuike = jutuike;
    }

    @GetMapping("/activities")
    public ApiResponse<List<JutuikeService.DealGroup>> activities() {
        if (!jutuike.isEnabled()) return ApiResponse.error(503, "优惠功能暂未开启");
        return ApiResponse.ok(jutuike.groupedActivities());
    }

    @GetMapping("/link")
    public ApiResponse<JutuikeService.DealLink> link(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @RequestParam("actId") long actId) {
        if (!jutuike.isEnabled()) return ApiResponse.error(503, "优惠功能暂未开启");
        if (actId <= 0) return ApiResponse.error(400, "活动无效");
        return jutuike.link(actId, sidOf(openid))
                .map(ApiResponse::ok)
                .orElseGet(() -> ApiResponse.error(404, "该活动暂不可领取"));
    }

    /** openid 单向哈希作跟单 sid：长度 16，第三方无法反查用户身份。 */
    private static String sidOf(String openid) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(openid.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (Exception ignored) {
            return "guozai";
        }
    }
}
