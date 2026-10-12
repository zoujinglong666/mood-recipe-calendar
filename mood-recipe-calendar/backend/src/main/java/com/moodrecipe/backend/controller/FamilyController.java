package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.service.FamilyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/** 家庭共享：创建 / 加入 / 退出 / 我的家庭。 */
@RestController
@RequestMapping("/api/family")
public class FamilyController {
    private final FamilyService family;

    @Autowired
    public FamilyController(FamilyService family) {
        this.family = family;
    }

    @GetMapping
    public ApiResponse<?> myFamily(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        return ApiResponse.ok(family.myFamily(openid));
    }

    @PostMapping
    public ApiResponse<?> create(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        try {
            return ApiResponse.ok(family.create(openid));
        }
        catch (IllegalStateException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PostMapping("/join")
    public ApiResponse<?> join(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @RequestBody JoinRequest request) {
        try {
            return ApiResponse.ok(family.join(openid, request == null ? null : request.code()));
        }
        catch (IllegalStateException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PostMapping("/leave")
    public ApiResponse<?> leave(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        try {
            family.leave(openid);
            return ApiResponse.ok();
        }
        catch (IllegalStateException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    public record JoinRequest(String code) {}
}
