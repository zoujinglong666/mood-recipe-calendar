package com.moodrecipe.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 菜名联网核验：用下厨房搜索页核对一道菜是否真实存在。
 * 只做「存在性」判断，不解析正文——解析 HTML 太脆，存在性已足够过滤 AI 编造的菜名。
 * 网络异常时按「通过」处理（放行），避免凌晨任务被外网抖动卡死；假菜名靠下一轮 LLM 出题再兜。
 */
@Service
public class DishNameVerifier {

    private static final Logger log = LoggerFactory.getLogger(DishNameVerifier.class);
    private static final String SEARCH_URL = "https://www.xiachufang.com/search/?keyword=";
    private static final Duration TIMEOUT = Duration.ofSeconds(8);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * @return true = 联网确认存在，或网络异常时放行；false = 下厨房明确查无此菜（判为编造，丢弃）。
     */
    public boolean isRealDish(String name) {
        String dish = name == null ? "" : name.trim();
        if (dish.isEmpty() || dish.length() > 30) return false;
        try {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(SEARCH_URL + URLEncoder.encode(dish, StandardCharsets.UTF_8)))
                    .timeout(TIMEOUT)
                    .header("User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124 Safari/537.36")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                // 反爬/限流等非 200：无法判断，放行（宁可多收一道可疑，也不让任务空转）
                return true;
            }
            String body = response.body();
            if (body.contains("没有找到") || body.contains("抱歉，没有")) {
                return false;
            }
            // 搜索结果正文里出现完整菜名，视为真实存在
            return body.contains(dish);
        } catch (Exception e) {
            log.warn("[菜名核验] 网络异常，放行 {}: {}", dish, e.getClass().getSimpleName());
            return true;
        }
    }
}
