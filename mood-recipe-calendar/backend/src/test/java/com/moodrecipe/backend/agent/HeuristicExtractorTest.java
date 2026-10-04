package com.moodrecipe.backend.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HeuristicExtractorTest {

    @Test
    void applySelectionParsesAllFields() {
        DialogueState.AgentState s = DialogueState.AgentState.empty();
        assertEquals(3, HeuristicExtractor.applySelection(s, "people=3").people());
        assertEquals("微辣", HeuristicExtractor.applySelection(s, "spice=微辣").spiceLevel());
        assertEquals(2, HeuristicExtractor.applySelection(s, "dishes=2").dishesPerDay());
        assertEquals("FITNESS", HeuristicExtractor.applySelection(s, "goal=FITNESS").healthGoal());
        assertEquals("SAVE", HeuristicExtractor.applySelection(s, "budget=SAVE").budget());
        assertTrue(HeuristicExtractor.applySelection(s, "elder=yes").hasElder());
        assertTrue(HeuristicExtractor.applySelection(s, "child=yes").hasChild());
        assertTrue(HeuristicExtractor.applySelection(s, "记住").cuisineConfirmed());
        assertEquals(List.of(0, 1, 2), HeuristicExtractor.applySelection(s, "days=0,1,2").cookingDays());
        assertEquals(List.of("番茄", "鸡蛋"),
                HeuristicExtractor.applySelection(s, "ingredients=番茄,鸡蛋").requestedIngredients());
    }

    @Test
    void applySelectionReturnsSameStateForBlank() {
        DialogueState.AgentState s = DialogueState.AgentState.empty();
        assertSame(s, HeuristicExtractor.applySelection(s, ""));
    }

    @Test
    void factsExtractsPeopleAndSpice() {
        List<AgentFact> facts = HeuristicExtractor.facts("3个人想吃微辣");
        assertTrue(facts.stream().anyMatch(f -> "people".equals(f.key()) && "3".equals(f.value())));
        assertTrue(facts.stream().anyMatch(f -> "spice".equals(f.key()) && "微辣".equals(f.value())));
    }

    @Test
    void factsExtractsRequestedIngredients() {
        List<AgentFact> facts = HeuristicExtractor.facts("用鸡肉和番茄做个菜");
        assertTrue(facts.stream().anyMatch(f -> "requestedIngredients".equals(f.key()) && f.value().contains("鸡肉")));
    }

    @Test
    void factsInfersCuisineFromCity() {
        List<AgentFact> facts = HeuristicExtractor.facts("我老家在抚州");
        assertTrue(facts.stream().anyMatch(f -> "favoriteCuisine".equals(f.key()) && "赣菜".equals(f.value())));
    }

    @Test
    void factsEmptyForBlank() {
        assertTrue(HeuristicExtractor.facts("").isEmpty());
    }

    @Test
    void cuisineByCityAndKeyword() {
        assertEquals("赣菜", HeuristicExtractor.cuisine("我老家在抚州"));
        assertEquals("川菜", HeuristicExtractor.cuisine("今天想吃川菜"));
        assertNull(HeuristicExtractor.cuisine("随便吃"));
    }

    @Test
    void cuisinesReturnsKnownSet() {
        assertTrue(HeuristicExtractor.cuisines().contains("川菜"));
        assertTrue(HeuristicExtractor.cuisines().contains("粤菜"));
    }
}
