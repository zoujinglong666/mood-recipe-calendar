package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/upload")
public class FileUploadController {

    /** 支持的上传场景：image=菜品/记录图片，avatar=用户头像 */
    private static final Set<String> SCENES = Set.of("image", "avatar");

    @Value("${upload.dir:./uploads}")
    private String uploadDir;
    @Value("${upload.public-base-url:}")
    private String publicBaseUrl;
    @Value("${tencent.cos.enabled:false}") private boolean cosEnabled;
    @Value("${tencent.cos.secret-id:}") private String cosSecretId;
    @Value("${tencent.cos.secret-key:}") private String cosSecretKey;
    @Value("${tencent.cos.region:}") private String cosRegion;
    @Value("${tencent.cos.bucket:}") private String cosBucket;
    /** 项目级前缀：bucket 被多个项目共用，所有 key 统一带该前缀区分项目 */
    @Value("${tencent.cos.prefix:mood-recipe/}") private String cosPrefix;
    /** 普通图片（菜品/记录照片）场景子目录 */
    @Value("${tencent.cos.image-dir:uploads/}") private String cosImageDir;
    /** 用户头像场景子目录 */
    @Value("${tencent.cos.avatar-dir:avatar/}") private String cosAvatarDir;
    /** COS 自定义/回源 CDN 域名（bucket 公共读时可直接访问） */
    @Value("${tencent.cos.domain:}") private String cosDomain;

    /**
     * 图片上传
     * POST /api/upload/image?type=image|avatar
     * form-data: file
     *
     * COS 模式 key 结构：{项目前缀}/{场景目录}/{日期}_{随机}.{ext}
     *   - 普通图片：mood-recipe/uploads/20260912_xxxxxxxx.jpg
     *   - 用户头像：mood-recipe/avatar/20260912_xxxxxxxx.jpg
     */
    @PostMapping("/image")
    public ApiResponse<Map<String, String>> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", defaultValue = "image") String type) {
        if (file.isEmpty()) {
            return ApiResponse.error("文件不能为空");
        }
        if (!SCENES.contains(type)) {
            return ApiResponse.error(400, "type 仅支持 image 或 avatar");
        }
        String contentType = file.getContentType();
        Map<String, String> extensions = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
        );
        if (!extensions.containsKey(contentType)) {
            return ApiResponse.error(400, "仅支持 JPG、PNG 或 WebP 图片");
        }
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(12);
            if (!matchesMagic(contentType, header)) return ApiResponse.error(400, "文件内容与图片类型不一致");
        } catch (IOException exception) {
            return ApiResponse.error("读取上传文件失败");
        }

        String ext = extensions.get(contentType);
        String dateStr = new SimpleDateFormat("yyyyMMdd").format(new Date());
        String filename = dateStr + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;
        String sceneDir = "avatar".equals(type) ? cosAvatarDir : cosImageDir;
        if (cosEnabled) return uploadToCos(file, contentType, filename, sceneDir);

        // 本地兜底：./uploads/{image|avatar}/<filename>
        try {
            Path dirPath = Paths.get(uploadDir).resolve(sceneDir);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            // 不信任原文件名；仅按已校验的 MIME 类型决定扩展名。
            Path filePath = dirPath.resolve(filename);
            file.transferTo(filePath.toFile());

            Map<String, String> result = new HashMap<>();
            String path = "/uploads/" + sceneDir + "/" + filename;
            result.put("url", publicBaseUrl == null || publicBaseUrl.isBlank() ? path : publicBaseUrl.replaceAll("/$", "") + path);
            result.put("filename", filename);
            return ApiResponse.ok(result);

        } catch (IOException e) {
            return ApiResponse.error("上传失败: " + e.getMessage());
        }
    }

    private ApiResponse<Map<String, String>> uploadToCos(MultipartFile file, String contentType, String filename, String sceneDir) {
        if (cosSecretId.isBlank() || cosSecretKey.isBlank() || cosRegion.isBlank() || cosBucket.isBlank()) return ApiResponse.error(500, "腾讯云 COS 配置不完整");
        COSClient client = new COSClient(new BasicCOSCredentials(cosSecretId, cosSecretKey), new ClientConfig(new Region(cosRegion)));
        try (InputStream input = file.getInputStream()) {
            String base = cosPrefix.endsWith("/") ? cosPrefix : cosPrefix + "/";
            String dir = sceneDir.endsWith("/") ? sceneDir : sceneDir + "/";
            String key = base + dir + filename;
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize()); metadata.setContentType(contentType);
            client.putObject(new PutObjectRequest(cosBucket, key, input, metadata));
            return ApiResponse.ok(Map.of("url", resolveOrigin() + "/" + key, "filename", filename));
        } catch (IOException exception) {
            return ApiResponse.error("上传到腾讯云 COS 失败");
        } finally { client.shutdown(); }
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

    private boolean matchesMagic(String type, byte[] value) {
        if ("image/jpeg".equals(type)) return value.length >= 3 && (value[0] & 0xff) == 0xff && (value[1] & 0xff) == 0xd8 && (value[2] & 0xff) == 0xff;
        if ("image/png".equals(type)) return value.length >= 8 && (value[0] & 0xff) == 0x89 && value[1] == 0x50 && value[2] == 0x4e && value[3] == 0x47 && value[4] == 0x0d && value[5] == 0x0a && value[6] == 0x1a && value[7] == 0x0a;
        return value.length >= 12 && value[0] == 'R' && value[1] == 'I' && value[2] == 'F' && value[3] == 'F' && value[8] == 'W' && value[9] == 'E' && value[10] == 'B' && value[11] == 'P';
    }
}
