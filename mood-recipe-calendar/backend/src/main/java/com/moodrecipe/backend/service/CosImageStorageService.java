package com.moodrecipe.backend.service;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * 图片存储服务：腾讯云 COS 上传与 URL 转存。
 * 用于将锅仔智能体生成的临时图片 URL 转存为 COS 永久地址，保证菜谱落库后图片长期可用。
 * COS 密钥仅从环境变量/backend/.env 读取，绝不入库。
 */
@Service
public class CosImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(CosImageStorageService.class);

    @Value("${tencent.cos.enabled:true}")
    private boolean cosEnabled;
    @Value("${tencent.cos.secret-id:}")
    private String cosSecretId;
    @Value("${tencent.cos.secret-key:}")
    private String cosSecretKey;
    @Value("${tencent.cos.region:}")
    private String cosRegion;
    @Value("${tencent.cos.bucket:}")
    private String cosBucket;
    @Value("${tencent.cos.prefix:mood-recipe/}")
    private String cosPrefix;
    /** COS 自定义/回源 CDN 域名（bucket 公共读时可直接访问） */
    @Value("${tencent.cos.domain:}")
    private String cosDomain;
    @Value("${upload.public-base-url:}")
    private String publicBaseUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** 是否具备 COS 上传条件 */
    public boolean available() {
        return cosEnabled && !cosSecretId.isBlank() && !cosSecretKey.isBlank()
                && !cosRegion.isBlank() && !cosBucket.isBlank();
    }

    /**
     * 将外部图片 URL 转存到 COS（下载 → 上传 → 返回永久 URL）。
     * 失败时返回 empty，调用方保留原 URL。
     */
    public Optional<String> transferFromUrl(String sourceUrl, String sceneDir) {
        if (!available()) {
            // COS 未配置时保留原 URL（可能为短期临时图），避免整张封面丢失
            log.info("COS 未配置，保留原始图片 URL: {}", sourceUrl);
            return Optional.of(sourceUrl);
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(sourceUrl))
                    .timeout(java.time.Duration.ofSeconds(30))
                    .header("User-Agent", "Mozilla/5.0 (mood-recipe-backend)")
                    .GET().build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("AI 图片下载失败: status={} url={}", response.statusCode(), sourceUrl);
                return Optional.empty();
            }
            byte[] data = response.body();
            if (data.length == 0 || data.length > 8 * 1024 * 1024) {
                log.warn("AI 图片尺寸异常: {} bytes", data.length);
                return Optional.empty();
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("image/jpeg");
            String ext = switch (contentType) {
                case "image/png" -> ".png";
                case "image/webp" -> ".webp";
                case "image/gif" -> ".gif";
                default -> ".jpg";
            };
            return uploadBytes(data, contentType, ext, sceneDir);
        } catch (Exception ex) {
            log.warn("AI 图片转存失败: {}", ex.toString());
            return Optional.empty();
        }
    }

    /** 上传字节到 COS，返回永久访问 URL */
    public Optional<String> uploadBytes(byte[] data, String contentType, String ext, String sceneDir) {
        if (!available()) return Optional.empty();
        COSClient client = new COSClient(new BasicCOSCredentials(cosSecretId, cosSecretKey),
                new ClientConfig(new Region(cosRegion)));
        try {
            String base = cosPrefix.endsWith("/") ? cosPrefix : cosPrefix + "/";
            String dir = sceneDir.endsWith("/") ? sceneDir : sceneDir + "/";
            String dateStr = new SimpleDateFormat("yyyyMMdd").format(new Date());
            String filename = dateStr + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;
            String key = base + dir + filename;
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(data.length);
            metadata.setContentType(contentType);
            client.putObject(new PutObjectRequest(cosBucket, key, new ByteArrayInputStream(data), metadata));
            return Optional.of(resolveOrigin() + "/" + key);
        } catch (Exception ex) {
            log.warn("COS 上传失败: {}", ex.toString());
            return Optional.empty();
        } finally {
            client.shutdown();
        }
    }

    /** 对外访问域名优先级：upload.public-base-url > tencent.cos.domain > COS 默认域名 */
    private String resolveOrigin() {
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) return publicBaseUrl.replaceAll("/$", "");
        if (cosDomain != null && !cosDomain.isBlank()) {
            String d = cosDomain.replaceAll("/$", "");
            return d.startsWith("http") ? d : "https://" + d;
        }
        return "https://" + cosBucket + ".cos." + cosRegion + ".myqcloud.com";
    }
}
