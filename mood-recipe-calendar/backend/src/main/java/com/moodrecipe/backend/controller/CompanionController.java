package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.config.AppClock;
import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.service.CompanionMessageService;
import com.moodrecipe.backend.service.GuozaiAgent;
import com.moodrecipe.backend.service.UsageQuotaService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/companion")
public class CompanionController {
    private final GuozaiAgent guozaiAgent;
    private final UsageQuotaService quotas;

    public CompanionController(GuozaiAgent guozaiAgent, UsageQuotaService quotas) {
        this.guozaiAgent = guozaiAgent;
        this.quotas = quotas;
    }

    @GetMapping("/message")
    public ApiResponse<CompanionMessageService.Message> message(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @RequestParam(defaultValue = "12") int hour,
            @RequestParam(required = false) String date) {
        LocalDate localDate;
        try {
            localDate = date == null || date.isBlank()
                    ? LocalDate.now(AppClock.ZONE) : LocalDate.parse(date);
        } catch (RuntimeException ignored) {
            localDate = LocalDate.now(AppClock.ZONE);
        }
        // 陪伴寄语每次真实调 LLM：接入每日轻量额度，防刷量烧成本
        try {
            quotas.consume(openid, UsageQuotaService.Feature.LLM_LIGHT);
        } catch (IllegalStateException e) {
            return ApiResponse.error(429, e.getMessage());
        }
        return ApiResponse.ok(guozaiAgent.companion(openid, hour, localDate));
    }
}
