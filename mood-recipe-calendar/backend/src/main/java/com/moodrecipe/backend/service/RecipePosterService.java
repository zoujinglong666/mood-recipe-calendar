package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 菜谱教程海报（借鉴竞品的信息图式教程卡）：
 * 文字与版式全部由 Java2D 模板绘制（数据来自菜谱结构化字段，中文绝不依赖生图模型），
 * 成品图优先用菜谱已有封面，无图时用品牌占位块并顺手排一次异步补拍（下次生成即有图）。
 *
 * 与 RecordPosterService（记录海报）同源的基建：CJK 字体探测 / COS 上传 / 圆角裁剪 / 折行。
 * 内容未变化的菜谱直接复用已生成 URL（内存缓存），避免重复渲染与重复上传。
 */
@Service
public class RecipePosterService {
    private static final int WIDTH = 1080;
    private static final int HEIGHT = 1920;
    private static final Font POSTER_FONT = resolvePosterFont();
    private static final Color INK = new Color(57, 36, 25);
    private static final Color SUB = new Color(143, 112, 91);
    private static final Color ACCENT = new Color(239, 90, 60);
    private static final Color CREAM = new Color(255, 249, 239);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final RecipeCoverBackfillService coverBackfill;
    private final ObjectMapper json;
    private final String uploadDir;
    private final String publicBaseUrl;
    private final boolean cosEnabled;
    private final String cosSecretId;
    private final String cosSecretKey;
    private final String cosRegion;
    private final String cosBucket;
    private final String cosPrefix;
    private final String cosDomain;
    /** 内容 hash → 已生成海报 URL：菜谱未改不重复渲染上传 */
    private final Map<Long, PosterCacheEntry> cache = new ConcurrentHashMap<>();

    private record PosterCacheEntry(String contentHash, String url) { }

    public RecipePosterService(RecipeCoverBackfillService coverBackfill, ObjectMapper json,
                               @Value("${upload.dir:./uploads}") String uploadDir,
                               @Value("${upload.public-base-url:}") String publicBaseUrl,
                               @Value("${tencent.cos.enabled:false}") boolean cosEnabled,
                               @Value("${tencent.cos.secret-id:}") String cosSecretId,
                               @Value("${tencent.cos.secret-key:}") String cosSecretKey,
                               @Value("${tencent.cos.region:ap-guangzhou}") String cosRegion,
                               @Value("${tencent.cos.bucket:}") String cosBucket,
                               @Value("${tencent.cos.prefix:mood-recipe/}") String cosPrefix,
                               @Value("${tencent.cos.domain:}") String cosDomain) {
        this.coverBackfill = coverBackfill;
        this.json = json;
        this.uploadDir = uploadDir;
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/$", "");
        this.cosEnabled = cosEnabled;
        this.cosSecretId = cosSecretId;
        this.cosSecretKey = cosSecretKey;
        this.cosRegion = cosRegion;
        this.cosBucket = cosBucket;
        this.cosPrefix = cosPrefix;
        this.cosDomain = cosDomain;
    }

    private static Font resolvePosterFont() {
        String[] candidates = {"Noto Sans CJK SC", "Noto Sans CJK SC Regular", "Source Han Sans SC",
                "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", "WenQuanYi Zen Hei", "SimHei"};
        for (String name : candidates) {
            Font font = new Font(name, Font.PLAIN, 24);
            if (font.canDisplayUpTo("锅仔菜谱海报") < 0) return font;
        }
        return new Font("SansSerif", Font.PLAIN, 24);
    }

    /** 生成（或复用）菜谱教程海报，返回图片 URL */
    public String generate(Long recipeId, String name, String description, String image,
                           String ingredientsJson, String stepsJson,
                           Integer cookingTime, String difficulty) {
        List<String> ingredients = parseList(ingredientsJson, 8);
        List<String> steps = parseList(stepsJson, 5);
        String contentHash = String.valueOf((name + "|" + ingredients + "|" + steps).hashCode());
        PosterCacheEntry cached = cache.get(recipeId);
        if (cached != null && cached.contentHash().equals(contentHash)) return cached.url();
        String url = render(name, description, image, ingredients, steps, cookingTime, difficulty, recipeId);
        cache.put(recipeId, new PosterCacheEntry(contentHash, url));
        return url;
    }

    private String render(String name, String description, String image, List<String> ingredients,
                          List<String> steps, Integer cookingTime, String difficulty, Long recipeId) {
        try {
            BufferedImage poster = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = poster.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(CREAM);
            g.fillRect(0, 0, WIDTH, HEIGHT);

            // 头部：品牌 + 菜名 + 元信息
            drawText(g, "GUOZAI RECIPE ／ 锅仔菜谱", 72, 96, 26, ACCENT, true);
            drawDishTitle(g, safe(name), 72, 190);
            drawText(g, clip(safe(description), 30), 72, 246, 28, SUB, false);
            drawText(g, (cookingTime == null ? 30 : cookingTime) + " 分钟  ·  " + (safe(difficulty)) + "  ·  家常可复现",
                    72, 300, 26, SUB, false);

            // 成品图：有图用封面（cover/图片 URL），无图用品牌占位块 + 排一次异步补拍
            BufferedImage cover = readImage(image);
            if (cover != null) {
                drawCover(g, cover, 72, 340, WIDTH - 144, 520);
            } else {
                drawPlaceholder(g, 72, 340, WIDTH - 144, 520, safe(name));
                if (coverBackfill != null && recipeId != null) coverBackfill.schedule(recipeId);
            }

            // 食材区：标题 + 两列最多 8 条
            int y = 950;
            drawSectionTitle(g, "食材", 72, y);
            y += 58;
            for (int i = 0; i < ingredients.size(); i++) {
                int col = i % 2, row = i / 2;
                int x = 72 + col * (WIDTH - 144) / 2;
                drawBullet(g, clip(ingredients.get(i), 16), x, y + row * 52);
            }

            // 做法区：标题 + 最多 5 步（每步单行截断）
            y = 950 + 58 + 4 * 52 + 46;
            drawSectionTitle(g, "做法", 72, y);
            y += 58;
            for (int i = 0; i < steps.size(); i++) {
                drawStep(g, i + 1, clip(steps.get(i), 34), 72, y + i * 64);
            }

            // 脚注：品牌与行动引导
            g.setColor(new Color(234, 210, 182));
            g.fillRect(72, HEIGHT - 150, WIDTH - 144, 2);
            drawText(g, "锅仔 · 按心情帮你决定今天吃什么", 72, HEIGHT - 96, 30, INK, true);
            drawText(g, "完整做法、营养分析与安全提示见小程序", 72, HEIGHT - 54, 24, SUB, false);
            g.dispose();

            return upload(poster, recipeId);
        } catch (Exception e) {
            throw new IllegalStateException("菜谱海报生成失败，请稍后重试", e);
        }
    }

    private void drawSectionTitle(Graphics2D g, String title, int x, int baseline) {
        g.setColor(ACCENT);
        g.fillRect(x, baseline - 34, 8, 44);
        drawText(g, title, x + 28, baseline, 40, INK, true);
    }

    private void drawBullet(Graphics2D g, String text, int x, int baseline) {
        g.setColor(ACCENT);
        g.fillOval(x, baseline - 16, 10, 10);
        drawText(g, text, x + 24, baseline, 28, new Color(78, 54, 40), false);
    }

    private void drawStep(Graphics2D g, int index, String text, int x, int baseline) {
        g.setColor(ACCENT);
        g.fillOval(x, baseline - 34, 44, 44);
        drawText(g, String.valueOf(index), x + 14, baseline - 2, 26, Color.WHITE, true);
        drawText(g, text, x + 64, baseline, 30, new Color(78, 54, 40), false);
    }

    /** 无图占位块：品牌色底 + 居中菜名，顺便排一次异步补拍，下次生成即有真实封面 */
    private void drawPlaceholder(Graphics2D g, int x, int y, int width, int height, String name) {
        Graphics2D clip = (Graphics2D) g.create();
        clip.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        clip.clip(new RoundRectangle2D.Float(x, y, width, height, 24, 24));
        clip.setColor(new Color(255, 241, 231));
        clip.fillRect(x, y, width, height);
        clip.setColor(ACCENT);
        clip.setFont(POSTER_FONT.deriveFont(Font.BOLD, 56f));
        FontMetrics metrics = clip.getFontMetrics();
        clip.drawString(name, x + (width - metrics.stringWidth(name)) / 2, y + height / 2);
        clip.setFont(POSTER_FONT.deriveFont(Font.PLAIN, 26f));
        clip.setColor(SUB);
        String tip = "成品图生成中，稍后重新保存即有实拍图";
        clip.drawString(tip, x + (width - clip.getFontMetrics().stringWidth(tip)) / 2, y + height / 2 + 56);
        clip.dispose();
    }

    /** 食材/步骤解析：优先 JSON 数组文本，退回分隔符切分 */
    private List<String> parseList(String value, int limit) {
        if (value == null || value.isBlank()) return List.of();
        String trimmed = value.trim();
        List<String> items = new ArrayList<>();
        if (trimmed.startsWith("[")) {
            try {
                for (com.fasterxml.jackson.databind.JsonNode node : json.readTree(trimmed)) {
                    String text = node.asText("").trim();
                    if (!text.isEmpty()) items.add(text);
                }
            } catch (Exception ignored) { /* 落到分隔符解析 */ }
        }
        if (items.isEmpty()) {
            for (String seg : trimmed.split("[,，、;；\\n]")) {
                String text = seg.trim();
                if (!text.isEmpty()) items.add(text);
            }
        }
        return items.stream().limit(limit).toList();
    }

    private void drawDishTitle(Graphics2D g, String title, int x, int baseline) {
        int size = 76;
        if (title.length() > 6) size = 62;
        if (title.length() > 10) size = 50;
        if (title.length() > 14) size = 42;
        while (size > 28 && g.getFontMetrics(POSTER_FONT.deriveFont(Font.BOLD, (float) size)).stringWidth(title) > WIDTH - x - 72) {
            size -= 4;
        }
        drawText(g, title, x, baseline, size, INK, true);
    }

    private BufferedImage readImage(String value) {
        try {
            if (value == null || value.isBlank()) return null;
            if (value.startsWith("/uploads/")) {
                Path path = Paths.get(uploadDir, value.substring("/uploads/".length()));
                return Files.exists(path) ? ImageIO.read(path.toFile()) : null;
            }
            if (!value.startsWith("http")) return null;
            HttpResponse<byte[]> response = http.send(HttpRequest.newBuilder(URI.create(value))
                    .timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            return response.statusCode() >= 200 && response.statusCode() < 300
                    ? ImageIO.read(new ByteArrayInputStream(response.body())) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void drawCover(Graphics2D g, BufferedImage source, int x, int y, int width, int height) {
        double scale = Math.max((double) width / source.getWidth(), (double) height / source.getHeight());
        int sw = (int) (width / scale), sh = (int) (height / scale);
        int sx = (source.getWidth() - sw) / 2, sy = (source.getHeight() - sh) / 2;
        Graphics2D clip = (Graphics2D) g.create();
        clip.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        clip.clip(new RoundRectangle2D.Float(x, y, width, height, 24, 24));
        clip.drawImage(source, x, y, x + width, y + height, sx, sy, sx + sw, sy + sh, null);
        clip.dispose();
    }

    private void drawText(Graphics2D g, String text, int x, int y, int size, Color color, boolean bold) {
        g.setColor(color);
        g.setFont(POSTER_FONT.deriveFont(bold ? Font.BOLD : Font.PLAIN, (float) size));
        g.drawString(text, x, y);
    }

    private String upload(BufferedImage poster, Long recipeId) throws IOException {
        String filename = "recipe-" + recipeId + "_" + UUID.randomUUID().toString().substring(0, 8) + ".png";
        if (cosEnabled && !cosSecretId.isBlank() && !cosSecretKey.isBlank() && !cosBucket.isBlank()) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(poster, "png", bytes);
            String key = (cosPrefix.endsWith("/") ? cosPrefix : cosPrefix + "/") + "recipe-posters/" + filename;
            COSClient client = new COSClient(new BasicCOSCredentials(cosSecretId, cosSecretKey), new ClientConfig(new Region(cosRegion)));
            try (ByteArrayInputStream input = new ByteArrayInputStream(bytes.toByteArray())) {
                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentLength(bytes.size());
                metadata.setContentType("image/png");
                client.putObject(new PutObjectRequest(cosBucket, key, input, metadata));
                return resolveCosOrigin() + "/" + key;
            } finally { client.shutdown(); }
        }
        Path folder = Paths.get(uploadDir, "recipe-posters");
        Files.createDirectories(folder);
        Path output = folder.resolve(filename);
        ImageIO.write(poster, "png", output.toFile());
        String path = "/uploads/recipe-posters/" + filename;
        return publicBaseUrl.isBlank() ? path : publicBaseUrl + path;
    }

    private String resolveCosOrigin() {
        if (!publicBaseUrl.isBlank()) return publicBaseUrl;
        if (cosDomain != null && !cosDomain.isBlank()) return cosDomain.startsWith("http") ? cosDomain : "https://" + cosDomain;
        return "https://" + cosBucket + ".cos." + cosRegion + ".myqcloud.com";
    }

    private static String safe(String value) { return value == null || value.isBlank() ? "家常菜" : value; }
    private static String clip(String value, int length) { return value.length() <= length ? value : value.substring(0, length) + "…"; }
}
