package com.moodrecipe.backend.service;

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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * [2026-10-28] 聚推客联盟（jutuike.com）CPS 对接：活动列表 + 统一转链。
 *
 * 文档：https://www.jutuike.com/doc/34（活动列表）、/doc/48（统一转链 /union/act）。
 * 鉴权是单 apikey（登录个人中心获取），必须在服务端保管、绝不下发前端；
 * sid 是跟单参数——这里用 openid 的 SHA-256 前 16 位 hex，佣金归因可用且不泄露原始身份。
 *
 * 当前小程序是个人主体，微信禁止跳第三方小程序，we_app_info 暂时用不上；
 * 但转链仍完整返回 h5/we_app_info，升级企业主体后前端可直接切 navigateToMiniProgram。
 */
@Service
public class JutuikeService {

    private static final Logger log = LoggerFactory.getLogger(JutuikeService.class);
    /** 活动列表缓存 30 分钟：act_list 是分页大响应，且活动增减极低频。 */
    private static final long LIST_CACHE_MS = 30 * 60 * 1000L;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String baseUrl;

    private final AtomicReference<CachedGroups> groupsCache = new AtomicReference<>();

    public JutuikeService(
            ObjectMapper objectMapper,
            @Value("${cps.jutuike.api-key:}") String apiKey,
            @Value("${cps.jutuike.base-url:http://api.jutuike.com}") String baseUrl
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** apikey 未配置时整个优惠功能静默降级为不可用，不报错。 */
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    // ==== 输出结构 ====

    public record DealActivity(long actId, String name, String desc, String icon) { }

    /** 按分类分组的活动列表（固定顺序，空分类不返回）。 */
    public record DealGroup(String category, List<DealActivity> activities) { }

    /**
     * 转链结果。官方明确 h5/we_app_info/long_h5 都是「部分活动没有」，必须判空。
     */
    public record DealLink(String actName, String h5, String longH5, String miniAppId, String miniPath) { }

    private record CachedGroups(long fetchedAt, List<DealGroup> groups) { }

    /**
     * [2026-10-28] 分组口径：act_list 接口本身不返回分类字段，只能按 cate_name 筛选拉取。
     * 这组分类对应「吃饭的人顺路会用」的场景，顺序即展示顺序；漏拉只影响分组展示，不影响转链。
     */
    private static final List<String> GROUP_CATEGORIES = List.of(
            "美团", "饿了么", "连锁餐饮", "打车出行", "电影票", "特惠酒店", "快递优惠");

    /**
     * 按分类分组的活动列表（30 分钟缓存，分类间并行拉取）。
     * 单个分类失败跳过该分类，不影响其它分组——优惠是附加能力，不阻塞主流程。
     */
    public List<DealGroup> groupedActivities() {
        if (!isEnabled()) return List.of();
        CachedGroups cached = groupsCache.get();
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt() < LIST_CACHE_MS) {
            return cached.groups();
        }
        List<DealGroup> groups = GROUP_CATEGORIES.parallelStream()
                .map(this::activitiesOfCategory)
                .filter(group -> !group.activities().isEmpty())
                .toList();
        if (!groups.isEmpty()) groupsCache.set(new CachedGroups(System.currentTimeMillis(), groups));
        return groups.isEmpty() && cached != null ? cached.groups() : groups;
    }

    private DealGroup activitiesOfCategory(String category) {
        try {
            String body = httpGet(baseUrl + "/union/act_list?apikey=" + enc(apiKey)
                    + "&pageSize=100&cate_name=" + enc(category));
            return new DealGroup(category, parseActivities(body));
        } catch (Exception exception) {
            log.warn("聚推客活动列表请求失败 cate={}: {}", category, exception.getClass().getSimpleName());
            return new DealGroup(category, List.of());
        }
    }

    /**
     * 统一转链。sid 为跟单标识（openid 哈希），订单归因靠它。
     */
    public Optional<DealLink> link(long actId, String sid) {
        if (!isEnabled()) return Optional.empty();
        try {
            String body = httpGet(baseUrl + "/union/act?apikey=" + enc(apiKey)
                    + "&sid=" + enc(sid) + "&act_id=" + actId);
            JsonNode root = objectMapper.readTree(body);
            if (root.path("code").asInt(0) != 1) {
                log.warn("聚推客转链失败 act_id={} msg={}", actId, root.path("msg").asText(""));
                return Optional.empty();
            }
            JsonNode data = root.path("data");
            JsonNode weApp = data.path("we_app_info");
            String h5 = data.path("h5").asText("").trim();
            String longH5 = data.path("long_h5").asText("").trim();
            String actName = data.path("act_name").asText("").trim();
            if (h5.isEmpty() && longH5.isEmpty() && weApp.isMissingNode()) {
                log.warn("聚推客转链无可用链接 act_id={}", actId);
                return Optional.empty();
            }
            return Optional.of(new DealLink(
                    actName, h5, longH5,
                    weApp.path("app_id").asText("").trim(),
                    weApp.path("page_path").asText("").trim()));
        } catch (Exception exception) {
            log.warn("聚推客转链请求失败 act_id={}: {}", actId, exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    // ==== 内部 ====

    private List<DealActivity> parseActivities(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        if (root.path("code").asInt(0) != 1) return List.of();
        JsonNode rows = root.path("data").path("data");
        List<DealActivity> list = new ArrayList<>();
        if (!rows.isArray()) return list;
        for (JsonNode row : rows) {
            long actId = row.path("act_id").asLong(0);
            String name = row.path("act_name").asText("").trim();
            if (actId <= 0 || name.isEmpty()) continue;
            list.add(new DealActivity(actId, name,
                    row.path("desc").asText("").trim(),
                    row.path("icon").asText("").trim()));
        }
        return list;
    }

    private String httpGet(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return response.body();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
