package com.moodrecipe.backend.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.moodrecipe.backend.agent.AgentJson;
import com.moodrecipe.backend.agent.AgentMemoryStore;
import com.moodrecipe.backend.agent.AgentTool;
import com.moodrecipe.backend.agent.MenuQualityScorer;
import com.moodrecipe.backend.agent.ToolContext;
import com.moodrecipe.backend.agent.ToolResult;
import com.moodrecipe.backend.agent.UserProfile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对一整份周菜单做质量评分：营养结构、荤素比例、食材复用、难度与时长均衡、老人小孩适配。
 * 智能体拿到分数和具体问题后才知道该修哪里，而不是拍脑袋说"还不错"。
 */
@Service
public class ScoreMenuTool implements AgentTool {

    private final AgentMemoryStore store;
    private final MenuQualityScorer scorer = new MenuQualityScorer();

    public ScoreMenuTool(AgentMemoryStore store) {
        this.store = store;
    }

    @Override
    public String name() {
        return "score_menu_plan";
    }

    @Override
    public String description() {
        return "对一整份周菜单打分（0-100）并列出具体问题：营养结构、荤素比例、食材复用率、烹饪难度与时长、老人小孩适配。生成菜单后必须调用。";
    }

    @Override
    public String parametersJson() {
        return """
                {"type":"object","properties":{
                  "days":{"type":"array","description":"按天组织的菜单","items":{"type":"object","properties":{
                    "weekday":{"type":"integer"},
                    "dishes":{"type":"array","items":{"type":"object","properties":{
                      "name":{"type":"string"},
                      "role":{"type":"string"},
                      "ingredients":{"type":"array","items":{"type":"string"}},
                      "cookingTime":{"type":"integer"},
                      "difficulty":{"type":"string"}
                    }}}
                  }}}
                }}""";
    }

    @Override
    public ToolResult run(String argumentsJson, ToolContext context) {
        JsonNode args = AgentJson.parse(context.json(), argumentsJson);
        JsonNode daysNode = args.path("days");
        List<MenuQualityScorer.DayInput> days = new ArrayList<>();
        if (daysNode.isArray()) {
            for (JsonNode day : daysNode) {
                days.add(new MenuQualityScorer.DayInput(day.path("weekday").asInt(0),
                        AgentDishes.read(context.json(), day.path("dishes"))));
            }
        } else {
            days.add(new MenuQualityScorer.DayInput(0, AgentDishes.read(context.json(), args.path("dishes"))));
        }
        if (days.isEmpty()) {
            return ToolResult.failed("没有提供菜单内容");
        }
        UserProfile profile = store.profile(context.openid(), AgentMemoryStore.Scene.WEEKLY_PLAN);
        MenuQualityScorer.MenuQuality quality = scorer.evaluate(days, MenuQualityScorer.Constraints.from(profile));

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("score", quality.score());
        output.put("hasBlockingIssue", quality.hasHard());
        output.put("violationBrief", quality.violationBrief());
        output.put("stats", Map.of(
                "days", quality.stats().totalDays(),
                "vegetableDays", quality.stats().vegetableDays(),
                "proteinDays", quality.stats().proteinDays(),
                "meatVegRatio", quality.stats().meatVegRatio(),
                "reuseRate", quality.stats().reuseRate(),
                "avgMinutes", quality.stats().avgMinutes(),
                "heavyDishes", quality.stats().heavyDishes()));
        output.put("issues", quality.issues().stream().map(issue -> Map.of(
                "code", issue.code(),
                "severity", issue.severity().name(),
                "dish", issue.dish(),
                "message", issue.message())).toList());
        return ToolResult.ok(context.json(), output);
    }
}
