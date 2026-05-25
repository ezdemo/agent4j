package site.sorghum.agent4j.tool;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 工具参数定义——描述一个参数的名称、类型、是否必填及含义。
 * <p>
 * 对应 Tool-Use 协议中的 {@code parameters} JSON Schema 字段。
 * </p>
 *
 * @author Sorghum
 */
@Getter
@AllArgsConstructor
public class ToolParameter {

    /** 参数名，如 "path"、"lineId" */
    private final String name;

    /** 参数类型：string / int / boolean / array / object */
    private final String type;

    /** 是否必填 */
    private final boolean required;

    /** 参数含义描述 */
    private final String description;

    /** 默认值（可选），无默认值时为 null */
    private final String defaultValue;

    public ToolParameter(String name, String type, boolean required, String description) {
        this(name, type, required, description, null);
    }

    @Override
    public String toString() {
        return name + ":" + type + (required ? "*" : "?") + " — " + description;
    }
}
