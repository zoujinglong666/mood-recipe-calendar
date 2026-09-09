package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.service.CompanionMessageService;
import com.moodrecipe.backend.service.GuozaiAgent;
import org.springframework.web.bind.annotation.*;

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
            @RequestParam(defaultValue = "12") int hour) {
        return ApiResponse.ok(guozaiAgent.companion(openid, hour));
    }
}
