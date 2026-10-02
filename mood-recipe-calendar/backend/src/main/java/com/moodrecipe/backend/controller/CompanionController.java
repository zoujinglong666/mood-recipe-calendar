package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.config.AppClock;
import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.service.CompanionMessageService;
import com.moodrecipe.backend.service.GuozaiAgent;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/companion")
public class CompanionController {
    private final GuozaiAgent guozaiAgent;

    public CompanionController(GuozaiAgent guozaiAgent) {
        this.guozaiAgent = guozaiAgent;
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
        return ApiResponse.ok(guozaiAgent.companion(openid, hour, localDate));
    }
}
