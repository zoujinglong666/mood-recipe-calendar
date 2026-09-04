package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.entity.User;
import com.moodrecipe.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class WechatService {

    private final UserRepository userRepository;
    private final SessionKeyCipher sessionKeyCipher;
    private final UserSessionService userSessionService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${wechat.appid:}")
    private String appid;

    @Value("${wechat.secret:}")
    private String secret;

    public WechatService(UserRepository userRepository, SessionKeyCipher sessionKeyCipher, UserSessionService userSessionService) {
        this.userRepository = userRepository;
        this.sessionKeyCipher = sessionKeyCipher;
        this.userSessionService = userSessionService;
    }

    /**
     * 是否使用 mock 模式（H5 本地开发调试）
     * 判断依据：code 是否带 h5_dev_ 前缀，或 appid/secret 未配置
     * 小程序真实 code 一律走微信 code2session
     */
    private boolean isMockCode(String code) {
        if (appid == null || appid.isEmpty() || secret == null || secret.isEmpty()) {
            return true;
        }
        return code != null && code.startsWith("h5_dev_");
    }

    /**
     * 微信登录：用 code 换 openid，然后创建/更新用户
     */
    public Map<String, Object> login(String code, String nickname, String avatarUrl) {
        String openid;
        String sessionKey = null;

        if (isMockCode(code)) {
            // Mock 模式（H5 开发调试）：用 code 作为 openid
            openid = "mock_" + (code != null ? code : "dev");
        } else {
            // 真实微信登录：code2session 换 openid
            String url = String.format(
                "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                appid, secret, code
            );
            try {
                String response = restTemplate.getForObject(url, String.class);
                JsonNode root = objectMapper.readTree(response);
                if (root.has("errcode") && root.get("errcode").asInt() != 0) {
                    throw new RuntimeException("微信登录失败: " + root.path("errmsg").asText());
                }
                openid = root.path("openid").asText();
                if (openid == null || openid.isEmpty()) {
                    throw new RuntimeException("微信登录失败: 未获取到 openid");
                }
                sessionKey = root.path("session_key").asText(null);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("微信登录请求失败: " + e.getMessage());
            }
        }

        // 查找或创建用户
        User user = userRepository.findByOpenid(openid).orElseGet(() -> {
            User u = new User();
            u.setOpenid(openid);
            u.setNickname(nickname != null ? nickname : "小圆");
            u.setAvatarUrl(avatarUrl);
            return userRepository.save(u);
        });

        // session_key 每次登录都可能变化。未配置加密密钥时保留登录能力，虚拟支付接口会明确拒绝。
        if (sessionKey != null && !sessionKey.isBlank() && sessionKeyCipher.isConfigured()) {
            user.setSessionKeyEncrypted(sessionKeyCipher.encrypt(sessionKey));
            user = userRepository.save(user);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("openid", openid);
        result.put("sessionToken", userSessionService.issue(user));
        result.put("user", user);
        result.put("isNew", user.getCreatedAt() != null &&
            user.getCreatedAt().plusSeconds(5).isAfter(java.time.LocalDateTime.now()));
        return result;
    }
}
