package com.moodrecipe.backend.service;

import com.moodrecipe.backend.entity.CookingKnowledgeChunk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CookingKnowledgeServiceTest {
    @Test
    void ranksOnlyMatchingCookingEvidence() {
        CookingKnowledgeChunk safe = chunk("食物要彻底做熟", "FOOD_SAFETY", "熟透,中心温度,温度计");
        CookingKnowledgeChunk unrelated = chunk("蔬菜沥水", "TECHNIQUE", "蔬菜,沥干");

        assertTrue(CookingKnowledgeService.score(safe, "鸡肉怎样才算熟透 中心温度")
                > CookingKnowledgeService.score(unrelated, "鸡肉怎样才算熟透 中心温度"));
        assertEquals(0, CookingKnowledgeService.score(unrelated, "排骨熟透了吗"));
    }

    private CookingKnowledgeChunk chunk(String title, String category, String keywords) {
        CookingKnowledgeChunk value = new CookingKnowledgeChunk();
        value.setTitle(title); value.setCategory(category); value.setKeywords(keywords);
        return value;
    }
}
