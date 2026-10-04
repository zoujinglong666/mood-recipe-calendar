package com.moodrecipe.backend.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentJsonTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void parseObjectReadsFields() {
        JsonNode obj = AgentJson.parse(json, "{\"name\":\"番茄\",\"count\":3}");
        assertEquals("番茄", AgentJson.text(obj, "name"));
        assertEquals("3", AgentJson.text(obj, "count"));
        assertEquals("", AgentJson.text(obj, "missing"));
    }

    @Test
    void parseReturnsObjectNodeOnBadJson() {
        JsonNode obj = AgentJson.parse(json, "not json");
        assertNotNull(obj);
        assertEquals("", AgentJson.text(obj, "x"));
    }

    @Test
    void stringsReadsArrayField() {
        JsonNode obj = AgentJson.parse(json, "{\"list\":[\"川菜\",\"粤菜\"]}");
        assertEquals(List.of("川菜", "粤菜"), AgentJson.strings(obj, "list"));
        assertEquals(List.of(), AgentJson.strings(obj, "missing"));
    }

    @Test
    void readStringsFromJsonArray() {
        assertEquals(List.of("辣", "清淡"), AgentJson.readStrings(json, "[\"辣\",\"清淡\"]"));
    }

    @Test
    void readStringsFallsBackToRawForNonArray() {
        assertEquals(List.of("{\"a\":1}"), AgentJson.readStrings(json, "{\"a\":1}"));
    }

    @Test
    void readStringsEmptyForBlank() {
        assertTrue(AgentJson.readStrings(json, null).isEmpty());
        assertTrue(AgentJson.readStrings(json, "   ").isEmpty());
    }
}
