package com.moodrecipe.backend.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentOutputGateTest {

    private final AgentOutputGate gate = new AgentOutputGate();

    @Test
    void fixReturnsEmptyResultForBlankInput() {
        AgentOutputGate.GateResult result = gate.fix(null);
        assertEquals("", result.text());
        assertTrue(result.violationTypes().isEmpty());

        AgentOutputGate.GateResult blank = gate.fix("   ");
        assertTrue(blank.text().isBlank());
        assertTrue(blank.violationTypes().isEmpty());
    }

    @Test
    void fixReplacesInternalEnums() {
        AgentOutputGate.GateResult result = gate.fix("试试 SAVE 方案，或者 ASK_PEOPLE");
        assertTrue(result.text().contains("省钱"));
        assertFalse(result.text().contains("SAVE"));
        assertTrue(result.violationTypes().contains("reply.enum"));
    }

    @Test
    void fixStripsControlCharacters() {
        AgentOutputGate.GateResult result = gate.fix("你好\u0007世界\uFFFD");
        assertFalse(result.text().contains("\u0007"));
        assertFalse(result.text().contains("\uFFFD"));
        assertTrue(result.violationTypes().contains("reply.dirty"));
    }

    @Test
    void fixTruncatesToTwoSentences() {
        AgentOutputGate.GateResult result = gate.fix("第一句。第二句。第三句应该被截断。");
        assertTrue(result.text().contains("第一句"));
        assertTrue(result.text().contains("第二句"));
        assertFalse(result.text().contains("第三句"));
        assertTrue(result.violationTypes().contains("reply.too_long"));
    }

    @Test
    void fixKeepsCleanShortText() {
        AgentOutputGate.GateResult result = gate.fix("今天做什么菜好呢？");
        assertEquals("今天做什么菜好呢？", result.text());
        assertTrue(result.violationTypes().isEmpty());
    }

    @Test
    void recordAccumulatesViolations() {
        gate.record("reply.enum");
        gate.record("reply.enum");
        gate.record("reply.enum");
        assertTrue(gate.disciplineText().contains("内部枚举"));
    }

    @Test
    void disciplineTextEmptyBelowThreshold() {
        gate.record("reply.enum");
        gate.record("reply.enum");
        assertTrue(gate.disciplineText().isEmpty());
    }

    @Test
    void disciplineTextIgnoresUnknownViolation() {
        gate.record("reply.enum");
        gate.record("reply.enum");
        gate.record("reply.enum");
        gate.record("unknown.type");
        gate.record("unknown.type");
        gate.record("unknown.type");
        String discipline = gate.disciplineText();
        assertTrue(discipline.contains("内部枚举"));
        assertFalse(discipline.contains("unknown.type"));
    }
}
