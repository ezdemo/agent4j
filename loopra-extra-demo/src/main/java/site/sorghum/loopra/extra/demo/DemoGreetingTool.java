package site.sorghum.loopra.extra.demo;

import site.sorghum.cutin.core.context.LoopContext;
import site.sorghum.cutin.core.tool.Tool;
import site.sorghum.cutin.core.tool.ToolCall;
import site.sorghum.cutin.core.tool.ToolDefinition;
import site.sorghum.cutin.core.tool.ToolResult;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 示例工具：问候（演示必填参数与文本返回）。
 */
public final class DemoGreetingTool implements Tool {

    @Override
    public String id() {
        return "demo-greeting";
    }

    @Override
    public ToolDefinition definition() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", Map.of(
            "type", "string",
            "description", "被问候的人名"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", java.util.List.of("name"));
        return new ToolDefinition(
            "demo-greeting",
            "向指定对象发送问候",
            schema);
    }

    @Override
    public ToolResult call(ToolCall call, LoopContext context) {
        Object name = call.arguments().get("name");
        if (name == null || String.valueOf(name).isBlank()) {
            return ToolResult.failure(call.id(), "缺少参数 name");
        }
        return ToolResult.success(call.id(), "你好, " + name + "！来自拓展包的问候 👋");
    }
}
