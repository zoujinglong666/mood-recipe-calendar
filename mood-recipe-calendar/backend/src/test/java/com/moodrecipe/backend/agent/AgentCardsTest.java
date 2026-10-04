package com.moodrecipe.backend.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentCardsTest {

    @Test
    void actionOfValueMapsSelectionTokens() {
        assertEquals("ASK_PEOPLE", AgentCards.actionOfValue("people=3"));
        assertEquals("ASK_SPICE", AgentCards.actionOfValue("spice=微辣"));
        assertEquals("ASK_DISHES", AgentCards.actionOfValue("dishes=2"));
        assertEquals("ASK_GOAL", AgentCards.actionOfValue("goal=BALANCED"));
        assertEquals("ASK_BUDGET", AgentCards.actionOfValue("budget=SAVE"));
        assertEquals("ASK_HOUSEHOLD", AgentCards.actionOfValue("household=elder"));
        assertEquals("ASK_DAYS", AgentCards.actionOfValue("days=0,1,2"));
        assertEquals("CONFIRM_CUISINE", AgentCards.actionOfValue("记住"));
        assertEquals("READY", AgentCards.actionOfValue("generate"));
        assertNull(AgentCards.actionOfValue("foobar"));
        assertNull(AgentCards.actionOfValue(null));
        assertNull(AgentCards.actionOfValue("   "));
    }

    @Test
    void defaultCardIncludesOtherOption() {
        DialogueState.Card people = AgentCards.defaultCard("ASK_PEOPLE", DialogueState.AgentState.empty());
        assertEquals("OPTIONS", people.type());
        assertFalse(people.options().isEmpty());
        assertTrue(people.options().stream().anyMatch(o -> "自己输入".equals(o.label())));
    }

    @Test
    void defaultCardForReadyHasNoOther() {
        DialogueState.Card ready = AgentCards.defaultCard("READY", DialogueState.AgentState.empty());
        assertEquals("READY", ready.type());
    }

    @Test
    void acceptReturnsNullForEmptyOptions() {
        DialogueState.AgentState state = DialogueState.AgentState.empty();
        assertNull(AgentCards.accept(null, state, "OPTIONS", "t", "d", null));
        assertNull(AgentCards.accept(null, state, "OPTIONS", "t", "d", List.of()));
    }

    @Test
    void acceptReturnsNullWhenOnlyOtherSentinel() {
        DialogueState.AgentState state = DialogueState.AgentState.empty();
        DialogueState.Card card = AgentCards.accept(null, state, "OPTIONS", "选口味", "描述",
                List.of(new DialogueState.Option("自己输入", "other")));
        assertNull(card);
    }

    @Test
    void acceptNormalizesAndAppendsOther() {
        DialogueState.AgentState state = DialogueState.AgentState.empty();
        List<DialogueState.Option> options = List.of(
                new DialogueState.Option("川菜", "cuisine:川菜"),
                new DialogueState.Option("粤菜", "cuisine:粤菜"));
        DialogueState.Card card = AgentCards.accept(null, state, "OPTIONS", "选菜系", "描述", options);
        assertEquals("OPTIONS", card.type());
        assertEquals("选菜系", card.title());
        assertEquals(3, card.options().size());
        assertTrue(card.options().stream().anyMatch(o -> "自己输入".equals(o.label())));
    }

    @Test
    void acceptCapsAtFiveMeaningfulOptions() {
        DialogueState.AgentState state = DialogueState.AgentState.empty();
        List<DialogueState.Option> options = List.of(
                new DialogueState.Option("一", "a"), new DialogueState.Option("二", "b"),
                new DialogueState.Option("三", "c"), new DialogueState.Option("四", "d"),
                new DialogueState.Option("五", "e"), new DialogueState.Option("六", "f"));
        DialogueState.Card card = AgentCards.accept(null, state, "OPTIONS", "t", "d", options);
        // 5 meaningful + 自己输入
        assertEquals(6, card.options().size());
    }
}
