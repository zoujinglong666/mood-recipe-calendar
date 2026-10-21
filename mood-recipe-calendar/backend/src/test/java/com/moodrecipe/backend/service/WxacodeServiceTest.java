package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 小程序码服务测试。
 *
 * 范围说明：微信 HTTP 调用与 COS 上传属 IO 边界，遵循项目既有模式
 * （参见 WechatVirtualPaymentServiceTest 只测 parseQueryOrder 纯逻辑），
 * 由联调/真机扫码验证；这里覆盖纯逻辑：scene 白名单与非法场景的拒绝路径
 * （拒绝必须发生在任何微信调用之前，防止接口被滥用生成任意小程序码）。
 */
class WxacodeServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final CosImageStorageService cos = mock(CosImageStorageService.class);
    private final WxacodeService service = new WxacodeService(json, cos);

    @Test
    void whitelistOnlyCoversKnownShareScenes() {
        assertTrue(WxacodeService.sceneAllowed("album"));
        assertTrue(WxacodeService.sceneAllowed("report"));
        assertTrue(WxacodeService.sceneAllowed("recipe"));
        assertTrue(WxacodeService.sceneAllowed("daily_menu"));
        assertTrue(WxacodeService.sceneAllowed("record"));
        assertFalse(WxacodeService.sceneAllowed("evil"));
        assertFalse(WxacodeService.sceneAllowed("ALBUM")); // 大小写敏感，防绕过
        assertFalse(WxacodeService.sceneAllowed(""));
        assertFalse(WxacodeService.sceneAllowed(null));
    }

    @Test
    void resolveUrlRejectsUnknownSceneBeforeAnyIo() {
        // 非法 scene 直接拒绝，绝不触碰微信接口/COS（白名单是第一道防线）
        assertEquals(Optional.empty(), service.resolveUrl("evil"));
        assertEquals(Optional.empty(), service.resolveUrl(null));
        verify(cos, never()).uploadBytesFixedKey(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }
}
