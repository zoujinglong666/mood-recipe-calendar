package com.moodrecipe.backend.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.agent.ToolContext;
import com.moodrecipe.backend.service.UsageQuotaService;
import com.moodrecipe.backend.service.search.SearchClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebSearchToolTest {
    private final ToolContext context = new ToolContext("user-1", "trace-1", new ObjectMapper());

    @Test
    void rejectsNonMemberBeforeCallingSearchProvider() {
        SearchClient client = mock(SearchClient.class);
        UsageQuotaService quota = mock(UsageQuotaService.class);
        when(quota.member("user-1")).thenReturn(false);

        var result = new WebSearchTool(client, quota).run("{\"query\":\"时令蔬菜\"}", context);

        assertTrue(result.failed());
        verifyNoInteractions(client);
    }

    @Test
    void returnsStructuredResultsForMember() {
        SearchClient client = mock(SearchClient.class);
        UsageQuotaService quota = mock(UsageQuotaService.class);
        when(quota.member("user-1")).thenReturn(true);
        when(client.available()).thenReturn(true);
        when(client.search("时令蔬菜", 3)).thenReturn(List.of(
                new SearchClient.SearchResult("标题", "摘要", "https://example.com")));

        var result = new WebSearchTool(client, quota).run("{\"query\":\"时令蔬菜\",\"maxResults\":3}", context);

        assertFalse(result.failed());
        assertTrue(result.outputJson().contains("https://example.com"));
    }

    @Test
    void rejectsOversizedQuery() {
        SearchClient client = mock(SearchClient.class);
        UsageQuotaService quota = mock(UsageQuotaService.class);
        when(quota.member("user-1")).thenReturn(true);
        String query = "x".repeat(201);

        var result = new WebSearchTool(client, quota).run("{\"query\":\"" + query + "\"}", context);

        assertTrue(result.failed());
        verifyNoInteractions(client);
    }
}
