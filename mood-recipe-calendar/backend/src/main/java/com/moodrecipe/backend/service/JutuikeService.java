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
 * 文档：https://www.jutuike.com/doc/34（活动列表 act_list）、/doc/48（统一转链 act）。
 * 鉴权仅单 apikey（登录个人中心获取，无 sign 签名），必须在服务端保管、绝不下发前端；
 * sid 是跟单参数——用 openid 的 SHA-256 前 16 位 hex，佣金归因可用且不泄露原始身份。
 *
 * 返回字段口径（doc/48）：h5 / long_h5 / we_app_info 都是「部分活动没有，需判断」。
 * 个人主体小程序可走 wx.navigateToMiniProgram 直跳官方小程序（前端 openDeal 已实现），
 * 无小程序信息或拒跳时降级复制 h5；miniCode 为小程序码图片，可作二维码兜底。
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
     * 转链结果。官方明确 h5/long_h5/we_app_info/miniCode 都是「部分活动没有」，必须判空。
     * miniCode 为小程序码图片，可作「仅小程序、无 h5」活动的二维码兜底。
     */
    public record DealLink(String actName, String h5, String longH5,
                           String miniAppId, String miniPath, String miniCode) { }

    private record CachedGroups(long fetchedAt, List<DealGroup> groups) { }

    /**
     * [2026-10-30] 分组口径（依据聚推客 act_list 文档）：cate_name 是官方支持的过滤参数，
     * 取值固定为「美团/饿了么/打车出行/特惠酒店/电影票/快递优惠/连锁餐饮/本地生活/电商/京东外卖」。
     * 关键点：响应体【不含】cate_name 字段，所以分类只能靠「按 cate_name 分次拉取、请求的分类即分组名」得到。
     * 这里取与「吃饭的人顺路会用」最贴合的子集（值与前端 GROUP_ICONS 的 key 对齐，图标可直接命中）。
     * 注：先前返回空的根因是 apikey 鉴权（携带尾部空白 → code!=1 被静默吞），已由构造器 trim 修复；
     * 此处每个分类请求独立失败只跳过该组并记日志，不阻塞其它分组。
     */
    private static final List<String> GROUP_CATEGORIES = List.of(
            "美团", "饿了么", "连锁餐饮", "打车出行", "电影票", "特惠酒店", "快递优惠", "本地生活", "京东外卖");

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

    /** 单个分类：按官方 cate_name 过滤拉取，请求的分类名即分组名。 */
    private DealGroup activitiesOfCategory(String category) {
        try {
            String body = httpGet(baseUrl + "/union/act_list?apikey=" + enc(apiKey)
                    + "&pageSize=100&cate_name=" + enc(category));
            return new DealGroup(category, parseActivities(body, category));
        } catch (Exception exception) {
            log.warn("聚推客活动列表请求失败 cate={}: {}", category, exception.getClass().getSimpleName());
            return new DealGroup(category, List.of());
        }
    }

    /** 解析单分类响应。code!=1（apikey 无效/账号未激活）记日志后返回空，避免静默丢数据。 */
    private List<DealActivity> parseActivities(String body, String category) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        if (root.path("code").asInt(0) != 1) {
            log.warn("聚推客活动列表返回失败 cate={} code={} msg={}",
                    category, root.path("code").asInt(0), root.path("msg").asText(""));
            return List.of();
        }
        JsonNode rows = root.path("data").path("data");
        List<DealActivity> list = new ArrayList<>();
        if (!rows.isArray()) {
            log.warn("聚推客活动列表 cate={} 的 data.data 非数组", category);
            return list;
        }
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
            String miniCode = weApp.path("miniCode").asText("").trim();
            // h5 / long_h5 / we_app_info / miniCode 任一可用即视为有效（部分活动只有其中一种）
            if (h5.isEmpty() && longH5.isEmpty() && miniCode.isEmpty() && weApp.isMissingNode()) {
                log.warn("聚推客转链无可用链接 act_id={}", actId);
                return Optional.empty();
            }
            return Optional.of(new DealLink(
                    actName, h5, longH5,
                    weApp.path("app_id").asText("").trim(),
                    weApp.path("page_path").asText("").trim(),
                    miniCode));
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
