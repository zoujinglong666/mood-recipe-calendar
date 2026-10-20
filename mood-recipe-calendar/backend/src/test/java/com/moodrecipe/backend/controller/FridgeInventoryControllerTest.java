package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.service.AdRewardService;
import com.moodrecipe.backend.service.FoodShelfLifeCatalog;
import com.moodrecipe.backend.service.FridgeExpiryNotifier;
import com.moodrecipe.backend.service.FridgeInventoryService;
import com.moodrecipe.backend.service.FridgeVisionService;
import com.moodrecipe.backend.service.UsageQuotaService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 冰箱拍照识别闸门：会员放行；非会员需当日广告试用凭证，且识别成功才消耗（看一次=用一次）。 */
class FridgeInventoryControllerTest {
    private final FridgeInventoryService inventory = mock(FridgeInventoryService.class);
    private final FridgeVisionService vision = mock(FridgeVisionService.class);
    private final FridgeExpiryNotifier expiryNotifier = mock(FridgeExpiryNotifier.class);
    private final FoodShelfLifeCatalog shelfLifeCatalog = mock(FoodShelfLifeCatalog.class);
    private final UsageQuotaService quota = mock(UsageQuotaService.class);
    private final AdRewardService adRewards = mock(AdRewardService.class);
    private final FridgeInventoryController controller = new FridgeInventoryController(
            inventory, vision, expiryNotifier, shelfLifeCatalog, quota, adRewards);

    private static final Map<String, String> REQUEST_BODY = Map.of();

    @Test
    void memberRecognizesWithoutAnyAdReward() {
        when(quota.member("member-1")).thenReturn(true);
        when(vision.recognize("https://img/x.jpg"))
                .thenReturn(new FridgeVisionService.RecognizeResult(java.util.List.of(), false, null));

        var resp = controller.recognize("member-1",
                new FridgeInventoryController.RecognizeRequest("https://img/x.jpg"));

        assertEquals(0, resp.getCode());
        verify(vision).recognize("https://img/x.jpg");
        verify(adRewards, never()).countWatched(anyString(), anyString(), any());
        verify(adRewards, never()).consumeOne(anyString(), anyString(), any());
    }

    @Test
    void nonMemberWithoutRewardIsRejectedBeforeVisionCall() {
        when(quota.member("user-1")).thenReturn(false);
        when(adRewards.countWatched(eq("user-1"), eq(AdRewardService.FRIDGE_OCR), any())).thenReturn(0);

        var resp = controller.recognize("user-1",
                new FridgeInventoryController.RecognizeRequest("https://img/x.jpg"));

        assertEquals(403, resp.getCode());
        // 关键：无凭证时绝不调视觉服务（每次调用都是真金白银的 API 花费）
        verify(vision, never()).recognize(anyString());
    }

    @Test
    void nonMemberWithRewardRecognizesAndConsumesOne() {
        when(quota.member("user-1")).thenReturn(false);
        when(adRewards.countWatched(eq("user-1"), eq(AdRewardService.FRIDGE_OCR), any())).thenReturn(2);
        when(vision.recognize("https://img/x.jpg"))
                .thenReturn(new FridgeVisionService.RecognizeResult(java.util.List.of(), false, null));

        var resp = controller.recognize("user-1",
                new FridgeInventoryController.RecognizeRequest("https://img/x.jpg"));

        assertEquals(0, resp.getCode());
        verify(adRewards).consumeOne(eq("user-1"), eq(AdRewardService.FRIDGE_OCR), any());
    }

    @Test
    void nonMemberKeepsRewardWhenVisionFails() {
        when(quota.member("user-1")).thenReturn(false);
        when(adRewards.countWatched(eq("user-1"), eq(AdRewardService.FRIDGE_OCR), any())).thenReturn(1);
        when(vision.recognize("https://img/x.jpg")).thenThrow(new IllegalStateException("视觉服务超时"));

        try {
            controller.recognize("user-1",
                    new FridgeInventoryController.RecognizeRequest("https://img/x.jpg"));
        }
        catch (RuntimeException ignored) {
            // 预期：异常直接上抛
        }

        // 识别失败不消耗凭证：用户重试不用再看一次广告
        verify(adRewards, never()).consumeOne(anyString(), anyString(), any());
    }

    @Test
    void blankImageIsRejectedWith400() {
        var resp = controller.recognize("user-1", new FridgeInventoryController.RecognizeRequest("  "));

        assertEquals(400, resp.getCode());
        verify(vision, never()).recognize(anyString());
    }
}
