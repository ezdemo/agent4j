package site.sorghum.agent4j.bin.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具定义——描述一个工具的元信息与执行函数。
 *
 * @param name        工具名（LLM 调用时使用）
 * @param description 工具描述（注入 system prompt / tool spec）
 * @param properties  参数定义（name → {type, description, required}）
 * @param fn          执行函数，参数 Map → 结果字符串
 *
 * @author Sorghum
 */
public class ToolDef {
    public final String name;
    public final String description;
    public final List<ParamDef> params;
    public final ToolFn fn;
    /** true = 只读，storm breaker 中变异调用会清除只读条目的窗口 */
    public final boolean readOnly;
    /** true = storm 豁免（廉价检查工具免检） */
    public final boolean stormExempt;

    public ToolDef(String name, String description, List<ParamDef> params, ToolFn fn) {
        this(name, description, params, fn, false, false);
    }

    public ToolDef(String name, String description, List<ParamDef> params, ToolFn fn,
                   boolean readOnly, boolean stormExempt) {
        this.name = name;
        this.description = description;
        this.params = params;
        this.fn = fn;
        this.readOnly = readOnly;
        this.stormExempt = stormExempt;
    }

    /** 生成 OpenAI function-calling 格式的 parameters schema */
    public Map<String, Object> toParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> props = new LinkedHashMap<>();
        java.util.List<String> required = new java.util.ArrayList<>();
        for (ParamDef p : params) {
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", p.type);
            prop.put("description", p.description);
            props.put(p.name, prop);
            if (p.required) required.add(p.name);
        }

        schema.put("properties", props);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    /** 参数定义 */
    public static class ParamDef {
        public final String name;
        public final String type;
        public final String description;
        public final boolean required;

        public ParamDef(String name, String type, String description, boolean required) {
            this.name = name;
            this.type = type;
            this.description = description;
            this.required = required;
        }

        public static ParamDef of(String name, String type, String description) {
            return new ParamDef(name, type, description, false);
        }

        public static ParamDef required(String name, String type, String description) {
            return new ParamDef(name, type, description, true);
        }
    }

    /** 工具执行函数 */
    @FunctionalInterface
    public interface ToolFn {
        String call(Map<String, Object> args);
    }
}
