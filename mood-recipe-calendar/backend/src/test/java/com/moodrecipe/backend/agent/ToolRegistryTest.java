package com.moodrecipe.backend.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {
    @Test
    void retriesWebSearchOnceAfterProviderException() {
        AtomicInteger calls = new AtomicInteger();
        AgentTool search = new AgentTool() {
            public String name() { return "web_search"; }
            public String description() { return "test"; }
            public String parametersJson() { return "{}"; }
            public ToolResult run(String args, ToolContext context) {
                if (calls.incrementAndGet() == 1) throw new IllegalStateException("temporary");
                return ToolResult.ok("{\"results\":[]}");
            }
        };

        ToolResult result = new ToolRegistry(java.util.List.of(search)).execute(
                "web_search", "{}", new ToolContext("u", "t", new ObjectMapper()));

        assertFalse(result.failed());
        assertEquals(2, calls.get());
    }
}
