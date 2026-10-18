package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 兜底 404：所有未匹配到的 /api/** 路径统一返回 JSON 404，而非 Tomcat/whitelabel 默认页。
 * 仅覆盖 /api/**，避开 /uploads/** 等静态资源（由 ResourceHandler 处理，不受影响）。
 * 具体接口的 @GetMapping/@PostMapping 优先级高于 /** 通配，不会误伤真实接口。
 */
@RestController
public class ApiFallbackController {

    @RequestMapping("/api/**")
    public ResponseEntity<ApiResponse<Void>> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, "接口不存在"));
    }
}
