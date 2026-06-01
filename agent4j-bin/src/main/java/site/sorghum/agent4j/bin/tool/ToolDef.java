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
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", p.type());
            prop.put("description", p.description());
            props.put(p.name(), prop);
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
     * 参数定义
     */
    public record ParamDef(String name, String type, String description, boolean required) {
        public static ParamDef of(String name, String type, String description) {
            return new ParamDef(name, type, description, false);
        }

        public static ParamDef required(String name, String type, String description) {
            return new ParamDef(name, type, description, true);
        }
    }
}
