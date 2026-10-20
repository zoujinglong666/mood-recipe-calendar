package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * WxPusher 管理员通知通道（wxpusher.zjiecode.com）。
 *
 * 为什么用 WxPusher 而不是小程序订阅消息：订阅消息每次都要用户先授权、且模板字段
 * 只能放 thing/date 等短文本，放不下去重报告这种长内容；WxPusher 管理员扫码关注后
 * 拿到 UID 即可推送 Markdown，正好承载「采集/去重报告 → 管理员处理」的闭环。
 *
 * 降级策略：未配置 appToken/UID 时报告原样打 INFO 日志——报告不丢，运维在日志里也能看，
 * 绝不因为通知通道缺失影响采集与去重主流程。
 */
@Service
public class WxPusherService {

    private static final Logger log = LoggerFactory.getLogger(WxPusherService.class);
    private static final String SEND_URL = "https://wxpusher.zjiecode.com/api/send/message";

    private final ObjectMapper json;
    private final boolean enabled;
    private final String appToken;
    private final List<String> adminUids;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public WxPusherService(ObjectMapper json,
                           @Value("${wxpusher.enabled:false}") boolean enabled,
                           @Value("${wxpusher.app-token:}") String appToken,
                           @Value("${wxpusher.admin-uids:}") String adminUids) {
        this.json = json;
        this.enabled = enabled;
        this.appToken = appToken == null ? "" : appToken.trim();
        this.adminUids = adminUids == null || adminUids.isBlank() ? List.of()
                : List.of(adminUids.split(",")).stream()
                        .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /** 通知通道是否真正可用（配置齐全）；否则 sendToAdmin 只落日志。 */
    public boolean isConfigured() {
        return enabled && !appToken.isEmpty() && !adminUids.isEmpty();
    }

    /**
     * 给管理员推送 Markdown 消息。WxPusher contentType=3 是 Markdown（支持标题/列表/加粗）。
     *
     * @param title    摘要（summary 字段，会话列表里显示的一句话）
     * @param markdown 正文（报告全文）
     * @return 是否推送成功；未配置或失败返回 false（均已降级为日志，调用方无需重试）
     */
    public boolean sendToAdmin(String title, String markdown) {
        if (!isConfigured()) {
            log.info("[管理员通知·未配置WxPusher，降级日志] {}\n{}", title, markdown);
            return false;
        }
        try {
            ObjectNode payload = json.createObjectNode();
            payload.put("appToken", appToken);
            payload.put("content", markdown);
            payload.put("summary", title.length() > 99 ? title.substring(0, 99) : title);
            payload.put("contentType", 3);
            ArrayNode uids = payload.putArray("uids");
            adminUids.forEach(uids::add);
            HttpRequest request = HttpRequest.newBuilder(URI.create(SEND_URL))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body = json.readTree(resp.body());
            if (resp.statusCode() == 200 && body.path("code").asInt() == 1000) {
                log.info("WxPusher 管理员通知已送达: {}", title);
                return true;
            }
            log.warn("WxPusher 推送失败: http={} body={}", resp.statusCode(), resp.body());
            return false;
        } catch (Exception e) {
            log.warn("WxPusher 推送异常: {}", e.getMessage());
            return false;
        }
    }
}
