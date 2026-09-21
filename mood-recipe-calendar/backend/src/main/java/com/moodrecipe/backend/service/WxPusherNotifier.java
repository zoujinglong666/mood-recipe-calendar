package com.moodrecipe.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * 把关键日志推送到微信（WxPusher）。
 * 使用简化发送端点：https://wxpusher.zjiecode.com/api/send/message/{topicToken}/{URL编码消息}
 * 该端点不需要 appToken，topicToken 本身已绑定应用。
 */
@Service
public class WxPusherNotifier {

    private static final Logger log = LoggerFactory.getLogger(WxPusherNotifier.class);
    private static final String ENDPOINT = "https://wxpusher.zjiecode.com/api/send/message/";

    @Value("${wxpusher.topic-token:}")
    private String topicToken;

    @Value("${wxpusher.enabled:false}")
    private boolean enabled;

    private final RestTemplate restTemplate;

    public WxPusherNotifier() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 异步推送一条消息到微信。任何异常都被吞掉，绝不影响主业务流程。
     */
    public void send(String content) {
        if (!enabled || topicToken == null || topicToken.isEmpty()) {
            return;
        }
        final String safeTopic = topicToken;
        CompletableFuture.runAsync(() -> {
            try {
                // 去掉会破坏 URL 的换行/制表符，统一成单行
                String safe = (content == null ? "" : content)
                        .replace("\r", " ")
                        .replace("\n", " / ")
                        .replace("\t", " ");
                String url = ENDPOINT + safeTopic + "/" + URLEncoder.encode(safe, StandardCharsets.UTF_8);
                restTemplate.getForObject(url, String.class);
            } catch (Exception e) {
                log.warn("[WxPusher] 推送失败(不影响主流程): {}", e.getMessage());
            }
        });
    }
}
