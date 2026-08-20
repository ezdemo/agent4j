package site.sorghum.loopra.bin.tool;

import org.noear.snack4.ONode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Removes Loopra runtime-injected arguments from model-visible tool schemas. */
public final class ToolSchemaSanitizer {

    private static final Set<String> RUNTIME_ARGUMENTS = Set.of("ctx", "__cwd");

    private ToolSchemaSanitizer() {
    }

    public static Map<String, Object> sanitize(String inputSchema) {
        Map<String, Object> schema = parseSchema(inputSchema);
        removeRuntimeProperties(schema);
        removeRuntimeRequirements(schema);
        return schema;
    }

    private static Map<String, Object> parseSchema(String inputSchema) {
        if (inputSchema == null || inputSchema.isBlank()) {
            return objectSchema();
        }
        try {
            Object parsed = ONode.ofJson(inputSchema).toBean(Map.class);
            if (parsed instanceof Map<?, ?> map) {
                Map<String, Object> schema = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    schema.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                return schema.isEmpty() ? objectSchema() : schema;
            }
        } catch (RuntimeException ignored) {
            // Invalid third-party schemas must not prevent the remaining tools from loading.
        }
        return objectSchema();
    }

    private static void removeRuntimeProperties(Map<String, Object> schema) {
        Object value = schema.get("properties");
        if (!(value instanceof Map<?, ?> properties)) {
            return;
        }
        Map<Object, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : properties.entrySet()) {
            if (!RUNTIME_ARGUMENTS.contains(String.valueOf(entry.getKey()))) {
                sanitized.put(entry.getKey(), entry.getValue());
            }
        }
        schema.put("properties", sanitized);
    }

    private static void removeRuntimeRequirements(Map<String, Object> schema) {
        Object value = schema.get("required");
        if (!(value instanceof List<?> required)) {
            return;
        }
        List<Object> sanitized = new ArrayList<>();
        for (Object name : required) {
            if (!RUNTIME_ARGUMENTS.contains(String.valueOf(name))) {
                sanitized.add(name);
            }
        }
        schema.put("required", sanitized);
    }

    private static Map<String, Object> objectSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        return schema;
    }
}
