package com.moodrecipe.backend.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.moodrecipe.backend.agent.AgentJson;
import com.moodrecipe.backend.agent.AgentTool;
import com.moodrecipe.backend.agent.ToolContext;
import com.moodrecipe.backend.agent.ToolResult;
import com.moodrecipe.backend.service.GuidelineRagService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 膳食指南 RAG 检索工具。
 *
 * 给「锅仔」在回答营养/健康饮食问题时检索权威要点。只返回经过改写、来源可追溯的要点，
 * 不引用指南原文（符合合规边界）。未配置嵌入模型或未导入数据时优雅降级，不影响其它回答。
 */
@Service
public class GuidelineRagTool implements AgentTool {

    private final GuidelineRagService rag;

    public GuidelineRagTool(GuidelineRagService rag) {
        this.rag = rag;
    }

    @Override
    public String name() {
        return "guideline_rag";
    }

    @Override
    public String description() {
        return "检索《中国居民膳食指南》中关于膳食结构、食物摄入量、谷薯/蔬果/蛋白质/奶豆、油盐糖摄入上限、"
                + "饮水、运动与体重管理、三餐安排，以及孕妇/婴幼儿/儿童/老年/素食等特殊人群饮食的权威建议要点。"
                + "用于回答营养与健康饮食相关问题，提供经过改写、来源可追溯的要点，不引用原文。"
                + "当用户问及「每天该吃多少」「膳食宝塔」「油盐摄入上限」「怎么吃更健康」「某人群饮食注意」等时调用。";
    }

    @Override
    public String parametersJson() {
        return """
                {"type":"object","properties":{
                  "query":{"type":"string","description":"用户的营养/饮食问题或关键词"},
                  "maxResults":{"type":"integer","description":"返回条数，默认 5，最多 10"}
                },"required":["query"]}""";
    }

    @Override
    public ToolResult run(String argumentsJson, ToolContext context) {
        if (!rag.isReady()) {
            return ToolResult.failed("膳食指南知识库尚未就绪（未配置嵌入模型或未导入数据），请基于通用营养常识谨慎回答，不要编造指南原文。");
        }
        JsonNode args = AgentJson.parse(context.json(), argumentsJson);
        String query = AgentJson.text(args, "query");
        if (query.isBlank()) {
            return ToolResult.failed("缺少查询词 query。");
        }
        if (query.length() > 200) {
            return ToolResult.failed("查询词最多 200 字。");
        }
        int max = Math.max(1, Math.min(args.path("maxResults").asInt(5), 10));
        List<Map<String, Object>> hits = rag.search(query, max);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("query", query);
        out.put("count", hits.size());
        out.put("source", "中国居民膳食指南（要点经改写，来源可追溯）");
        out.put("results", hits);
        if (hits.isEmpty()) {
            out.put("notice", "未检索到高度相关条目，请基于通用营养常识回答并说明局限。");
        }
        return ToolResult.ok(context.json(), out);
    }
}
