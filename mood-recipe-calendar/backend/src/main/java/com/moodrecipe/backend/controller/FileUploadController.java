package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
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

        try {
            // 确保目录存在
            Path dirPath = Paths.get(uploadDir);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            // 生成文件名：日期 + UUID + 原扩展名
            String originalFilename = file.getOriginalFilename();
            String ext = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                ext = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
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
