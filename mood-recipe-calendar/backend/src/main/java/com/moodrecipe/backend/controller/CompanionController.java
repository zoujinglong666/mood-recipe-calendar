package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.service.CompanionMessageService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companion")
public class CompanionController {
    private final CompanionMessageService messages;

    public CompanionController(CompanionMessageService messages) { this.messages = messages; }

    @GetMapping("/message")
    public ApiResponse<CompanionMessageService.Message> message(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @RequestParam(defaultValue = "12") int hour) {
        return ApiResponse.ok(messages.create(openid, hour));
    }
}
