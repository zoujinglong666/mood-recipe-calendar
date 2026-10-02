package com.moodrecipe.backend.service;

import com.moodrecipe.backend.config.AppClock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** 一次性订阅消息；发送失败绝不影响用户刚完成的服务。 */
@Service
public class WechatSubscriptionMessageService {
    private static final Logger log = LoggerFactory.getLogger(WechatSubscriptionMessageService.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final String appid;
    private final String secret;
    private final String templateId;
    /** 冰箱临期提醒专用模板（需在微信后台单独申请后配置）。 */
    private final String fridgeTemplateId;
    private final String state;
    private volatile String token = "";
    private volatile long tokenExpiresAt;

    public WechatSubscriptionMessageService(ObjectMapper json,
                                            @Value("${wechat.appid:}") String appid,
                                            @Value("${wechat.secret:}") String secret,
                                            @Value("${wechat.subscription.template-id:}") String templateId,
                                            @Value("${wechat.subscription.fridge-template-id:}") String fridgeTemplateId,
                                            @Value("${wechat.subscription.miniprogram-state:formal}") String state) {
        this.json = json;
        this.appid = appid;
        this.secret = secret;
        this.templateId = templateId;
        this.fridgeTemplateId = fridgeTemplateId == null ? "" : fridgeTemplateId.trim();
        this.state = state;
    }

    /**
     * 食材临期提醒（会员专享能力，调用方需先校验会员）。
     *
     * @param expiringNames 临期/过期食材名（已用「、」拼接）
     * @param daysLeft      最短剩余天数
     */
    public void sendFridgeExpiring(String openid, String expiringNames, int daysLeft) {
        if (openid == null || openid.isBlank() || fridgeTemplateId.isBlank()) return;
        try {
            String accessToken = accessToken();
            if (accessToken.isBlank()) return;
            String summary = expiringNames == null || expiringNames.isBlank() ? "有食材快过期了" : expiringNames;
            // 模板字段需与微信后台申请的字段一致，此处按通用「食材/时间/提示」字段发送
            Map<String, Object> body = Map.of(
                    "touser", openid,
                    "template_id", fridgeTemplateId,
                    "page", "pages/fridge/index",
                    "miniprogram_state", state,
                    "lang", "zh_CN",
                    "data", Map.of(
                            "thing1", Map.of("value", truncate(summary, 20)),
                            "number2", Map.of("value", String.valueOf(Math.max(daysLeft, 0))),
                            "time3", Map.of("value", TIME.format(AppClock.now()))
                    )
            );
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=" + accessToken))
                    .timeout(Duration.ofSeconds(12)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            JsonNode response = json.readTree(http.send(request, HttpResponse.BodyHandlers.ofString()).body());
            if (response.path("errcode").asInt(-1) != 0) log.info("临期提醒未发送: errcode={}", response.path("errcode").asInt());
        } catch (Exception error) {
            log.info("临期提醒发送失败: {}", error.getClass().getSimpleName());
        }
    }

    /** 微信模板字段有长度限制，超长会被拒。 */
    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    public void sendWeeklyPlanCompleted(String openid, Long planId) {
        if (openid == null || openid.isBlank() || templateId.isBlank()) return;
        try {
            String accessToken = accessToken();
            if (accessToken.isBlank()) return;
            Map<String, Object> body = Map.of(
                    "touser", openid,
                    "template_id", templateId,
                    "page", "pages/weekly-plan/detail?id=" + planId,
                    "miniprogram_state", state,
                    "lang", "zh_CN",
                    "data", Map.of(
                            "time3", Map.of("value", TIME.format(AppClock.now())),
                            "thing4", Map.of("value", "本周晚餐已安排好，点此查看")
                    )
            );
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=" + accessToken))
                    .timeout(Duration.ofSeconds(12)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            JsonNode response = json.readTree(http.send(request, HttpResponse.BodyHandlers.ofString()).body());
            if (response.path("errcode").asInt(-1) != 0) log.info("订阅消息未发送: errcode={}", response.path("errcode").asInt());
        } catch (Exception error) {
            log.info("订阅消息发送失败: {}", error.getClass().getSimpleName());
        }
    }

    private String accessToken() throws Exception {
        if (System.currentTimeMillis() < tokenExpiresAt) return token;
        if (appid.isBlank() || secret.isBlank()) return "";
        synchronized (this) {
            if (System.currentTimeMillis() < tokenExpiresAt) return token;
            URI uri = URI.create("https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid="
                    + URLEncoder.encode(appid, StandardCharsets.UTF_8) + "&secret=" + URLEncoder.encode(secret, StandardCharsets.UTF_8));
            JsonNode response = json.readTree(http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(12)).GET().build(), HttpResponse.BodyHandlers.ofString()).body());
            String next = response.path("access_token").asText();
            if (next.isBlank()) return "";
            token = next;
            tokenExpiresAt = System.currentTimeMillis() + Math.max(60, response.path("expires_in").asLong(7200) - 120) * 1000;
            return token;
        }
    }
}
