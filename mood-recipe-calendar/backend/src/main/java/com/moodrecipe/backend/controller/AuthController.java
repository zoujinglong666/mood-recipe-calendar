package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.entity.User;
import com.moodrecipe.backend.repository.UserRepository;
import com.moodrecipe.backend.service.WechatService;
import com.moodrecipe.backend.service.UserSessionService;
import com.moodrecipe.backend.service.AccountDeletionService;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final WechatService wechatService;
    private final UserRepository userRepository;
    private final UserSessionService userSessionService;
    private final AccountDeletionService accountDeletionService;

    public AuthController(WechatService wechatService, UserRepository userRepository,
                          UserSessionService userSessionService, AccountDeletionService accountDeletionService) {
        this.wechatService = wechatService;
        this.userRepository = userRepository;
        this.userSessionService = userSessionService;
        this.accountDeletionService = accountDeletionService;
    }

    /**
     * 微信登录
     * POST /api/auth/login
     * body: { code }
     */
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String code = body.get("code");
        if (code == null || code.isEmpty()) {
            return ApiResponse.error("code 不能为空");
        }
        try {
            Map<String, Object> result = wechatService.login(code);
            return ApiResponse.ok(result);
        } catch (IllegalArgumentException | IllegalStateException e) {
            // 业务校验失败（如 code 无效）：文案可回显
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            // 未预期异常：细节只落日志，不透传给客户端（避免泄露底层 HTTP/IO/DB 信息）
            log.error("[登录] 未预期异常", e);
            return ApiResponse.error("登录失败，请稍后重试");
        }
    }

    /**
     * 获取用户信息
     * GET /api/auth/user?openid=xxx
     */
    @GetMapping("/user")
    public ApiResponse<User> getUser(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
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
    public ApiResponse<User> updateUser(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @RequestBody Map<String, String> body) {
        return userRepository.findByOpenid(openid).map(user -> {
            if (body.containsKey("nickname")) user.setNickname(body.get("nickname"));
            if (body.containsKey("avatarUrl")) user.setAvatarUrl(body.get("avatarUrl"));
            if (body.containsKey("remindTime")) user.setRemindTime(body.get("remindTime"));
            return ApiResponse.ok(userRepository.save(user));
        }).orElseGet(() -> ApiResponse.error(404, "用户不存在"));
    }

    /** 用户主动退出当前设备。 */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        userRepository.findByOpenid(openid).ifPresent(userSessionService::revoke);
        return ApiResponse.ok();
    }

    @DeleteMapping("/account")
    public ApiResponse<Void> deleteAccount(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @RequestBody Map<String, Boolean> body) {
        if (!Boolean.TRUE.equals(body == null ? null : body.get("confirmed"))) {
            return ApiResponse.error(400, "请确认注销账号");
        }
        accountDeletionService.delete(openid);
        return ApiResponse.ok();
    }
}
