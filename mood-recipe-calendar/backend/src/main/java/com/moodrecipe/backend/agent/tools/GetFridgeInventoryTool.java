package com.moodrecipe.backend.agent.tools;

import com.moodrecipe.backend.agent.AgentTool;
import com.moodrecipe.backend.agent.ToolContext;
import com.moodrecipe.backend.agent.ToolResult;
import com.moodrecipe.backend.service.FridgeInventoryService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 只读查询用户当前冰箱库存；数量和临期状态必须来自数据库，不交给模型猜。 */
@Service
public class GetFridgeInventoryTool implements AgentTool {
    private final FridgeInventoryService inventory;

    public GetFridgeInventoryTool(FridgeInventoryService inventory) {
        this.inventory = inventory;
    }

    @Override public String name() { return "get_fridge_inventory"; }

    @Override public String description() {
        return "查询用户冰箱当前库存、数量、单位和临期状态。用户问冰箱里有什么时必须调用，不能凭空回答。";
    }

    @Override public String parametersJson() {
        return "{\"type\":\"object\",\"properties\":{}}";
    }

    @Override public ToolResult run(String argumentsJson, ToolContext context) {
        List<FridgeInventoryService.ItemView> items = inventory.list(context.openid());
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("count", items.size());
        output.put("items", items.stream().map(item -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", item.id());
            value.put("name", item.name());
            value.put("quantity", item.quantity());
            value.put("unit", item.unit());
            value.put("status", item.status());
            value.put("daysLeft", item.daysLeft());
            return value;
        }).toList());
        return ToolResult.ok(context.json(), output);
    }
}
