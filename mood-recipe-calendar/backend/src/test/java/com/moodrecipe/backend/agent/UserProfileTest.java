package com.moodrecipe.backend.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UserProfileTest {

    @Test
    void emptyProfileHasNoContent() {
        UserProfile p = UserProfile.empty("u1");
        assertEquals("u1", p.openid());
        assertNull(p.people());
        assertTrue(p.favoriteCuisines().isEmpty());
        assertFalse(p.preferSimple());
        assertEquals("暂无可用档案", p.summary());
    }

    @Test
    void summaryReflectsKnownFields() {
        UserProfile p = new UserProfile("u1", 3, null, List.of(), "微辣", null, null,
                null, null, List.of("川菜"), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), Map.of(), null, false, 0, List.of());
        String summary = p.summary();
        assertTrue(summary.contains("辣度=微辣"));
        assertTrue(summary.contains("用餐人数=3"));
        assertTrue(summary.contains("偏爱菜系=川菜"));
    }
}
