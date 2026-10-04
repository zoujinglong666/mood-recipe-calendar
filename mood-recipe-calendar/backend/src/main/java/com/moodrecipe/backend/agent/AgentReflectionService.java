package com.moodrecipe.backend.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.service.AgentConversationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自进化：把用户对会话的真实评价变成锅仔的教训。
 *
 * 流程对齐 pi 的思想——模型自己复盘（代码不写规则），代码只负责三件事：
 * 提供会话记录、把教训滚进记忆（lesson.1~5 滑动窗口）、在之后每轮对话注入。
 * 用户点一次「不满意」，下一轮对话的行为就真的不一样。
 */
@Service
public class AgentReflectionService {

    private static final Logger log = LoggerFactory.getLogger(AgentReflectionService.class);

    /** 教训滑动窗口：lesson.1 ~ lesson.5，新教训覆盖旧槽位，天然淘汰过期的。 */
    private static final int LESSON_SLOTS = 5;
    /** 同一用户评分反思的节流：60 秒内多次评分只复盘一次，防止刷分刷爆模型调用。 */
    private static final long THROTTLE_MS = 60_000L;

    private final LlmClient llm;
    private final AgentMemoryStore store;
    private final AgentConversationService conversations;
    private final ObjectMapper json;
    private final Map<String, Long> lastReflectAt = new ConcurrentHashMap<>();

    public AgentReflectionService(LlmClient llm, AgentMemoryStore store,
                                  AgentConversationService conversations, ObjectMapper json) {
        this.llm = llm;
        this.store = store;
        this.conversations = conversations;
        this.json = json;
    }

    /** 评分入口：异步执行，评分接口立即返回，复盘失败静默（不打扰用户）。 */
    public void reflectOnRatingAsync(String openid, String rating, String comment) {
        if (openid == null || openid.isBlank() || !llm.isConfigured()) return;
        long now = System.currentTimeMillis();
        Long last = lastReflectAt.get(openid);
        if (last != null && now - last < THROTTLE_MS) return;
        lastReflectAt.put(openid, now);
        Thread worker = new Thread(() -> {
            try {
                reflectOnRating(openid, rating, comment);
            } catch (Exception error) {
                log.info("会话复盘失败（静默降级）：{}", error.getMessage());
            }
        }, "agent-reflect-" + openid.substring(Math.max(0, openid.length() - 6)));
        worker.setDaemon(true);
        worker.start();
    }

    /** 同步复盘：读最近一次会话记录 → 模型提炼教训 → 滚动写入记忆。 */
    void reflectOnRating(String openid, String rating, String comment) {
        if (!llm.isConfigured()) return;
        String transcript = transcriptOf(openid);
        if (transcript == null || transcript.isBlank()) return;
        try {
            LlmResult result = llm.complete(LlmRequest.json("agent-reflect", AgentPrompts.system(),
                    AgentPrompts.reflect(transcript, rating == null ? "一般" : rating, comment), 0.2, 400, TimeoutTier.FAST));
            if (!result.ok()) return;
            JsonNode root = json.readTree(stripFence(result.text()));
            if (!root.isArray()) {
                root = root.path("lessons");
            }
            if (!root.isArray()) return;
            List<String> lessons = new ArrayList<>();
            for (JsonNode item : root) {
                String lesson = item.asText("").trim();
                if (!lesson.isEmpty()) lessons.add(AgentPrompts.budget(lesson, 40));
                if (lessons.size() >= 3) break;
            }
            int start = nextSlot(openid);
            for (int i = 0; i < lessons.size(); i++) {
                String key = "lesson." + ((start + i) % LESSON_SLOTS + 1);
                store.remember(AgentMemoryStore.RememberCommand.learned(openid, key, lessons.get(i),
                        "会话评分「" + (rating == null ? "一般" : rating) + "」后的复盘"));
            }
        } catch (Exception ignored) {
            // 复盘属于增强能力：任何失败都不影响主链路
        }
    }

    /** 之后每轮对话注入的经验教训：最近写入的优先。 */
    public String lessons(String openid) {
        if (openid == null || openid.isBlank()) return "";
        StringBuilder text = new StringBuilder();
        int count = 0;
        for (MemoryItem item : store.recall(openid, AgentMemoryStore.Scene.DIALOGUE, 40)) {
            if (!item.key().startsWith("lesson.")) continue;
            if (item.value() == null || item.value().isBlank()) continue;
            text.append("- ").append(item.value()).append('\n');
            if (++count >= 5) break;
        }
        return AgentPrompts.budget(text.toString(), 400);
    }

    /** 下一个要覆盖的槽位：取现有 lesson 编号最大值 +1，形成滚动窗口。 */
    private int nextSlot(String openid) {
        int max = 0;
        for (MemoryItem item : store.recall(openid, AgentMemoryStore.Scene.DIALOGUE, 40)) {
            if (!item.key().startsWith("lesson.")) continue;
            try {
                max = Math.max(max, Integer.parseInt(item.key().substring("lesson.".length())));
            } catch (RuntimeException ignored) {
                // 非数字槽位忽略
            }
        }
        return max % LESSON_SLOTS;
    }

    private String transcriptOf(String openid) {
        return conversations.latest(openid)
                .map(snapshot -> {
                    StringBuilder text = new StringBuilder();
                    for (AgentConversationService.TranscriptMessage message : snapshot.messages()) {
                        if (message == null || message.text() == null || message.text().isBlank()) continue;
                        text.append("user".equals(message.role()) ? "用户：" : "锅仔：")
                                .append(message.text().trim()).append('\n');
                    }
                    return AgentPrompts.budget(text.toString(), 2200);
                })
                .orElse("");
    }

    private static String stripFence(String content) {
        if (content == null) return "";
        return content.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
    }
}
