package com.moodrecipe.backend.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentPromptsTest {

    @Test
    void budgetKeepsShortText() {
        assertEquals("短文本", AgentPrompts.budget("短文本", 20));
        assertEquals("", AgentPrompts.budget(null, 20));
        assertEquals("", AgentPrompts.budget("   ", 20));
    }

    @Test
    void budgetTruncatesLongText() {
        String longText = "一二三四五六七八九十".repeat(5); // 50 chars
        String result = AgentPrompts.budget(longText, 20);
        assertEquals(21, result.length());
        assertTrue(result.endsWith("…"));
    }

    @Test
    void fenceWrapsUserText() {
        String f = AgentPrompts.fence("想吃辣");
        assertTrue(f.contains("想吃辣"));
        assertTrue(f.contains("\"\"\""));
        assertTrue(f.startsWith("下面是用户刚说的一句话"));
    }

    @Test
    void orchestrateReturnsNonEmpty() {
        String p = AgentPrompts.orchestrate("用户想吃辣", "状态", "档案", "缺口", "冲突",
                "ASK_SPICE", "", "", "");
        assertFalse(p.isBlank());
        assertTrue(p.contains("用户想吃辣"));
    }

    @Test
    void reflectContainsTranscriptAndRating() {
        String p = AgentPrompts.reflect("用户：想吃辣", "满意", "不错");
        assertTrue(p.contains("用户：想吃辣"));
        assertTrue(p.contains("满意"));
    }

    @Test
    void compactContainsTranscript() {
        String p = AgentPrompts.compact("", "用户：你好");
        assertTrue(p.contains("用户：你好"));
    }

    @Test
    void systemPromptNonEmpty() {
        assertFalse(AgentPrompts.system().isBlank());
    }
}
