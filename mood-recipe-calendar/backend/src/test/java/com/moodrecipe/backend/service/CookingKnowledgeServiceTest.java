package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.CookingKnowledgeChunk;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 烹饪知识库数据质量门禁：'?' 型乱码行绝不命中、绝不外露（2026-10-29「这次依据」卡片问号修复）。
 * 背景：latin1 连接导入的种子行中文全变字面 '?'，且 normalize() 剥掉 '?' 后
 * 标题变空串导致 query.contains("") 恒命中，任意提问都把它排进可靠依据。
 */
class CookingKnowledgeServiceTest {

    private CookingKnowledgeChunk chunk(String title, String content, String keywords, String sourceName) {
        CookingKnowledgeChunk chunk = new CookingKnowledgeChunk();
        chunk.setId(1L);
        chunk.setTitle(title);
        chunk.setContent(content);
        chunk.setCategory("FOOD_SAFETY");
        chunk.setKeywords(keywords);
        chunk.setSourceName(sourceName);
        chunk.setSourceUrl("https://example.org/guide");
        chunk.setReviewedAt(LocalDateTime.now());
        chunk.setEnabled(true);
        return chunk;
    }

    @Test
    void corruptedChunkIsNeverAuditable() {
        CookingKnowledgeChunk corrupted = chunk("???????", "????????????????", "?,?,?",
                "????????????????");
        assertFalse(CookingKnowledgeService.auditable(corrupted));
    }

    @Test
    void corruptedSourceNameAloneIsRejected() {
        CookingKnowledgeChunk halfBroken = chunk("食物要彻底做熟", "需要烧熟煮透，中心温度 70℃ 以上。",
                "熟透,中心温度", "???????????????");
        assertFalse(CookingKnowledgeService.auditable(halfBroken));
    }

    @Test
    void healthyChunkPassesGate() {
        CookingKnowledgeChunk healthy = chunk("食物要彻底做熟",
                "需要烧熟煮透的食品，中心温度应达到70℃以上。", "熟透,中心温度,温度计",
                "世界卫生组织：食品安全五大要点");
        assertTrue(CookingKnowledgeService.auditable(healthy));
    }

    @Test
    void corruptedTitleNormalizingToEmptyNeverScores() {
        // 修复点：normalize 把 '?' 剥成空串，query.contains("") 恒 true 曾给损坏行送满分
        CookingKnowledgeChunk corrupted = chunk("???????", "??????", "???",
                "???????????????");
        assertEquals(0, CookingKnowledgeService.score(corrupted, "盐什么时候放"));
    }

    @Test
    void healthyChunkStillScoresByTitleAndKeyword() {
        CookingKnowledgeChunk healthy = chunk("食物要彻底做熟",
                "需要烧熟煮透的食品，中心温度应达到70℃以上。", "熟透,中心温度,温度计",
                "世界卫生组织：食品安全五大要点");
        int score = CookingKnowledgeService.score(healthy, "肉怎么判断熟透 中心温度");
        assertTrue(score >= 5, "标题或关键词命中应得分，实际=" + score);
    }
}
