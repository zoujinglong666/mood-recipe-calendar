package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.agent.LlmClient;
import com.moodrecipe.backend.agent.LlmRequest;
import com.moodrecipe.backend.agent.LlmResponse;
import com.moodrecipe.backend.agent.LlmResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 视觉质检门：坏图拦截、降级放行（质检是增强项不能成为生图链路的单点故障）。
 */
class ImageQualityGateServiceTest {

    private final LlmClient llm = mock(LlmClient.class);
    private final ObjectMapper json = new ObjectMapper();

    private ImageQualityGateService gate(String visionModel) {
        return new ImageQualityGateService(llm, json, visionModel);
    }

    private static LlmResult ok(String content) {
        return LlmResult.ok(new LlmResponse(content, java.util.List.of(), "stop", null, "glm-4v-flash"), 1, 10);
    }

    @Test
    void disabledWhenVisionModelMissing() {
        when(llm.isConfigured()).thenReturn(true);
        assertFalse(gate("").enabled());
        assertFalse(gate(" ").enabled());
        // 未启用时恒放行，且不发起任何模型调用
        ImageQualityGateService.Verdict v = gate(" ").review("https://cos/x.jpg", "鲜肉馄饨", true);
        assertTrue(v.pass());
    }

    @Test
    void passesGoodImageAndSendsDishContext() {
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(any(LlmRequest.class))).thenReturn(ok("{\"pass\":true,\"reason\":\"\"}"));

        ImageQualityGateService.Verdict v = gate("glm-4v-flash").review("https://cos/x.jpg", "鲜肉馄饨 · 配米糊", true);

        assertTrue(v.pass());
        ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
        org.mockito.Mockito.verify(llm).complete(captor.capture());
        // 组合餐场景：质检 prompt 必须携带「两碗分摆」的校验项
        String prompt = captor.getValue().messages().get(0).content();
        assertTrue(prompt.contains("各自成碗"));
        assertTrue(prompt.contains("鲜肉馄饨"));
    }

    @Test
    void rejectsBadImageWithActionableReason() {
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(any(LlmRequest.class)))
                .thenReturn(ok("{\"pass\":false,\"reason\":\"画面出现生肉块，混装在一个盘子里\"}"));

        ImageQualityGateService.Verdict v = gate("glm-4v-flash").review("https://cos/x.jpg", "鲜肉馄饨", false);

        assertFalse(v.pass());
        assertEquals("画面出现生肉块，混装在一个盘子里", v.reason());
    }

    @Test
    void gateFailureDegradesToAllow() {
        when(llm.isConfigured()).thenReturn(true);
        // 质检模型挂了/返回垃圾 → 放行，绝不能卡死生图链路
        when(llm.complete(any(LlmRequest.class))).thenReturn(LlmResult.failed(LlmResult.Failure.TIMEOUT, 1, 5));
        assertTrue(gate("glm-4v-flash").review("https://cos/x.jpg", "番茄牛腩", false).pass());

        when(llm.complete(any(LlmRequest.class))).thenReturn(ok("这不是 JSON"));
        assertTrue(gate("glm-4v-flash").review("https://cos/x.jpg", "番茄牛腩", false).pass());
    }
}
