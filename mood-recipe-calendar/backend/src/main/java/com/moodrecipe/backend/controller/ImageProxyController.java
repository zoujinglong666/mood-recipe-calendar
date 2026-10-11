package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.service.CosImageStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 图片代理：把外部 CDN 图片（如 AI 菜谱封面所在的 doubaocdn）经服务端拉取后
 * 再吐给小程序，绕开两类问题：
 *   1) 微信小程序域名白名单——小程序只需请求本项目 API 域名；
 *   2) 第三方 CDN 防盗链/签名过期——由服务端带 UA/Referer 拉取。
 *
 * 安全：复用 CosImageStorageService.isSafeSourceUrl 做 SSRF 校验（仅 https、拒绝内网）。
 * url 以 Base64 传参，避免特殊字符 / 日志泄露 query；空或非法直接 400。
 */
@RestController
@RequestMapping("/api/images")
public class ImageProxyController {

    private final CosImageStorageService cosImageStorage;

    @Autowired
    public ImageProxyController(CosImageStorageService cosImageStorage) {
        this.cosImageStorage = cosImageStorage;
    }

    @GetMapping("/proxy")
    public ResponseEntity<byte[]> proxy(@RequestParam("u") String encodedUrl) {
        if (encodedUrl == null || encodedUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少图片地址");
        }
        final String url;
        try {
            url = new String(Base64.getUrlDecoder().decode(encodedUrl.trim()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片地址格式不正确");
        }
        return cosImageStorage.proxyFetch(url)
                .map(result -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, result.contentType())
                        .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                        .body(result.data()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "图片获取失败"));
    }
}
