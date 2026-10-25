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
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 小程序码（wxacode.getUnlimited）生成与托管。
 *
 * 用途：画册/年度报告分享图的「扫码打开小程序」必须是真码——
 * 之前导出图嵌的是静态占位 JPG、页面是 CSS 假网格，好友扫码什么都扫不出。
 *
 * 设计要点：
 * - scene 白名单（调用方校验），落地页统一首页，scene 仅作扫码来源归因；
 * - 码内容确定性（scene 固定 → 码固定），COS 用固定 key 覆盖上传，URL 稳定可被前端永久缓存；
 * - 内存缓存 scene→URL，进程内只生成一次；微信侧同一 scene 重复生成也返回同一个码；
 * - access_token 独立管理（与虚拟支付各自缓存，避免相互牵连）。
 */
@Service
public class WxacodeService {

    private static final Logger log = LoggerFactory.getLogger(WxacodeService.class);
    private static final String LANDING_PAGE = "pages/index/index";
    private static final String WXACODE_API = "https://api.weixin.qq.com/wxa/getwxacodeunlimited";
    /** 旧接口兜底：getwxacodeunlimited 对「页面未随代码包上传」返回 40066 invalid url，
     *  本地开发期/从未上传过代码包时必然命中；getwxacode 走 path 校验更宽松，实测可出码。
     *  两个接口的码都是永久的，旧接口有 10 万总量限制——分享物料场景（个位数 scene）远够用。 */
    private static final String WXACODE_LEGACY_API = "https://api.weixin.qq.com/wxa/getwxacode";
    /** 码边长（px）：导出画布里码盒约 150px，480 足够清晰；页面展示容器仅 80rpx */
    private static final int CODE_WIDTH = 480;

    /** 允许生成的 scene（同时决定前端调用方的合法性）：分享物料来源归因 */
    public static final Set<String> ALLOWED_SCENES = Set.of("album", "report", "recipe", "daily_menu", "record");

    private final ObjectMapper objectMapper;
    private final CosImageStorageService cos;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build();

    @Value("${wechat.appid:}")
    private String appid;

    @Value("${wechat.secret:}")
    private String secret;

    @Value("${tencent.cos.prefix:mood-recipe/}")
    private String cosPrefix;

    private volatile String accessToken = "";
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    /** scene → COS URL；进程内只生成一次（码确定性，无失效问题） */
    private final Map<String, String> urlCache = new ConcurrentHashMap<>();

    public WxacodeService(ObjectMapper objectMapper, CosImageStorageService cos) {
        this.objectMapper = objectMapper;
        this.cos = cos;
    }

    public static boolean sceneAllowed(String scene) {
        return scene != null && ALLOWED_SCENES.contains(scene);
    }

    /**
     * 取小程序码 URL：命中内存缓存直接返回；否则调微信生成 → 固定 key 上传 COS → 缓存。
     * 任何一步失败返回 empty，调用方应降级（隐藏码位或保留文字兜底），不阻断主流程。
     */
    public Optional<String> resolveUrl(String scene) {
        if (!sceneAllowed(scene)) {
            log.warn("[wxacode] 拒绝非法 scene: {}", scene);
            return Optional.empty();
        }
        String cached = urlCache.get(scene);
        if (cached != null) return Optional.of(cached);
        try {
            byte[] png = generateCode(scene);
            String base = cosPrefix.endsWith("/") ? cosPrefix : cosPrefix + "/";
            // 按真实字节定扩展名与 MIME：微信旧接口返回 JPEG，硬编码 .png 会让 COS
            // content-type 与内容不符，部分 CDN/客户端按类型渲染会出问题
            boolean jpeg = png.length >= 2 && (png[0] & 0xFF) == 0xFF && (png[1] & 0xFF) == 0xD8;
            String ext = jpeg ? ".jpg" : ".png";
            String mime = jpeg ? "image/jpeg" : "image/png";
            String key = base + "wxacode/" + scene + ext;
            Optional<String> url = cos.uploadBytesFixedKey(png, mime, ext, key);
            url.ifPresent(u -> urlCache.put(scene, u));
            return url;
        } catch (Exception ex) {
            log.warn("[wxacode] 生成失败 scene={}: {}", scene, ex.toString());
            return Optional.empty();
        }
    }

    /** 调微信生成小程序码：优先 getwxacodeunlimited（无总量限制+scene 归因），
     *  失败（典型 40066 invalid url：页面未随代码包上传）回退 getwxacode。 */
    byte[] generateCode(String scene) throws Exception {
        if (appid == null || appid.isBlank() || secret == null || secret.isBlank())
            throw new IllegalStateException("未配置微信 appid/secret");
        try {
            return callCodeApi(WXACODE_API, unlimitedPayload(scene));
        } catch (Exception ex) {
            log.warn("[wxacode] getwxacodeunlimited 失败 scene={}: {}，回退 getwxacode", scene, ex.toString());
        }
        return callCodeApi(WXACODE_LEGACY_API, legacyPayload(scene));
    }

    private String unlimitedPayload(String scene) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scene", scene);
        body.put("page", LANDING_PAGE); // 落地统一首页：无需参数、任何用户可见；scene 在 onLoad 可做归因
        body.put("width", CODE_WIDTH);
        body.put("check_path", false);
        return objectMapper.writeValueAsString(body);
    }

    /** 旧接口只认 path：scene 拼 query 带上，首页 onLoad 从 query.scene 归因。 */
    private String legacyPayload(String scene) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("path", LANDING_PAGE + "?scene=" + URLEncoder.encode(scene, StandardCharsets.UTF_8));
        body.put("width", CODE_WIDTH);
        return objectMapper.writeValueAsString(body);
    }

    /** 通用码接口调用：微信成功返回图片二进制，失败返回 JSON（errcode/errmsg）→ 抛异常。 */
    private byte[] callCodeApi(String api, String payload) throws Exception {
        URI uri = URI.create(api + "?access_token="
                + URLEncoder.encode(accessToken(), StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(java.time.Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        byte[] data = response.body();
        if (!contentType.startsWith("image/") || data.length == 0) {
            String errmsg = "unknown";
            try {
                JsonNode json = objectMapper.readTree(data);
                errmsg = json.path("errmsg").asText("unknown");
            } catch (Exception ignored) {
                // 非 JSON 响应，保留 unknown
            }
            throw new IllegalStateException("微信小程序码接口失败: " + errmsg);
        }
        return data;
    }

    private String accessToken() throws Exception {
        if (!accessToken.isBlank() && Instant.now().isBefore(tokenExpiresAt)) return accessToken;
        URI uri = URI.create("https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid="
                + URLEncoder.encode(appid, StandardCharsets.UTF_8) + "&secret="
                + URLEncoder.encode(secret, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(java.time.Duration.ofSeconds(10)).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode json = objectMapper.readTree(response.body());
        accessToken = json.path("access_token").asText();
        tokenExpiresAt = Instant.now().plusSeconds(Math.max(60, json.path("expires_in").asLong(7200) - 300));
        return accessToken;
    }
}
