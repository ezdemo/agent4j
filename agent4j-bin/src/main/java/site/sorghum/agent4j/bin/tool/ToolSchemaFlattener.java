package site.sorghum.agent4j.bin.tool;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Schema 展平工具 —— 分析嵌套深度和叶子数，必要时展平 JSON Schema。
 * <p>
 * 当叶子数 &gt; 10 或深度 &gt; 2 时，
 * 将嵌套 properties 展平为点号分隔的顶层属性，以减少 token 开销。
 * </p>
 *
 * @author Sorghum
 */
public class ToolSchemaFlattener {

    /** 展平阈值：叶子数 */
    static final int MAX_LEAVES = 10;
    /** 展平阈值：深度 */
    static final int MAX_DEPTH = 2;

    /**
     * 分析 schema 是否需要展平。叶子数 &gt; 10 或深度 &gt; 2 时展平。
     *
     * @param schema 原始 JSON Schema
     * @return 展平后的 schema（无需展平时返回原值）
     */
    public Map<String, Object> maybeFlattenSchema(Map<String, Object> schema) {
        int[] stats = analyzeObject(schema);
        int depth = stats[0];
        int leaves = stats[1];
        if (leaves <= MAX_LEAVES && depth <= MAX_DEPTH) return schema;

        Map<String, Object> flat = new LinkedHashMap<>();
        flat.put("type", "object");
        Map<String, Object> flatProps = new LinkedHashMap<>();
        flattenProperties((Map<String, Object>) schema.get("properties"), "", flatProps);
        flat.put("properties", flatProps);
        if (schema.containsKey("required")) {
            flat.put("required", schema.get("required"));
        }
        return flat;
    }

    @SuppressWarnings("unchecked")
    private int[] analyzeObject(Map<String, Object> obj) {
        int maxDepth = 1;
        int leafCount = 0;
        if (obj.containsKey("properties")) {
            Map<String, Object> props = (Map<String, Object>) obj.get("properties");
            for (Map.Entry<String, Object> e : props.entrySet()) {
                if (e.getValue() instanceof Map) {
                    Map<String, Object> val = (Map<String, Object>) e.getValue();
                    if ("object".equals(val.get("type")) && val.containsKey("properties")) {
                        int[] sub = analyzeObject(val);
                        maxDepth = Math.max(maxDepth, sub[0] + 1);
                        leafCount += sub[1];
                    } else {
                        leafCount++;
                    }
                }
            }
        }
        return new int[]{maxDepth, leafCount};
    }

    @SuppressWarnings("unchecked")
    private void flattenProperties(Map<String, Object> props, String prefix,
                                   Map<String, Object> out) {
        for (Map.Entry<String, Object> e : props.entrySet()) {
            String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            if (e.getValue() instanceof Map) {
                Map<String, Object> val = (Map<String, Object>) e.getValue();
                if ("object".equals(val.get("type")) && val.containsKey("properties")) {
                    flattenProperties((Map<String, Object>) val.get("properties"), key, out);
                } else {
                    out.put(key, val);
                }
            } else {
                Map<String, Object> leaf = new LinkedHashMap<>();
                leaf.put("type", "string");
                out.put(key, leaf);
            }
        }
    }
}
