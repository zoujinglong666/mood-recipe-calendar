package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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

        try {
            // 确保目录存在
            Path dirPath = Paths.get(uploadDir);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            // 不信任原文件名；仅按已校验的 MIME 类型决定扩展名。
            String ext = extensions.get(contentType);
            String dateStr = new SimpleDateFormat("yyyyMMdd").format(new Date());
            String filename = dateStr + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;

            // 保存文件
            Path filePath = dirPath.resolve(filename);
            file.transferTo(filePath.toFile());

            // 返回可访问的 URL
            Map<String, String> result = new HashMap<>();
            result.put("url", "/uploads/" + filename);
            result.put("filename", filename);
            return ApiResponse.ok(result);

        } catch (IOException e) {
            return ApiResponse.error("上传失败: " + e.getMessage());
        }
    }
}
