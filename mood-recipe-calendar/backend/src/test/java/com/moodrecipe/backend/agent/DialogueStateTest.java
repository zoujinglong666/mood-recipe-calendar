package com.moodrecipe.backend.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DialogueStateTest {

    @Test
    void emptyStateHasNoFacts() {
        DialogueState.AgentState s = DialogueState.AgentState.empty();
        assertNull(s.people());
        assertNull(s.spiceLevel());
        assertTrue(s.cookingDays().isEmpty());
        assertFalse(s.cuisineConfirmed());
    }

    @Test
    void withMethodsReplaceFields() {
        DialogueState.AgentState base = DialogueState.AgentState.empty();
        DialogueState.AgentState updated = base.withSpiceLevel("微辣").withPeople(3);
        assertNull(base.spiceLevel());
        assertEquals("微辣", updated.spiceLevel());
        assertEquals(3, updated.people());
        // 未修改字段原样保留
        assertEquals(base.cookingDays(), updated.cookingDays());
    }

    @Test
    void withRequestedIngredientsCarriesList() {
        DialogueState.AgentState s = DialogueState.AgentState.empty()
                .withRequestedIngredients(List.of("番茄", "鸡蛋"));
        assertEquals(List.of("番茄", "鸡蛋"), s.requestedIngredients());
    }

    @Test
    void turnRecordAccessors() {
        DialogueState.Turn turn = new DialogueState.Turn("回复", "ASK_PEOPLE",
                DialogueState.AgentState.empty(), null, "原因", List.of(), List.of(), List.of(), List.of(), List.of());
        assertEquals("回复", turn.reply());
        assertEquals("ASK_PEOPLE", turn.action());
    }

    @Test
    void optionAndCardRecords() {
        DialogueState.Option o = new DialogueState.Option("川菜", "cuisine:川菜");
        DialogueState.Card c = new DialogueState.Card("OPTIONS", "标题", "描述", List.of(o));
        assertEquals("OPTIONS", c.type());
        assertEquals(1, c.options().size());
        assertEquals("川菜", c.options().get(0).label());
    }
}
