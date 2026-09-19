package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** 微信内容安全统一入口；生产环境服务异常时拒绝写入。 */
@Service
public class WechatContentSafetyService {
    private static final Logger log = LoggerFactory.getLogger(WechatContentSafetyService.class);
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String appid;
    private final String secret;
    private final boolean enabled;
    private final boolean failClosed;
    private volatile String accessToken = "";
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    public WechatContentSafetyService(ObjectMapper json,
                                      @Value("${wechat.appid:}") String appid,
                                      @Value("${wechat.secret:}") String secret,
                                      @Value("${wechat.content-safety.enabled:false}") boolean enabled,
                                      @Value("${wechat.content-safety.fail-closed:false}") boolean failClosed) {
        this.json = json;
        this.appid = appid;
        this.secret = secret;
        this.enabled = enabled;
        this.failClosed = failClosed;
    }

    public boolean allowsText(String openid, String... values) {
        String content = String.join(" ", values == null ? new String[0] : values).trim();
        if (content.isBlank() || !enabled) return true;
        try {
            String body = json.writeValueAsString(Map.of(
                    "content", content.substring(0, Math.min(content.length(), 2500)),
                    "version", 2, "scene", 2, "openid", openid));
            JsonNode response = postJson("https://api.weixin.qq.com/wxa/msg_sec_check?access_token=" + token(), body);
            return response.path("errcode").asInt(-1) == 0
                    && "pass".equals(response.path("result").path("suggest").asText());
        } catch (Exception exception) {
            log.warn("微信文本安全检查不可用: {}", exception.toString());
            return !failClosed;
        }
    }

    public boolean allowsImage(byte[] data, String contentType) {
        if (!enabled) return true;
        try {
            String boundary = "----Guozai" + UUID.randomUUID().toString().replace("-", "");
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"media\"; filename=\"upload\"\r\n"
                    + "Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(data);
            body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                            "https://api.weixin.qq.com/wxa/img_sec_check?access_token=" + token()))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
            JsonNode response = json.readTree(http.send(request, HttpResponse.BodyHandlers.ofString()).body());
            return response.path("errcode").asInt(-1) == 0;
        } catch (Exception exception) {
            log.warn("微信图片安全检查不可用: {}", exception.toString());
            return !failClosed;
        }
    }

    private JsonNode postJson(String url, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return json.readTree(http.send(request, HttpResponse.BodyHandlers.ofString()).body());
    }

    private synchronized String token() throws Exception {
        if (!accessToken.isBlank() && Instant.now().isBefore(tokenExpiresAt)) return accessToken;
        if (appid.isBlank() || secret.isBlank()) throw new IllegalStateException("微信内容安全配置缺失");
        URI uri = URI.create("https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid="
                + java.net.URLEncoder.encode(appid, StandardCharsets.UTF_8) + "&secret="
                + java.net.URLEncoder.encode(secret, StandardCharsets.UTF_8));
        JsonNode response = json.readTree(http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofString()).body());
        accessToken = response.path("access_token").asText();
        if (accessToken.isBlank()) throw new IllegalStateException("获取微信 access_token 失败");
        tokenExpiresAt = Instant.now().plusSeconds(Math.max(60, response.path("expires_in").asLong(7200) - 300));
        return accessToken;
    }
}
