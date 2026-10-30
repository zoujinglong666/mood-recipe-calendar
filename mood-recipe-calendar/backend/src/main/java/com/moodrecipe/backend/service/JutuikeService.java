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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
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
    private final WxPusherNotifier notifier;

    /** 优惠列表拉空告警节流：冷却期内（30 分钟）最多推一次，避免每个请求都推。 */
    private static final long EMPTY_ALERT_COOLDOWN_MS = 30 * 60 * 1000L;
    private final AtomicLong lastEmptyAlertAt = new AtomicLong(0);

    private final AtomicReference<CachedGroups> groupsCache = new AtomicReference<>();

    public JutuikeService(
            ObjectMapper objectMapper,
            WxPusherNotifier notifier,
            @Value("${cps.jutuike.api-key:}") String apiKey,
            @Value("${cps.jutuike.base-url:http://api.jutuike.com}") String baseUrl
    ) {
        this.objectMapper = objectMapper;
        this.notifier = notifier;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        // 去掉 .env 复制时可能带入的尾部空白/换行，避免鉴权失败（聚推客 code!=1 → 静默空数据）
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        // 启动即确认 key 真的注入了（非空白），并暴露长度便于发现「复制时被截断」的 key；
        // 不打印 key 原文，避免泄露。
        if (isEnabled()) log.info("聚推客 CPS 已启用，apikey 长度={}", apiKey.length());
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
        // 顺序拉取（9 个分类、请求很快），同时收集每类失败原因用于空数据告警
        List<CatResult> results = GROUP_CATEGORIES.stream()
                .map(this::activitiesOfCategory)
                .toList();
        List<DealGroup> groups = results.stream()
                .filter(r -> !r.activities().isEmpty())
                .map(r -> new DealGroup(r.category(), r.activities()))
                .toList();
        if (!groups.isEmpty()) {
            groupsCache.set(new CachedGroups(System.currentTimeMillis(), groups));
        } else if (cached == null) {
            // 首次拉取（无旧缓存兜底）就空 → 推 WxPusher 告警并附各分类失败原因，便于排查
            alertEmpty(results);
        }
        return groups.isEmpty() && cached != null ? cached.groups() : groups;
    }

    /** 单分类拉取结果：成功带活动列表，失败带原因（供空数据时推 WxPusher 排查）。 */
    private record CatResult(String category, List<DealActivity> activities, String error) { }

    /** 单个分类：按官方 cate_name 过滤拉取，请求的分类名即分组名。 */
    private CatResult activitiesOfCategory(String category) {
        try {
            String body = httpGet(baseUrl + "/union/act_list?apikey=" + enc(apiKey)
                    + "&pageSize=100&cate_name=" + enc(category));
            return new CatResult(category, parseActivities(body, category), null);
        } catch (Exception exception) {
            String reason = exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage();
            log.warn("聚推客活动列表请求失败 cate={}: {}", category, reason);
            return new CatResult(category, List.of(), category + " → " + reason);
        }
    }

    /** 解析单分类响应。code!=1（apikey 无效/账号未激活）或结构异常时抛异常，由上层捕获并告警。 */
    private List<DealActivity> parseActivities(String body, String category) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        if (root.path("code").asInt(0) != 1) {
            throw new IllegalStateException("code=" + root.path("code").asInt(0)
                    + " msg=" + root.path("msg").asText(""));
        }
        JsonNode rows = root.path("data").path("data");
        List<DealActivity> list = new ArrayList<>();
        if (!rows.isArray()) {
            throw new IllegalStateException("data.data 非数组");
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
     * [2026-10-30] 优惠列表拉空告警：把「为什么没数据」推到 WxPusher，方便排查。
     * 仅在距上次推送超过冷却期时发送，避免每个请求都推（刷屏）。
     */
    private void alertEmpty(List<CatResult> results) {
        long now = System.currentTimeMillis();
        long last = lastEmptyAlertAt.get();
        if (now - last < EMPTY_ALERT_COOLDOWN_MS || !lastEmptyAlertAt.compareAndSet(last, now)) {
            return;
        }
        List<String> errs = results.stream()
                .map(CatResult::error)
                .filter(Objects::nonNull)
                .toList();
        StringBuilder sb = new StringBuilder("【聚推客优惠拉取为空】apikey 已配置但无活动返回，请排查： ");
        if (errs.isEmpty()) {
            sb.append("所有分类均返回成功(code=1)但无活动数据——多半是聚推客账号未通过审核/激活，");
            sb.append("或活动列表接口当前无数据。可用 curl 直连 ");
            sb.append("http://api.jutuike.com/union/act_list?apikey=你的KEY&cate_name=美团 验证 key/账号。");
        } else {
            sb.append("各分类失败原因： ");
            for (String e : errs) sb.append(e).append("； ");
            sb.append("若为 code!=1：核对 .env 的 CPS_JUTUIKE_API_KEY（别带空格/换行）；");
            sb.append("若为网络异常：检查服务器到 api.jutuike.com 的连通性。");
        }
        notifier.send(sb.toString());
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
            // 把状态码和响应前 200 字符带进异常，便于在日志里直接看到聚推客原始报错（不含 apikey）
            String snippet = response.body() == null ? "" : response.body();
            if (snippet.length() > 200) snippet = snippet.substring(0, 200);
            throw new IllegalStateException("HTTP " + response.statusCode() + " body=" + snippet);
        }
        return response.body();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
