package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.entity.User;
import com.moodrecipe.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final WxPusherNotifier wxPusherNotifier;
    private static final Logger log = LoggerFactory.getLogger(WechatService.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${wechat.appid:}")
    private String appid;

    @Value("${wechat.secret:}")
    private String secret;

    public WechatService(UserRepository userRepository, SessionKeyCipher sessionKeyCipher,
                         UserSessionService userSessionService, WxPusherNotifier wxPusherNotifier) {
        this.userRepository = userRepository;
        this.sessionKeyCipher = sessionKeyCipher;
        this.userSessionService = userSessionService;
        this.wxPusherNotifier = wxPusherNotifier;
    }

    /**
     * 微信登录：用 code 换 openid，然后创建/更新用户
     * 仅传 code；昵称/头像通过 PUT /api/auth/user 单独编辑
     */
    public Map<String, Object> login(String code) {
        // —— 配置自检日志（绝不打印 secret 明文）——
        boolean secretConfigured = secret != null && !secret.isEmpty();
        boolean appidConfigured = appid != null && !appid.isEmpty();
        String maskedSecret = secretConfigured
            ? (secret.length() <= 4 ? "****" : secret.substring(0, 4) + "****(len=" + secret.length() + ")")
            : "(空)";
        log.info("[微信登录] 入参 code长度={}; 配置 appid={}, appid已配置={}, secret已配置={}, secret前缀={}",
                (code == null ? "null" : code.length()), appid, appidConfigured, secretConfigured, maskedSecret);

        if (!appidConfigured || !secretConfigured) {
            String msg = String.format("[微信登录] 配置缺失 appid=%s appid已配置=%s secret已配置=%s",
                    appid, appidConfigured, secretConfigured);
            log.error(msg);
            wxPusherNotifier.send(msg);
            throw new RuntimeException("微信登录配置缺失：appid / secret 未配置");
        }
        if (code == null || code.isBlank()) {
            throw new RuntimeException("微信登录失败：code 不能为空");
        }

        // 真实微信登录：code2session 换 openid
        String url = String.format(
            "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
            appid, secret, code
        );
        String openid;
        String sessionKey = null;
        try {
            // 打印请求地址但隐去 secret，避免密钥泄露到日志
            log.info("[微信登录] 调用微信 code2session, appid={}, url(已隐去secret)={}",
                    appid, url.replace(secret, "***SECRET***"));
            String response = restTemplate.getForObject(url, String.class);
            // 隐去 session_key 后打印微信原始响应，便于排查 errcode/errmsg
            String safeResponse = response == null ? "null"
                : response.replaceAll("\"session_key\"\\s*:\\s*\"[^\"]*\"", "\"session_key\":\"***MASKED***\"");
            log.info("[微信登录] 微信原始响应: {}", safeResponse);

            JsonNode root = objectMapper.readTree(response);
            if (root.has("errcode") && root.get("errcode").asInt() != 0) {
                String errcode = root.path("errcode").asText();
                String errmsg = root.path("errmsg").asText();
                String msg = String.format("[微信登录] 微信返回错误 errcode=%s errmsg=%s appid=%s secret已配置=%s",
                        errcode, errmsg, appid, secretConfigured);
                log.error(msg);
                wxPusherNotifier.send(msg);
                throw new RuntimeException("微信登录失败: " + errmsg);
            }
            openid = root.path("openid").asText();
            if (openid == null || openid.isEmpty()) {
                throw new RuntimeException("微信登录失败: 未获取到 openid");
            }
            sessionKey = root.path("session_key").asText(null);
            String okMsg = String.format("[微信登录] 成功 appid=%s openid=%s", appid, openid);
            log.info("[微信登录] code2session 成功, openid={}, 是否含session_key={}", openid, sessionKey != null);
            wxPusherNotifier.send(okMsg);
        } catch (RuntimeException e) {
            wxPusherNotifier.send("[微信登录] 失败 appid=" + appid + " 错误=" + e.getMessage());
            throw e;
        } catch (Exception e) {
            String msg = "[微信登录] 请求微信异常: " + e.getMessage();
            log.error(msg, e);
            wxPusherNotifier.send(msg);
            throw new RuntimeException("微信登录请求失败: " + e.getMessage());
        }

        // 查找或创建用户（新用户默认昵称"小圆"+4位随机码，头像为空）
        User user = userRepository.findByOpenid(openid).orElseGet(() -> {
            User u = new User();
            u.setOpenid(openid);
            String suffix = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 4);
            u.setNickname("小圆" + suffix);
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
