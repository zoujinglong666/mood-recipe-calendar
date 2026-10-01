package com.moodrecipe.backend.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.moodrecipe.backend.agent.AgentJson;
import com.moodrecipe.backend.agent.AgentTool;
import com.moodrecipe.backend.agent.ToolContext;
import com.moodrecipe.backend.agent.ToolResult;
import com.moodrecipe.backend.service.UsageQuotaService;
import com.moodrecipe.backend.service.search.SearchClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 联网搜索（会员专享）：回答菜谱库之外的知识，如时令食材、食材常识、
 * 某道菜的做法变体、地方特色等。
 *
 * 会员校验：非会员直接返回失败结果（而不是抛异常），模型会据此告知用户这是会员能力。
 * 未配置搜索 key 时降级为"暂不可用"，不影响锅仔其他回答。
 */
@Service
public class WebSearchTool implements AgentTool {

    private final SearchClient searchClient;
    private final UsageQuotaService quota;

    public WebSearchTool(SearchClient searchClient, UsageQuotaService quota) {
        this.searchClient = searchClient;
        this.quota = quota;
    }

    @Override
    public String name() {
        return "web_search";
    }

    @Override
    public String description() {
        return "联网搜索菜谱库之外的实时或通用信息，例如时令食材、食材挑选与保存常识、"
                + "某道菜的不同做法、地方特色。仅当本地菜谱库无法回答时使用；"
                + "搜索结果为外部信息，引用时需注明来源链接。";
    }

    @Override
    public String parametersJson() {
        return """
                {"type":"object","properties":{
                  "query":{"type":"string","description":"要搜索的问题或关键词"},
                  "maxResults":{"type":"integer","description":"返回条数，默认 5，最多 8"}
                },"required":["query"]}""";
    }

    @Override
    public ToolResult run(String argumentsJson, ToolContext context) {
        // 会员闸门：联网为会员能力，非会员给出可读原因，交由模型转达
        if (context.openid() == null || context.openid().isBlank() || !quota.member(context.openid())) {
            return ToolResult.failed("联网搜索是会员功能，当前用户不可用。请告知用户开通会员后即可使用，并基于已有知识继续回答。");
        }
        JsonNode args = AgentJson.parse(context.json(), argumentsJson);
        String query = AgentJson.text(args, "query");
        if (query.isBlank()) {
            return ToolResult.failed("缺少搜索关键词 query。");
        }
        if (query.length() > 200) {
            return ToolResult.failed("搜索关键词最多 200 个字。");
        }
        if (!searchClient.available()) {
            return ToolResult.failed("联网搜索暂未配置，请基于已有菜谱知识回答，不要编造网上信息。");
        }
        int maxResults = Math.max(1, Math.min(args.path("maxResults").asInt(5), 8));

        List<SearchClient.SearchResult> results = searchClient.search(query, maxResults);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("query", query);
        output.put("count", results.size());
        List<Map<String, Object>> items = new ArrayList<>();
        for (SearchClient.SearchResult hit : results) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("title", hit.title());
            item.put("snippet", hit.snippet());
            item.put("url", hit.url());
            items.add(item);
        }
        output.put("results", items);
        if (results.isEmpty()) {
            output.put("notice", "没有搜到结果，请基于已有知识回答，并说明未找到实时信息。");
        }
        return ToolResult.ok(context.json(), output);
    }
}
