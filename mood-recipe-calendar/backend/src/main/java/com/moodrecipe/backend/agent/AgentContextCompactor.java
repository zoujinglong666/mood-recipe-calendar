package com.moodrecipe.backend.agent;

import com.moodrecipe.backend.service.AgentConversationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compaction（pi 思想）：长会话的较早往来自动压缩成摘要，喂给模型当背景，旧对话不失忆。
 *
 * 设计取舍：
 * - 摘要按用户缓存（openid），滚动合并——只对新增的旧消息增量压缩，不每轮重算；
 * - 模型调用失败时静默返回上一次的摘要或空串，绝不断主链路；
 * - 缓存只存内存，重启后首个长会话多花一次 FAST 调用重建，不值得为此加表。
 */
@Service
public class AgentContextCompactor {

    private static final Logger log = LoggerFactory.getLogger(AgentContextCompactor.class);

    /** 旧消息至少有这么多条才值得摘要：低于这个数直接全文给出更划算。 */
    private static final int MIN_OLD_MESSAGES = 6;
    /** 缓存上限：摘要很小，但避免极端情况下无限增长。 */
    private static final int CACHE_LIMIT = 2000;

    private final LlmClient llm;
    private final Map<String, Compaction> cache = new ConcurrentHashMap<>();

    private record Compaction(int coveredCount, String summary) {
    }

    public AgentContextCompactor(LlmClient llm) {
        this.llm = llm;
    }

    /**
     * 返回 history 中「较早往来」的摘要；不足或失败时返回空串。
     *
     * @param history     完整对话历史（含本轮用户消息）
     * @param recentCount 最近往来条数——这部分直接给模型看原文，不参与压缩
     */
    public String compact(String openid, List<AgentConversationService.TranscriptMessage> history, int recentCount) {
        if (openid == null || openid.isBlank() || history == null) return "";
        int oldCount = history.size() - Math.max(1, recentCount);
        if (oldCount < MIN_OLD_MESSAGES) return "";
        Compaction cached = cache.get(openid);
        if (cached != null && cached.coveredCount() >= oldCount && !cached.summary().isBlank()) {
            return cached.summary();
        }
        String previous = cached == null ? "" : cached.summary();
        String summary = summarize(previous, history.subList(0, oldCount));
        if (summary.isBlank()) return previous;
        if (cache.size() >= CACHE_LIMIT) cache.clear();
        cache.put(openid, new Compaction(oldCount, summary));
        return summary;
    }

    /** 让模型把较早往来压成一段摘要；任何失败返回空串，由调用方降级到旧缓存或无摘要。 */
    private String summarize(String previous, List<AgentConversationService.TranscriptMessage> oldMessages) {
        if (!llm.isConfigured()) return "";
        StringBuilder transcript = new StringBuilder();
        for (AgentConversationService.TranscriptMessage message : oldMessages) {
            if (message == null || message.text() == null || message.text().isBlank()) continue;
            transcript.append("user".equals(message.role()) ? "用户：" : "锅仔：")
                    .append(message.text().trim()).append('\n');
        }
        if (transcript.isEmpty()) return "";
        try {
            LlmResult result = llm.complete(LlmRequest.text("agent-compact", AgentPrompts.system(),
                    AgentPrompts.compact(previous, transcript.toString()), 0.2, 400, TimeoutTier.FAST));
            if (!result.ok()) {
                log.info("Compaction 摘要失败（静默降级）：{}", result.reason());
                return "";
            }
            return AgentPrompts.budget(result.text().trim(), 300);
        } catch (Exception error) {
            log.info("Compaction 摘要异常（静默降级）：{}", error.getMessage());
            return "";
        }
    }
}
