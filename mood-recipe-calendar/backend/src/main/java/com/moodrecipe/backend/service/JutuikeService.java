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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        // 去掉 .env 复制时可能带入的尾部空白/换行，避免鉴权失败（聚推客 code!=1 → 静默空数据）
        this.apiKey = apiKey == null ? "" : apiKey.trim();
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
     * [2026-10-30] 分组口径修正：act_list 不认「按中文 cate_name 请求过滤」，
     * 分类是返回数据里每个活动自带的字段。旧逻辑按猜测的 cate_name 当参数拉 → 每类都空 → data:[]。
     * 改为单次拉全量、本地按真实 cate_name 分组（展示顺序即接口返回顺序）。
     */
    public List<DealGroup> groupedActivities() {
        if (!isEnabled()) return List.of();
        CachedGroups cached = groupsCache.get();
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt() < LIST_CACHE_MS) {
            return cached.groups();
        }
        // 单次拉全量后在本地按 cate_name 分组；单个请求失败 → 返回空（已在 fetchAndGroup 内记日志）。
        List<DealGroup> groups = fetchAndGroup();
        if (!groups.isEmpty()) groupsCache.set(new CachedGroups(System.currentTimeMillis(), groups));
        return groups.isEmpty() && cached != null ? cached.groups() : groups;
    }

    /**
     * [2026-10-30] 单次拉全量活动列表，按返回数据的 cate_name 在本地分组。
     * 不再用 cate_name 当请求参数（接口不支持按中文分类过滤），避免每类都返回空。
     * 任何失败（网络异常 / code!=1）都记日志后返回空，便于在日志里区分「apikey 无效」与「账号未激活」。
     */
    private List<DealGroup> fetchAndGroup() {
        Map<String, List<DealActivity>> byCat = new LinkedHashMap<>();
        try {
            int page = 1;
            while (page <= 10) {
                String body = httpGet(baseUrl + "/union/act_list?apikey=" + enc(apiKey)
                        + "&page=" + page + "&pageSize=100");
                JsonNode root = objectMapper.readTree(body);
                if (root.path("code").asInt(0) != 1) {
                    log.warn("聚推客活动列表返回失败 code={} msg={}",
                            root.path("code").asInt(0), root.path("msg").asText(""));
                    return List.of();
                }
                JsonNode rows = root.path("data").path("data");
                if (!rows.isArray() || rows.isEmpty()) break;
                for (JsonNode row : rows) {
                    long actId = row.path("act_id").asLong(0);
                    String name = row.path("act_name").asText("").trim();
                    if (actId <= 0 || name.isEmpty()) continue;
                    String cat = row.path("cate_name").asText("").trim();
                    if (cat.isEmpty()) cat = "其他";
                    byCat.computeIfAbsent(cat, k -> new ArrayList<>())
                            .add(new DealActivity(actId, name,
                                    row.path("desc").asText("").trim(),
                                    row.path("icon").asText("").trim()));
                }
                JsonNode data = root.path("data");
                int total = data.path("total").asInt(0);
                int current = data.path("current_page").asInt(page);
                int pageSize = data.path("per_page").asInt(100);
                if (total > 0 && current * pageSize >= total) break;
                page++;
            }
        } catch (Exception exception) {
            log.warn("聚推客活动列表请求失败: {}", exception.getClass().getSimpleName());
            return List.of();
        }
        if (byCat.isEmpty()) {
            log.warn("聚推客活动列表返回成功但无活动数据：请确认 apikey 是否有效、聚推客账号是否已通过审核/激活");
            return List.of();
        }
        List<DealGroup> groups = new ArrayList<>();
        byCat.forEach((cat, list) -> groups.add(new DealGroup(cat, list)));
        return groups;
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
