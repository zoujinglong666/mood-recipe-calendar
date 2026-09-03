package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.entity.User;
import com.moodrecipe.backend.repository.UserRepository;
import com.moodrecipe.backend.service.WechatService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final WechatService wechatService;
    private final UserRepository userRepository;

    public AuthController(WechatService wechatService, UserRepository userRepository) {
        this.wechatService = wechatService;
        this.userRepository = userRepository;
    }

    /**
     * 微信登录
     * POST /api/auth/login
     * body: { code, nickname, avatarUrl }
     */
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String code = body.get("code");
        String nickname = body.get("nickname");
        String avatarUrl = body.get("avatarUrl");
        if (code == null || code.isEmpty()) {
            return ApiResponse.error("code 不能为空");
        }
        try {
            Map<String, Object> result = wechatService.login(code, nickname, avatarUrl);
            return ApiResponse.ok(result);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取用户信息
     * GET /api/auth/user?openid=xxx
     */
    @GetMapping("/user")
    public ApiResponse<User> getUser(@RequestParam String openid) {
        return userRepository.findByOpenid(openid)
            .map(ApiResponse::ok)
            .orElseGet(() -> ApiResponse.error(404, "用户不存在"));
    }

    /**
     * 更新用户信息
     * PUT /api/auth/user
     * body: { openid, nickname, avatarUrl }
     */
    @PutMapping("/user")
    public ApiResponse<User> updateUser(@RequestBody Map<String, String> body) {
        String openid = body.get("openid");
        if (openid == null) return ApiResponse.error("openid 不能为空");
        return userRepository.findByOpenid(openid).map(user -> {
            if (body.containsKey("nickname")) user.setNickname(body.get("nickname"));
            if (body.containsKey("avatarUrl")) user.setAvatarUrl(body.get("avatarUrl"));
            if (body.containsKey("remindTime")) user.setRemindTime(body.get("remindTime"));
            return ApiResponse.ok(userRepository.save(user));
        }).orElseGet(() -> ApiResponse.error(404, "用户不存在"));
    }
}
