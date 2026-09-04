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
import java.util.UUID;

@RestController
@RequestMapping("/api/upload")
public class FileUploadController {

    @Value("${upload.dir:./uploads}")
    private String uploadDir;
    @Value("${upload.public-base-url:}")
    private String publicBaseUrl;
    @Value("${tencent.cos.enabled:false}") private boolean cosEnabled;
    @Value("${tencent.cos.secret-id:}") private String cosSecretId;
    @Value("${tencent.cos.secret-key:}") private String cosSecretKey;
    @Value("${tencent.cos.region:}") private String cosRegion;
    @Value("${tencent.cos.bucket:}") private String cosBucket;
    @Value("${tencent.cos.prefix:uploads/}") private String cosPrefix;

    /**
     * 图片上传
     * POST /api/upload/image
     * form-data: file
     */
    @PostMapping("/image")
    public ApiResponse<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ApiResponse.error("文件不能为空");
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
        if (cosEnabled) return uploadToCos(file, contentType, filename);

        try {
            // 确保目录存在
            Path dirPath = Paths.get(uploadDir);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            // 不信任原文件名；仅按已校验的 MIME 类型决定扩展名。
            // 保存文件
            Path filePath = dirPath.resolve(filename);
            file.transferTo(filePath.toFile());

            // 返回可访问的 URL
            Map<String, String> result = new HashMap<>();
            String path = "/uploads/" + filename;
            result.put("url", publicBaseUrl == null || publicBaseUrl.isBlank() ? path : publicBaseUrl.replaceAll("/$", "") + path);
            result.put("filename", filename);
            return ApiResponse.ok(result);

        } catch (IOException e) {
            return ApiResponse.error("上传失败: " + e.getMessage());
        }
    }

    private ApiResponse<Map<String, String>> uploadToCos(MultipartFile file, String contentType, String filename) {
        if (cosSecretId.isBlank() || cosSecretKey.isBlank() || cosRegion.isBlank() || cosBucket.isBlank()) return ApiResponse.error(500, "腾讯云 COS 配置不完整");
        COSClient client = new COSClient(new BasicCOSCredentials(cosSecretId, cosSecretKey), new ClientConfig(new Region(cosRegion)));
        try (InputStream input = file.getInputStream()) {
            String key = (cosPrefix.endsWith("/") ? cosPrefix : cosPrefix + "/") + filename;
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize()); metadata.setContentType(contentType);
            client.putObject(new PutObjectRequest(cosBucket, key, input, metadata));
            String origin = publicBaseUrl == null || publicBaseUrl.isBlank() ? "https://" + cosBucket + ".cos." + cosRegion + ".myqcloud.com" : publicBaseUrl.replaceAll("/$", "");
            return ApiResponse.ok(Map.of("url", origin + "/" + key, "filename", filename));
        } catch (IOException exception) {
            return ApiResponse.error("上传到腾讯云 COS 失败");
        } finally { client.shutdown(); }
    }

    private boolean matchesMagic(String type, byte[] value) {
        if ("image/jpeg".equals(type)) return value.length >= 3 && (value[0] & 0xff) == 0xff && (value[1] & 0xff) == 0xd8 && (value[2] & 0xff) == 0xff;
        if ("image/png".equals(type)) return value.length >= 8 && (value[0] & 0xff) == 0x89 && value[1] == 0x50 && value[2] == 0x4e && value[3] == 0x47 && value[4] == 0x0d && value[5] == 0x0a && value[6] == 0x1a && value[7] == 0x0a;
        return value.length >= 12 && value[0] == 'R' && value[1] == 'I' && value[2] == 'F' && value[3] == 'F' && value[8] == 'W' && value[9] == 'E' && value[10] == 'B' && value[11] == 'P';
    }
}
