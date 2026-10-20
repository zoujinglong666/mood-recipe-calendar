package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.service.AdRewardService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 激励视频广告回调：用户看完广告后由客户端上报，服务端记账解锁对应能力。 */
@RestController
@RequestMapping("/api/ad")
public class AdController {
    private final AdRewardService adRewards;

    public AdController(AdRewardService adRewards) {
        this.adRewards = adRewards;
    }

    /**
     * 看完广告回调。type 仅允许白名单，避免被滥用写入任意 reward_type。
     * body: { "type": "RESHUFFLE_UNLIMITED", "adUnitId": "xxx" }
     */
    @PostMapping("/reward")
    public ApiResponse<?> reward(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
                                 @RequestBody(required = false) Map<String, String> body) {
        String type = body == null ? null : body.get("type");
        String adUnitId = body == null ? null : body.get("adUnitId");
        if (!AdRewardService.RESHUFFLE_UNLIMITED.equals(type)) {
            return ApiResponse.error(400, "不支持的奖励类型");
        }
        adRewards.record(openid, type, adUnitId);
        return ApiResponse.ok(Map.of("granted", true, "type", type));
    }
}
