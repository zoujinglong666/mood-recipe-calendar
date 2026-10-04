package com.moodrecipe.backend.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentFactTest {

    @Test
    void explicitFactIsWorthRemembering() {
        AgentFact f = AgentFact.explicit("people", "3", "用户说一家三口");
        assertTrue(f.worthRemembering());
        assertTrue(f.explicit());
        assertEquals("people=3（明确，用户说一家三口）", f.describe());
    }

    @Test
    void inferredFactWorthRememberingAboveThreshold() {
        AgentFact f = AgentFact.inferred("spice", "微辣", "多次点辣");
        assertFalse(f.explicit());
        assertTrue(f.worthRemembering());
    }

    @Test
    void lowConfidenceFactNotWorth() {
        AgentFact f = new AgentFact("people", "3", 0.3, true, "依据不足");
        assertFalse(f.worthRemembering());
    }

    @Test
    void equalsAndDescribe() {
        AgentFact a = new AgentFact("people", "3", 0.85, true, "用户说一家三口");
        AgentFact b = new AgentFact("people", "3", 0.85, true, "用户说一家三口");
        AgentFact c = new AgentFact("people", "4", 0.85, true, "用户说一家四口");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertTrue(a.describe().contains("明确"));
    }
}
