package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.UserRecord;
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
import java.util.List;
import java.util.UUID;

/** 服务端海报渲染：会员专享，确保中文排版不依赖模型生成。 */
@Service
public class RecordPosterService {
    private static final int WIDTH = 1080;
    private static final int HEIGHT = 1440;
    /** 海报中文字体：Alpine 精简 JRE 零字体，中文会全部渲染成方框（豆腐块）。
     *  部署镜像已在 Dockerfile 安装 font-noto-cjk；此处按优先级探测可用中文字体，
     *  都探测不到再回退 SansSerif（英文兜底，功能不崩）。 */
    private static final Font POSTER_FONT = resolvePosterFont();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final String uploadDir;
    private final String publicBaseUrl;
    private final boolean cosEnabled;
    private final String cosSecretId;
    private final String cosSecretKey;
    private final String cosRegion;
    private final String cosBucket;
    private final String cosPrefix;
    private final String cosDomain;

    public RecordPosterService(@Value("${upload.dir:./uploads}") String uploadDir,
                               @Value("${upload.public-base-url:}") String publicBaseUrl,
                               @Value("${tencent.cos.enabled:false}") boolean cosEnabled,
                               @Value("${tencent.cos.secret-id:}") String cosSecretId,
                               @Value("${tencent.cos.secret-key:}") String cosSecretKey,
                               @Value("${tencent.cos.region:ap-guangzhou}") String cosRegion,
                               @Value("${tencent.cos.bucket:}") String cosBucket,
                               @Value("${tencent.cos.prefix:mood-recipe/}") String cosPrefix,
                               @Value("${tencent.cos.domain:}") String cosDomain) {
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
            if (font.canDisplayUpTo("锅仔美食海报") < 0) return font;
        }
        return new Font("SansSerif", Font.PLAIN, 24);
    }

    public String generate(UserRecord record) {
        try {
            BufferedImage poster = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = poster.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setColor(new Color(255, 249, 239));
            g.fillRect(0, 0, WIDTH, HEIGHT);
            drawText(g, "GUOZAI  /  FOOD MEMORY", 72, 92, 28, new Color(239, 90, 60), true);
            drawText(g, "NO. " + safe(record.getRecordDate()), 820, 92, 22, new Color(143, 112, 91), false);
            drawText(g, "这一餐的食光", 72, 170, 26, new Color(143, 112, 91), false);
            drawDishTitle(g, safe(record.getDishName()), 72, 245);
            drawText(g, safe(record.getRecordDate()) + "  ·  " + (record.getCookingTime() == null ? 30 : record.getCookingTime()) + " 分钟", 72, 292, 26, new Color(143, 112, 91), false);
            drawPhotos(g, record.getImageUrls());
            g.setColor(new Color(239, 90, 60));
            g.fillRect(72, 1120, 8, 176);
            drawText(g, "锅仔编辑批注", 108, 1152, 24, new Color(239, 90, 60), true);
            String note = record.getNote() == null || record.getNote().isBlank()
                    ? "把今天的味道收好，下一次回来继续。" : record.getNote();
            drawNote(g, clip(note, 66), 108, 1188);
            drawText(g, "THE TASTE OF TODAY", 72, 1368, 18, new Color(143, 112, 91), false);
            g.dispose();

            Path folder = Paths.get(uploadDir, "posters");
            Files.createDirectories(folder);
            String filename = record.getId() + "_" + UUID.randomUUID().toString().substring(0, 8) + ".png";
            Path output = folder.resolve(filename);
            String path = "/uploads/posters/" + filename;
            if (cosEnabled && !cosSecretId.isBlank() && !cosSecretKey.isBlank() && !cosBucket.isBlank()) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ImageIO.write(poster, "png", bytes);
                String key = (cosPrefix.endsWith("/") ? cosPrefix : cosPrefix + "/") + "posters/" + filename;
                COSClient client = new COSClient(new BasicCOSCredentials(cosSecretId, cosSecretKey), new ClientConfig(new Region(cosRegion)));
                try (ByteArrayInputStream input = new ByteArrayInputStream(bytes.toByteArray())) {
                    ObjectMetadata metadata = new ObjectMetadata();
                    metadata.setContentLength(bytes.size());
                    metadata.setContentType("image/png");
                    client.putObject(new PutObjectRequest(cosBucket, key, input, metadata));
                    return resolveCosOrigin() + "/" + key;
                } finally { client.shutdown(); }
            }
            ImageIO.write(poster, "png", output.toFile());
            return publicBaseUrl.isBlank() ? path : publicBaseUrl + path;
        } catch (Exception e) {
            throw new IllegalStateException("海报生成失败，请稍后重试", e);
        }
    }

    private void drawPhotos(Graphics2D g, List<String> urls) throws IOException, InterruptedException {
        List<String> safeUrls = urls == null ? List.of() : urls.stream().filter(v -> v != null && !v.isBlank()).limit(9).toList();
        int x = 72, y = 340, width = 936, height = 650;
        for (int i = 0; i < safeUrls.size(); i++) {
            BufferedImage image = readImage(safeUrls.get(i));
            if (image == null) continue;
            int columns = safeUrls.size() == 1 ? 1 : safeUrls.size() > 4 ? 3 : 2;
            int rows = (int) Math.ceil((double) safeUrls.size() / columns);
            int col = safeUrls.size() == 1 ? 0 : i % columns;
            int row = safeUrls.size() == 1 ? 0 : i / columns;
            int cellWidth = safeUrls.size() == 1 ? width : (width - (columns - 1) * 8) / columns;
            int cellHeight = safeUrls.size() == 1 ? height : (height - (rows - 1) * 8) / rows;
            drawCover(g, image, x + col * (cellWidth + 8), y + row * (cellHeight + 8), cellWidth, cellHeight);
        }
    }

    private BufferedImage readImage(String value) throws IOException, InterruptedException {
        if (value.startsWith("/uploads/")) {
            Path path = Paths.get(uploadDir, value.substring("/uploads/".length()));
            return Files.exists(path) ? ImageIO.read(path.toFile()) : null;
        }
        if (!value.startsWith("http")) return null;
        HttpResponse<byte[]> response = http.send(HttpRequest.newBuilder(URI.create(value)).timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        return response.statusCode() >= 200 && response.statusCode() < 300 ? ImageIO.read(new ByteArrayInputStream(response.body())) : null;
    }

    /** 菜名标题：按长度自动缩号，保证单行放下不溢出画布——长菜名不再挤出一行怪字。 */
    private void drawDishTitle(Graphics2D g, String title, int x, int baseline) {
        int size = 72;
        if (title.length() > 6) size = 60;
        if (title.length() > 10) size = 48;
        if (title.length() > 14) size = 40;
        while (size > 28 && g.getFontMetrics(POSTER_FONT.deriveFont(Font.BOLD, (float) size)).stringWidth(title) > WIDTH - x - 72) {
            size -= 4;
        }
        drawText(g, title, x, baseline, size, new Color(57, 36, 25), true);
    }

    /** 批注按约 26 字/行折行，最多三行（超出尾部省略），行距 44。 */
    private void drawNote(Graphics2D g, String note, int x, int firstBaseline) {
        int perLine = 26;
        for (int i = 0; i * perLine < note.length() && i < 3; i++) {
            int end = Math.min(note.length(), (i + 1) * perLine);
            String line = note.substring(i * perLine, end);
            if (i == 2 && end < note.length()) line = line.substring(0, Math.max(0, line.length() - 1)) + "…";
            drawText(g, line, x, firstBaseline + i * 44, 30, new Color(78, 54, 40), false);
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

    private String safe(String value) { return value == null || value.isBlank() ? "这一餐" : value; }
    private String clip(String value, int length) { return value.length() <= length ? value : value.substring(0, length) + "…"; }
    private String resolveCosOrigin() {
        if (!publicBaseUrl.isBlank()) return publicBaseUrl;
        if (cosDomain != null && !cosDomain.isBlank()) return cosDomain.startsWith("http") ? cosDomain : "https://" + cosDomain;
        return "https://" + cosBucket + ".cos." + cosRegion + ".myqcloud.com";
    }
}
