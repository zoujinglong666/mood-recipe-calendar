package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.agent.LlmClient;
import com.moodrecipe.backend.agent.LlmRequest;
import com.moodrecipe.backend.agent.LlmResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 生图视觉质检门：封面生成后用视觉模型看图打分，坏图（生肉、混装、插画感）带修改意见退回重拍。
 *
 * 设计原则：
 * - 质检是「门」不是「裁判」：视觉模型不可用/调用失败时一律放行（降级为现状），
 *   绝不因质检链路故障卡死生图；
 * - 判定只输出严格 JSON {pass, reason}，reason ≤50 字，作为重拍时的追加指令回灌给生图模型。
 */
@Service
public class ImageQualityGateService {

    private static final Logger log = LoggerFactory.getLogger(ImageQualityGateService.class);

    private final LlmClient llm;
    private final ObjectMapper json;
    private final String visionModel;

    public ImageQualityGateService(LlmClient llm,
                                   ObjectMapper json,
                                   @Value("${ai.vision.model:}") String visionModel) {
        this.llm = llm;
        this.json = json;
        this.visionModel = visionModel == null ? "" : visionModel.trim();
    }

    /** 质检是否启用（未配置视觉模型时恒放行，行为与接入前一致）。 */
    public boolean enabled() {
        return !visionModel.isBlank() && llm.isConfigured();
    }

    public record Verdict(boolean pass, String reason) {
        static Verdict allow(String reason) {
            return new Verdict(true, reason);
        }
    }

    /**
     * 看图质检。imageUrl 必须公网可访问（COS 地址）。
     * combo=true 表示组合餐（「主食 · 配饮品」），额外校验两碗分摆。
     */
    public Verdict review(String imageUrl, String dishName, boolean combo) {
        if (!enabled()) return Verdict.allow("quality gate disabled");
        if (imageUrl == null || imageUrl.isBlank()) return Verdict.allow("no image");

        LlmRequest request = LlmRequest.vision(
                "recipe-image.quality-gate", SYSTEM, buildPrompt(dishName, combo), imageUrl, visionModel, 300);
        LlmResult result = llm.complete(request);
        if (!result.ok()) {
            log.warn("封面质检调用失败 failure={}（放行）", result.failure());
            return Verdict.allow("gate unavailable");
        }
        return parse(result.response().content());
    }

    Verdict parse(String content) {
        try {
            JsonNode root = json.readTree(content);
            JsonNode verdict = root.has("pass") ? root : root.path("verdict");
            boolean pass = verdict.path("pass").asBoolean(true);
            String reason = verdict.path("reason").asText("").trim();
            if (pass) return new Verdict(true, "");
            return new Verdict(false, reason.isBlank() ? "画面与菜名要求不符" : reason);
        } catch (Exception e) {
            // 解析失败宁可放行：质检是增强项，不能成为出图链路的单点故障
            log.warn("封面质检结果解析失败（放行）: {}", e.getClass().getSimpleName());
            return Verdict.allow("unparseable verdict");
        }
    }

    private String buildPrompt(String dishName, boolean combo) {
        StringBuilder p = new StringBuilder("你是美食封面图质检员。这道菜是「").append(dishName == null ? "" : dishName).append("」。")
                .append("请严格检查这张图片是否满足以下全部条件：\n")
                .append("1. 食物全部是烹饪完成、可直接入口的成品——出现生肉块、生肉馅、生蛋液任何一项直接不通过；\n")
                .append("2. 画面是真实照片质感——插画、卡通、3D 渲染感直接不通过；\n")
                .append("3. 画面内容与菜名相符，没有与菜无关的杂物（调料瓶、包装、文字水印）；\n");
        if (combo) {
            p.append("4. 这是「主食 · 配饮品」组合餐：必须是两样食物各自成碗、分开摆放——")
                    .append("混装在同一容器、或饮品垫在主食下面，直接不通过；\n");
        }
        p.append("只输出 JSON 对象 {\"pass\":true/false,\"reason\":\"不通过时的修改意见（≤50字，直接可给生图模型）\"}，")
                .append("不要输出其它任何内容。");
        return p.toString();
    }

    private static final String SYSTEM =
            "你是严格的美食封面质检员，只输出 JSON，不做任何解释。你的意见会被直接用作重新生图的指令。";
}
