package site.sorghum.loopra.extra.demo;

import site.sorghum.cutin.core.context.LoopContext;
import site.sorghum.cutin.core.tool.Tool;
import site.sorghum.cutin.core.tool.ToolCall;
import site.sorghum.cutin.core.tool.ToolDefinition;
import site.sorghum.cutin.core.tool.ToolResult;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 示例工具：获取服务器当前时间。
 *
 * <p>演示工具如何声明 JSON Schema 参数并返回结果（成功/失败两种路径）。</p>
 */
public final class DemoServerTimeTool implements Tool {

    @Override
    public String id() {
        return "demo-server-time";
    }

    @Override
    public ToolDefinition definition() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("format", Map.of(
            "type", "string",
            "description", "Java DateTimeFormatter 模式，默认 yyyy-MM-dd HH:mm:ss"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return new ToolDefinition(
            "demo-server-time",
            "获取服务器当前时间（可指定输出格式）",
            schema);
    }

    @Override
    public ToolResult call(ToolCall call, LoopContext context) {
        String format = String.valueOf(call.arguments().getOrDefault("format", "yyyy-MM-dd HH:mm:ss"));
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
            return ToolResult.success(call.id(), LocalDateTime.now().format(formatter));
        } catch (RuntimeException exception) {
            return ToolResult.failure(call.id(), "时间格式无效: " + exception.getMessage());
        }
    }
}
