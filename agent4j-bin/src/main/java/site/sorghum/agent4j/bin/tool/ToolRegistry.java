package site.sorghum.agent4j.bin.tool;

import java.util.*;

/**
 * 工具注册表 —— 管理工具定义的注册和查询。
 * <p>
 * 职责：工具注册、查询、生成 OpenAI function-calling 格式的工具列表。
 * 调度逻辑已移至 {@link ToolDispatcher}，schema 展平已移至 {@link ToolSchemaFlattener}。
 * </p>
 *
 * @author Sorghum
 */
public class ToolRegistry {

    private final Map<String, ToolDef> tools = new LinkedHashMap<>();
    private final ToolSchemaFlattener flattener = new ToolSchemaFlattener();

    public ToolRegistry register(ToolDef def) {
        tools.put(def.name, def);
        return this;
    }

    public ToolDef get(String name) {
        return tools.get(name);
    }

    public boolean has(String name) {
        return tools.containsKey(name);
    }

    /** 返回所有工具的不可变视图。 */
    public Map<String, ToolDef> all() {
        return Collections.unmodifiableMap(tools);
    }

    /**
     * 生成 OpenAI 格式的 tools 数组（按名称排序，保证 prompt prefix 稳定可缓存）。
     * Schema 展平委托给 {@link ToolSchemaFlattener}。
     */
    public List<Map<String, Object>> toOpenAiTools() {
        List<Map<String, Object>> list = new ArrayList<>();
        List<ToolDef> sorted = new ArrayList<>(tools.values());
        sorted.sort(Comparator.comparing(t -> t.name));
        for (ToolDef t : sorted) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", "function");
            Map<String, Object> func = new LinkedHashMap<>();
            func.put("name", t.name);
            func.put("description", t.description);
            Map<String, Object> schema = flattener.maybeFlattenSchema(t.toParametersSchema());
            func.put("parameters", schema);
            entry.put("function", func);
            list.add(entry);
        }
        return list;
    }
}
