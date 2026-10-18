package com.moodrecipe.backend.controller;

import com.moodrecipe.backend.common.ApiResponse;
import com.moodrecipe.backend.service.GuidelineRagService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.MessageDigest;
import java.util.Map;

/**
 * 膳食知识 RAG 索引的运维入口。
 * GuidelineRagService 启动时一次性把 guideline_chunks 全量加载进内存；
 * 离线脚本（scripts/build_guideline_rag.py）新增知识入库后，调本接口热重建索引，
 * 无需重启服务。
 *
 * 安全：同 /api/breakfast-pool/seed——白名单接口，必须携带 X-Ops-Token（OPS_TOKEN 配置）。
 */
@RestController
@RequestMapping("/api/ops/knowledge")
public class KnowledgeOpsController {

    private final GuidelineRagService guidelineRag;
    private final String opsToken;

    public KnowledgeOpsController(GuidelineRagService guidelineRag,
                                  @Value("${ops.token:}") String opsToken) {
        this.guidelineRag = guidelineRag;
        this.opsToken = opsToken;
    }

    /** 热重建膳食知识检索索引；返回重建后的知识点数量。 */
    @PostMapping("/rebuild")
    public ApiResponse<Map<String, Object>> rebuild(@RequestHeader(value = "X-Ops-Token", required = false) String token) {
        if (opsToken == null || opsToken.isBlank() || token == null
                || !MessageDigest.isEqual(opsToken.getBytes(java.nio.charset.StandardCharsets.UTF_8), token.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            return ApiResponse.error(403, "无效的运维令牌");
        guidelineRag.rebuild();
        return ApiResponse.ok(Map.of("ready", guidelineRag.isReady()));
    }
}
