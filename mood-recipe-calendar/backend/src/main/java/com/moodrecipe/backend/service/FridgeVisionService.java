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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 冰箱拍照识别（会员专享）。
 *
 * 职责边界（重要）：
 * - 大模型**只负责看图说出食材名称**，不决定日期、不决定数量；
 * - 保质期一律由 {@link FoodShelfLifeCatalog} 按名称给出基准天数，再以购买日推算，
 *   结果确定、可复现，杜绝模型编造日期；
 * - 识别结果**不直接入库**，只作为「确认清单」返回，用户勾选/修改后才写库存。
 *
 * 会员校验在调用方（Controller）完成，本服务只做识别编排。
 */
@Service
public class FridgeVisionService {

    private static final Logger log = LoggerFactory.getLogger(FridgeVisionService.class);
    private static final int MAX_ITEMS = 12;

    private final LlmClient llm;
    private final FoodShelfLifeCatalog shelfLife;
    private final ObjectMapper json;
    private final String visionModel;

    public FridgeVisionService(LlmClient llm,
                               FoodShelfLifeCatalog shelfLife,
                               ObjectMapper json,
                               @Value("${ai.vision.model:}") String visionModel) {
        this.llm = llm;
        this.shelfLife = shelfLife;
        this.json = json;
        this.visionModel = visionModel == null ? "" : visionModel.trim();
    }

    /** 识别是否可用（视觉模型是否配置）。 */
    public boolean available() {
        return !visionModel.isBlank() && llm.isConfigured();
    }

    /**
     * 单条识别结果：食材名 + 依据常识库算出的保质期，供用户确认。
     *
     * storageLevel / storageTip：若该食材不适合放冰箱则为非空，前端据此做入库前提醒。
     * storageLevel 取值 AVOID（不建议）/ WORSE（会加速变质），null 表示可正常冷藏。
     */
    public record RecognizedItem(String name, int shelfLifeDays, String category,
                                 LocalDate expiresOn, boolean shelfLifeMatched, String confidence,
                                 String storageLevel, String storageTip) {}

    public record RecognizeResult(List<RecognizedItem> items, boolean degraded, String notice) {
        static RecognizeResult empty(String notice) {
            return new RecognizeResult(List.of(), true, notice);
        }
    }

    /**
     * 识别冰箱照片。
     *
     * @param imageUrl 公网可访问的图片地址（上传后由 COS 返回）
     */
    public RecognizeResult recognize(String imageUrl) {
        if (!available()) {
            return RecognizeResult.empty("识别服务暂不可用，请稍后再试或手动录入");
        }
        if (imageUrl == null || imageUrl.isBlank()) {
            return RecognizeResult.empty("没有拿到照片，请重新拍摄");
        }

        LlmRequest request = LlmRequest.vision(
                "fridge.vision", SYSTEM, buildPrompt(), imageUrl, visionModel, 900);

        LlmResult result = llm.complete(request);
        if (!result.ok()) {
            log.warn("冰箱识别失败 failure={}", result.failure());
            return RecognizeResult.empty("暂时没看清照片里的食材，请换个角度再拍一张");
        }
        List<RecognizedItem> items = parse(result.response().content(), LocalDate.now());
        if (items.isEmpty()) {
            return RecognizeResult.empty("没认出可存放的食材，可以靠近一点再拍一张");
        }
        return new RecognizeResult(items, false, null);
    }

    private List<RecognizedItem> parse(String content, LocalDate purchasedOn) {
        List<RecognizedItem> items = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        try {
            JsonNode root = json.readTree(content);
            JsonNode array = root.isArray() ? root : root.path("items");
            for (JsonNode node : array) {
                String name = node.path("name").asText("").trim();
                if (name.isBlank() || !seen.add(name)) continue;
                // 模型只给名字（可选给 category），保质期由常识库决定
                String hintCategory = node.path("category").asText("");
                FoodShelfLifeCatalog.ShelfLife life = shelfLife.lookup(name);
                int days = life.matched() ? life.days() : shelfLife.defaultDays(hintCategory);
                // 不宜冷藏提醒（与保质期正交的独立维度），命中则前端会做入库前提示
                FoodShelfLifeCatalog.StorageAdvice advice = shelfLife.storageAdvice(name);
                items.add(new RecognizedItem(
                        name, days, life.category(),
                        purchasedOn.plusDays(days),
                        life.matched(),
                        node.path("confidence").asText("MEDIUM"),
                        advice == null ? null : advice.level(),
                        advice == null ? null : advice.tip()));
                if (items.size() >= MAX_ITEMS) break;
            }
        } catch (Exception e) {
            log.warn("冰箱识别结果解析失败: {}", e.getClass().getSimpleName());
        }
        return items;
    }

    private String buildPrompt() {
        return "请识别这张冰箱/食材照片里的可食用食材。" +
                "只输出 JSON 对象 {\"items\":[{\"name\":\"食材名\",\"category\":\"类别\",\"confidence\":\"HIGH/MEDIUM/LOW\"}]}，" +
                "类别从 " + shelfLife.categories() + " 中选。" +
                "name 用最常见的普通话名称（如「西红柿」），不要凭空猜测看不到的食材，" +
                "不要编造数量或日期，最多 12 项。看不清就不要输出该项。";
    }

    private static final String SYSTEM =
            "你是一个只负责辨认食材的助手。你的唯一任务是看图说出食材名称与类别，" +
            "绝不判断保质期、日期或数量——这些由系统按规则计算。不确定的食材不要输出。";
}
