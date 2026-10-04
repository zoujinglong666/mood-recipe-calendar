package com.moodrecipe.backend.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PromptFilesTest {

    @Test
    void returnsBundledSystemPrompt() {
        String system = PromptFiles.get("system");
        assertNotNull(system);
        assertFalse(system.isBlank());
    }

    @Test
    void returnsBundledOrchestrateRules() {
        String rules = PromptFiles.get("orchestrate-rules");
        assertNotNull(rules);
        assertFalse(rules.isBlank());
    }

    @Test
    void returnsEmptyForUnknownPrompt() {
        String unknown = PromptFiles.get("this-prompt-does-not-exist-xyz");
        assertNotNull(unknown);
        assertTrue(unknown.isEmpty());
    }

    @Test
    void getReturnsEmptyForBlankName() {
        // null 会触发 ConcurrentHashMap NPE，因此仅校验空串：返回空串而非 null
        assertNotNull(PromptFiles.get(""));
        assertTrue(PromptFiles.get("").isEmpty());
    }

    @Test
    void cachingReturnsStableResult() {
        String a = PromptFiles.get("system");
        String b = PromptFiles.get("system");
        assertEquals(a, b);
    }
}
