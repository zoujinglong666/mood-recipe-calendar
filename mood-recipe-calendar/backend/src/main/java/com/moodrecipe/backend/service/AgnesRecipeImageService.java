package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.entity.Recipe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 使用 Agnes Image 为 AI 菜谱生成封面；失败时不影响菜谱正文返回。 */
@Service
public class AgnesRecipeImageService {

    // Agnes 图片生成通常需要 30-60 秒，超时放宽到 90 秒
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);
    private static final Logger log = LoggerFactory.getLogger(AgnesRecipeImageService.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final String size;
    private final String ratio;
    /** 同一菜名的封面在进程内复用，避免每周计划反复等待同一张 AI 图。 */
    private final Map<String, String> coverCache = new ConcurrentHashMap<>();

    public AgnesRecipeImageService(
            ObjectMapper objectMapper,
            @Value("${ai.recipe-image.api-key:}") String apiKey,
            @Value("${ai.recipe-image.base-url:https://apihub.agnes-ai.com/v1/images/generations}") String baseUrl,
            @Value("${ai.recipe-image.model:agnes-image-2.5-flash}") String model,
            @Value("${ai.recipe-image.size:1K}") String size,
            @Value("${ai.recipe-image.ratio:4:3}") String ratio
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.size = size;
        this.ratio = ratio;
    }

    public Optional<String> generateCover(Recipe recipe) {
        return generateCover(recipe, null, true);
    }

    /**
     * 带追加指令的生图（视觉质检退回重拍用）。
     * extraInstruction 非空时拼进 prompt 末尾；useCache=false 绕过按名缓存，
     * 否则重拍会直接命中第一次的缓存 URL，修改意见等于没加。
     */
    public Optional<String> generateCover(Recipe recipe, String extraInstruction, boolean useCache) {
        if (apiKey.isBlank()) return Optional.empty();
        String cacheKey = clip(recipe.getName(), 80);
        if (useCache) {
            String cached = cacheKey.isBlank() ? null : coverCache.get(cacheKey);
            if (cached != null) return Optional.of(cached);
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody(recipe, extraInstruction))))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String body = response.body();
                log.warn("Agnes image request failed with HTTP {}: {}", response.statusCode(),
                        body == null ? "" : body.substring(0, Math.min(300, body.length())));
                return Optional.empty();
            }
            Optional<String> image = imageUrl(response.body());
            if (useCache && !cacheKey.isBlank()) image.ifPresent(url -> coverCache.putIfAbsent(cacheKey, url));
            return image;
        } catch (Exception exception) {
            log.warn("Agnes image request failed: {}", exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** 管理端「重新配图」用：驱逐按名缓存，强制下一次生图走真实请求。 */
    public void evictCoverCache(String name) {
        String key = clip(name, 80);
        if (!key.isBlank()) coverCache.remove(key);
    }

    Map<String, Object> requestBody(Recipe recipe) {
        return requestBody(recipe, null);
    }

    Map<String, Object> requestBody(Recipe recipe, String extraInstruction) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        String p = prompt(recipe);
        if (extraInstruction != null && !extraInstruction.isBlank()) {
            // 质检退回的修改意见：作为硬性补充要求追加在 prompt 末尾
            p = p + "上一版画面存在以下问题，本次必须修正：" + clip(extraInstruction, 120) + "。";
        }
        body.put("prompt", p);
        body.put("size", size);
        body.put("ratio", ratio);
        body.put("extra_body", Map.of("response_format", "url"));
        return body;
    }

    Optional<String> imageUrl(String responseBody) {
        try {
            JsonNode data = objectMapper.readTree(responseBody).path("data");
            if (!data.isArray() || data.isEmpty()) return Optional.empty();
            String url = data.path(0).path("url").asText().trim();
            URI uri = URI.create(url);
            return "https".equalsIgnoreCase(uri.getScheme()) ? Optional.of(url) : Optional.empty();
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    /**
     * 组合菜名拆分：「主食 · 配饮品」（早餐池硬性格式）拆成两样食物，
     * 「配米糊」这类前导连接词剥掉；无分隔符的普通菜名返回单元素。
     */
    static java.util.List<String> comboParts(String name) {
        if (name == null || name.isBlank()) return java.util.List.of();
        java.util.List<String> parts = new java.util.ArrayList<>();
        for (String seg : name.split("·")) {
            String s = seg.trim().replaceAll("^[配加和与]+", "").trim();
            if (!s.isEmpty()) parts.add(s);
        }
        return parts;
    }

    /**
     * 食欲感来自模型能响应的具体物理信号，而不是「令人有食欲」这类形容词：
     * ① 85mm f/2.8 浅景深 + 暖色散景（器材语言，真实感骨架）
     * ② 侧逆窗光把蒸汽打亮成白雾（「刚出锅」最硬的视觉证据）
     * ③ 按菜品类型给质地微观（油星/米油/焦斑/半透明皮），见 textureCue
     * ④ 动态痕迹（舀痕/微晃的汤面）暗示刚被端上桌
     */
    private String prompt(Recipe recipe) {
        String raw = clip(recipe.getName(), 60);
        java.util.List<String> parts = comboParts(raw);
        StringBuilder p = new StringBuilder(
                "真实食物摄影，RAW photo，85mm 镜头 f/2.8 浅景深，自然侧逆窗光，暖白色调，"
                        + "哑光陶瓷餐具与原木桌面，背景柔化成暖色散景，食物前景锐利清晰，约 40 度俯拍视角。");
        if (parts.size() >= 2) {
            // 组合餐：两样食物各自成碗、左右并列——不是一个盘子装所有
            p.append("一顿刚做好的中式组合餐：木质托盘上两样食物分开盛放、左右并排——左边一碗/一笼「")
                    .append(clip(parts.get(0), 20)).append("」，右边一碗/一杯「").append(clip(parts.get(1), 20))
                    .append("」，各自独立成碗、高低错落，绝不装进同一个盘子，也不要把右边食物垫在左边下面，")
                    .append("画面对象只出现这两样完成后的食物与餐具。");
        } else {
            p.append("一道刚出锅、还冒着热气的中国家常菜「").append(clip(raw, 40))
                    .append("」盛在盘中，主体居中留有呼吸感，画面只出现完成后的这道菜。");
        }
        // 质地微观按主菜类型取
        p.append(textureCue(parts.isEmpty() ? raw : parts.get(0))).append("。");
        // 生肉防线：菜名里的「鲜肉/鲜虾」指馅料或已烹熟的主料，绝不是单独的生肉
        p.append("所有食材必须是烹饪完成、可直接入口的状态：肉馅与肉末必须已包入面皮或完全烹熟，"
                + "严禁出现生肉块、生肉馅、生蛋液等未烹饪原料。");
        // 负向合并成一条，避免否定词堆叠稀释主描述
        p.append("严禁插画、卡通、3D 渲染、CG 感、过饱和塑料质感；不要出现生的未切配食材、调料碟瓶、包装、"
                + "人物、文字、水印、品牌标志或多余餐具。");
        return p.toString();
    }

    /** 按菜品类型给食欲质地描述：汤要油星与透亮的汤色，粥要米油，蒸点要半透明皮，饭要锅气。 */
    static String textureCue(String name) {
        String n = name == null ? "" : name;
        if (n.matches(".*(馄饨|汤面|汤粉|米粉|面条|汤|盅).*"))
            return "汤面清亮泛着细小油星，葱花与配料悬浮其上，白色蒸汽被逆光照亮，隐约可见馅色，汤面微微晃动的生动感";
        if (n.matches(".*(粥|糊|羹|米糊).*"))
            return "质地绵密浓稠，表面带米油光泽与少量配料点缀，留有勺子舀过的痕迹，微微冒热气";
        if (n.matches(".*(包子|小笼|烧麦|蒸饺|饺|点心).*"))
            return "皮薄半透明隐约透出馅色，褶子均匀，垫屉布或白瓷盘，少量蒸汽缭绕";
        if (n.matches(".*(饭|煲|烩).*"))
            return "饭粒分明油亮，酱汁均匀挂色，带锅气与轻微焦边";
        if (n.matches(".*(饼|煎|烙|烤|酥).*"))
            return "表面金黄焦斑与油光，边缘微脆，断面层次分明";
        return "酱汁油亮挂壁，食材质感清晰，热气微微升起";
    }

    private String clip(String value, int maxLength) {
        if (value == null) return "";
        String clean = value.replaceAll("[\\r\\n]+", " ").trim();
        return clean.substring(0, Math.min(clean.length(), maxLength));
    }
}
