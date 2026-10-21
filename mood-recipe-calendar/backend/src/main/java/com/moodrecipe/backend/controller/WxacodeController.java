package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.service.WxacodeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 小程序码（「扫码打开小程序」真码）：画册/报告/食谱分享物料共用。
 * scene 白名单校验防滥用；生成结果服务端缓存 + COS 固定 key，前端可永久缓存 URL。
 */
@RestController
@RequestMapping("/api/wxacode")
public class WxacodeController {

    private final WxacodeService wxacode;

    public WxacodeController(WxacodeService wxacode) {
        this.wxacode = wxacode;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> get(
            @RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,
            @RequestParam String scene) {
        if (!WxacodeService.sceneAllowed(scene))
            return ApiResponse.error(400, "不支持的场景");
        Optional<String> url = wxacode.resolveUrl(scene);
        if (url.isEmpty())
            return ApiResponse.error(503, "小程序码生成失败，请稍后再试");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url", url.get());
        data.put("scene", scene);
        return ApiResponse.ok(data);
    }
}
