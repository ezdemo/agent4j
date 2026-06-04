package site.sorghum.agent4j.bin.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具定义——描述一个工具的元信息与执行函数。
 *
 * @param name        工具名（LLM 调用时使用）
 * @param description 工具描述（注入 system prompt / tool spec）
 * @param params      参数定义（name → {type, description, required}）
 * @param fn          执行函数，参数 Map → 结果字符串
 * @param readOnly    true = 只读，storm breaker 中变异调用会清除只读条目的窗口
 * @param stormExempt true = storm 豁免（廉价检查工具免检）
 * @param toolSpec    纯文本工具规范（用于嵌入 system prompt），为空时自动生成
 * @author Sorghum
 */
public record ToolDef(String name, String description, List<ParamDef> params, ToolFn fn,
                      boolean readOnly, boolean stormExempt, String toolSpec) {

    public ToolDef(String name, String description, List<ParamDef> params, ToolFn fn) {
        this(name, description, params, fn, false, false, null);
    }

    public ToolDef(String name, String description, List<ParamDef> params, ToolFn fn,
                   boolean readOnly, boolean stormExempt) {
        this(name, description, params, fn, readOnly, stormExempt, null);
    }

    /**
     * 生成 OpenAI function-calling 格式的 parameters schema
     */
    public Map<String, Object> toParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> props = new LinkedHashMap<>();
        List<String> required = new java.util.ArrayList<>();
        for (ParamDef p : params) {
            props.put(p.name(), p.toSchemaProp());
            if (p.required()) required.add(p.name());
        }

        schema.put("properties", props);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    /**
     * 生成纯文本格式的工具规范（用于嵌入 system prompt）。
     * 返回 toolSpec 字段值，若为空则返回空字符串。
     */
    public String toToolSpec() {
        return toolSpec != null && !toolSpec.isEmpty() ? toolSpec : "";
    }

    /**
     * 工具执行函数
     */
    @FunctionalInterface
    public interface ToolFn {
        String call(Map<String, Object> args);
    }

    /**
     * 参数定义——支持扁平及嵌套结构（object 类型含 properties，array 类型含 items）。
     */
    public record ParamDef(String name, String type, String description, boolean required,
                           List<ParamDef> properties, ParamDef items) {

        // ==================== 扁平参数工厂（向后兼容） ====================

        public static ParamDef of(String name, String type, String description) {
            return new ParamDef(name, type, description, false, null, null);
        }

        public static ParamDef required(String name, String type, String description) {
            return new ParamDef(name, type, description, true, null, null);
        }

        // ==================== 嵌套参数工厂 ====================

        /**
         * 创建 object 类型参数。
         *
         * @param name        参数名
         * @param description 参数描述
         * @param required    是否必填
         * @param properties  子参数定义
         * @return object 类型的 ParamDef
         */
        public static ParamDef object(String name, String description, boolean required,
                                      List<ParamDef> properties) {
            return new ParamDef(name, "object", description, required, properties, null);
        }

        /**
         * 创建 array 类型参数。
         *
         * @param name        参数名
         * @param description 参数描述
         * @param required    是否必填
         * @param items       数组元素的参数定义
         * @return array 类型的 ParamDef
         */
        public static ParamDef array(String name, String description, boolean required, ParamDef items) {
            return new ParamDef(name, "array", description, required, null, items);
        }

        // ==================== JSON Schema 输出 ====================

        /**
         * 将当前参数定义转为 JSON Schema 属性对象。
         */
        @SuppressWarnings("unchecked")
        public Map<String, Object> toSchemaProp() {
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", type);
            prop.put("description", description);

            // object 类型：递归生成子 properties
            if ("object".equals(type) && properties != null && !properties.isEmpty()) {
                Map<String, Object> nestedProps = new LinkedHashMap<>();
                List<String> nestedRequired = new java.util.ArrayList<>();
                for (ParamDef sub : properties) {
                    nestedProps.put(sub.name(), sub.toSchemaProp());
                    if (sub.required()) nestedRequired.add(sub.name());
                }
                prop.put("properties", nestedProps);
                if (!nestedRequired.isEmpty()) {
                    prop.put("required", nestedRequired);
                }
            }

            // array 类型：递归生成 items
            if ("array".equals(type) && items != null) {
                prop.put("items", items.toSchemaProp());
            }

            return prop;
        }
    }
}
