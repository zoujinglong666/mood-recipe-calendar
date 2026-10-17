package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.service.PersonalityService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 干饭人格 · AI 语义判定：12 题答案 → 统一 16 型目录判型 + 锅仔专属点评。 */
@RestController
@RequestMapping("/api/personality")
public class PersonalityController {

    private final PersonalityService personality;

    public PersonalityController(PersonalityService personality) {
        this.personality = personality;
    }

    /** answers：12 道题各自所选选项的下标（0-3）；test：eat=干饭人格 / cook=做饭人格。 */
    @PostMapping("/analyze")
    public ApiResponse<PersonalityService.PersonalityView> analyze(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @RequestBody AnalyzeRequest request) {
        try {
            return ApiResponse.ok(personality.analyze(request == null ? "eat" : request.test(), request.answers()));
        }
        catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    public record AnalyzeRequest(List<Integer> answers, String test) {}
}
