package com.moodrecipe.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 把关键日志推送到微信（WxPusher）。
 * 使用简化发送端点：https://wxpusher.zjiecode.com/api/send/message/{topicToken}/{URL编码消息}
 * 该端点不需要 appToken，topicToken 本身已绑定应用。
 */
@Service
public class WxPusherNotifier {

    private static final Logger log = LoggerFactory.getLogger(WxPusherNotifier.class);
    private static final String ENDPOINT = "https://wxpusher.zjiecode.com/api/send/message/";

    /**
     * 专用守护线程池：把阻塞式 HTTP 推送与公共 ForkJoinPool 隔离。
     * 固定 2 个工作线程 + 无界队列，足够应对偶发的登录/告警推送，
     * 既不会因 WxPusher 响应慢而拖垮业务线程，也不会被别的异步任务挤掉执行时机。
     */
    private final ExecutorService notifyExecutor = Executors.newFixedThreadPool(2, new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "wxpusher-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    });

    @Value("${wxpusher.topic-token:}")
    private String topicToken;

    @Value("${wxpusher.enabled:true}")
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
            // 使用专用线程池 notifyExecutor，避免阻塞公共 ForkJoinPool
            try {
                // 去掉会破坏 URL 的换行/制表符，统一成单行
                String safe = (content == null ? "" : content)
                        .replace("\r", " ")
                        .replace("\n", " / ")
                        .replace("\t", " ");
                // 手动 URL 编码一次；把空格的 '+' 换成 '%20'，避免路径段里 '+' 不被还原
                String encoded = URLEncoder.encode(safe, StandardCharsets.UTF_8).replace("+", "%20");
                // 用 URI.create 构造，交给 RestTemplate 直接发送，避免 Spring 对 '%' 二次编码导致双重编码乱码
                URI uri = URI.create(ENDPOINT + safeTopic + "/" + encoded);
                restTemplate.getForObject(uri, String.class);
            } catch (Exception e) {
                log.warn("[WxPusher] 推送失败(不影响主流程): {}", e.getMessage());
            }
        }, notifyExecutor);
    }
}
