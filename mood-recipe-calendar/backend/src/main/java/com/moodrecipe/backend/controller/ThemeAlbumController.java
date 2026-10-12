package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.config.SessionAuthInterceptor;
import com.moodrecipe.backend.service.ThemeAlbumService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/** 主题菜谱专辑：列表 / 详情（购买解锁走 /api/virtual-commerce）。 */
@RestController
@RequestMapping("/api/theme-albums")
public class ThemeAlbumController {
    private final ThemeAlbumService albums;

    @Autowired
    public ThemeAlbumController(ThemeAlbumService albums) {
        this.albums = albums;
    }

    @GetMapping
    public ApiResponse<?> list(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        return ApiResponse.ok(albums.list(openid));
    }

    @GetMapping("/{sku}")
    public ApiResponse<?> detail(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @PathVariable String sku) {
        ThemeAlbumService.AlbumDetail detail = albums.detail(openid, sku);
        return detail == null ? ApiResponse.error(404, "专辑不存在") : ApiResponse.ok(detail);
    }
}
