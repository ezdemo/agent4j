package site.sorghum.loopra.bin.model;

import org.noear.snack4.ONode;
import site.sorghum.cutin.core.tool.ToolDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 将 Loopra 的工具 JSON 定义转换为 Cutin ToolDefinition 列表。 */
final class CutinModelTools {

    private CutinModelTools() {
    }

    static List<ToolDefinition> toCutin(ONode tools) {
        if (tools == null || !tools.isArray()) {
            return List.of();
        }
        List<ToolDefinition> result = new ArrayList<>();
        for (ONode tool : tools.getArray()) {
            ONode function = tool.get("function");
            if (function == null || function.isNull()) {
                continue;
            }
            String name = function.get("name").getString();
            if (name == null || name.isEmpty()) {
                continue;
            }
            String description = function.get("description").getString();
            ONode parameters = function.get("parameters");
            Map<String, Object> schema = Map.of("type", "object");
            if (parameters != null && !parameters.isNull()) {
                Object bean = parameters.toBean(Map.class);
                if (bean instanceof Map<?, ?> map) {
                    Map<String, Object> converted = new java.util.HashMap<>();
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        converted.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    schema = converted;
                }
            }
            result.add(new ToolDefinition(
                name,
                description == null ? "" : description,
                schema
            ));
        }
        return result;
    }
}
