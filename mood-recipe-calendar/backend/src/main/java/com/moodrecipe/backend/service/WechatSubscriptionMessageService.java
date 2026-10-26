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
    /** 「日程提醒」模板 1677（thing3=提醒事项 thing10=类型 thing9=备注），周六清单与沉睡召回共用。 */
    private final String scheduleTemplateId;
    /** 「开通会员成功通知」模板 738（amount2/thing1/date3/thing8），虚拟支付发货成功后发送。 */
    private final String memberTemplateId;
    private final String state;
    private volatile String token = "";
    private volatile long tokenExpiresAt;

    public WechatSubscriptionMessageService(ObjectMapper json,
                                            @Value("${wechat.appid:}") String appid,
                                            @Value("${wechat.secret:}") String secret,
                                            @Value("${wechat.subscription.template-id:}") String templateId,
                                            @Value("${wechat.subscription.fridge-template-id:}") String fridgeTemplateId,
                                            @Value("${wechat.subscription.schedule-template-id:}") String scheduleTemplateId,
                                            @Value("${wechat.subscription.member-template-id:}") String memberTemplateId,
                                            @Value("${wechat.subscription.miniprogram-state:formal}") String state) {
        this.json = json;
        this.appid = appid;
        this.secret = secret;
        this.templateId = templateId;
        this.fridgeTemplateId = fridgeTemplateId == null ? "" : fridgeTemplateId.trim();
        this.scheduleTemplateId = scheduleTemplateId == null ? "" : scheduleTemplateId.trim();
        this.memberTemplateId = memberTemplateId == null ? "" : memberTemplateId.trim();
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
                    "page", "subpkg-settings/fridge/index",
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
                    "page", "subpkg-planning/weekly-plan/detail?id=" + planId,
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

    /**
     * 周六上午「买菜清单已备好」：发给最近一周生成过周菜单的用户。
     * 订阅消息是一次性授权：用户在生成周菜单时勾选「日程提醒」授权后才能送达。
     */
    public void sendShoppingListReady(String openid) {
        sendScheduleReminder(openid, "subpkg-planning/weekly-plan/index",
                "本周买菜清单已备好", "采购提醒", "周六采购正合适，照着清单一次买齐");
    }

    /** 沉睡召回：连续 2 天未打开时提示今日菜牌已留好。 */
    public void sendMissYou(String openid) {
        sendScheduleReminder(openid, "pages/index/index",
                "今天的菜已留好", "锅仔召回", "锅仔想你了，点开看看今日推荐");
    }

    /** 「日程提醒」模板（thing3=提醒事项 thing10=类型 thing9=备注）；模板未配置直接跳过。 */
    private void sendScheduleReminder(String openid, String page, String thing3, String thing10, String thing9) {
        if (openid == null || openid.isBlank() || scheduleTemplateId.isBlank()) return;
        try {
            String accessToken = accessToken();
            if (accessToken.isBlank()) return;
            Map<String, Object> body = Map.of(
                    "touser", openid,
                    "template_id", scheduleTemplateId,
                    "page", page,
                    "miniprogram_state", state,
                    "lang", "zh_CN",
                    "data", Map.of(
                            "thing3", Map.of("value", truncate(thing3, 20)),
                            "thing10", Map.of("value", truncate(thing10, 20)),
                            "thing9", Map.of("value", truncate(thing9, 20))
                    )
            );
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=" + accessToken))
                    .timeout(Duration.ofSeconds(12)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            JsonNode response = json.readTree(http.send(request, HttpResponse.BodyHandlers.ofString()).body());
            if (response.path("errcode").asInt(-1) != 0) log.info("日程提醒未发送: openid={} errcode={}", openid, response.path("errcode").asInt());
        } catch (Exception error) {
            log.info("日程提醒发送失败: {}", error.getClass().getSimpleName());
        }
    }

    /**
     * 开通会员成功通知（模板 738：amount2=支付金额 thing1=会员名称 date3=到期时间 thing8=会员类型）。
     * amount 为微信 amount 类型（纯数字元，可两位小数）；date 支持「yyyy年M月d日」。
     * 用户在会员页支付前请求过授权才能送达；模板未配置直接跳过。
     */
    public void sendMemberActivated(String openid, String memberName, String amountYuan,
                                    LocalDateTime expireAt, String memberType) {
        if (openid == null || openid.isBlank() || memberTemplateId.isBlank()) return;
        try {
            String accessToken = accessToken();
            if (accessToken.isBlank()) return;
            DateTimeFormatter date = DateTimeFormatter.ofPattern("yyyy年M月d日");
            Map<String, Object> body = Map.of(
                    "touser", openid,
                    "template_id", memberTemplateId,
                    "page", "subpkg-settings/membership/index",
                    "miniprogram_state", state,
                    "lang", "zh_CN",
                    "data", Map.of(
                            "amount2", Map.of("value", truncate(amountYuan == null ? "0" : amountYuan, 10)),
                            "thing1", Map.of("value", truncate(memberName, 20)),
                            "date3", Map.of("value", expireAt == null ? "长期有效" : date.format(expireAt)),
                            "thing8", Map.of("value", truncate(memberType, 20))
                    )
            );
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=" + accessToken))
                    .timeout(Duration.ofSeconds(12)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            JsonNode response = json.readTree(http.send(request, HttpResponse.BodyHandlers.ofString()).body());
            if (response.path("errcode").asInt(-1) != 0) log.info("会员开通通知未发送: openid={} errcode={}", openid, response.path("errcode").asInt());
        } catch (Exception error) {
            log.info("会员开通通知发送失败: {}", error.getClass().getSimpleName());
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
