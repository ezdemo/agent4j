package site.sorghum.agent4j.tool;

/**
 * 工具参数定义——描述一个参数的名称、类型、是否必填及含义。
 * <p>
 * 对应 Tool-Use 协议中的 {@code parameters} JSON Schema 字段。
 * </p>
 *
 * @param name         参数名，如 "path"、"lineId"
 * @param type         参数类型：string / int / boolean / array / object
 * @param required     是否必填
 * @param description  参数含义描述
 * @param defaultValue 默认值（可选），无默认值时为 null
 *
 * @author Sorghum
 */
public record ToolParameter(String name, String type, boolean required, String description, String defaultValue) {

    public ToolParameter(String name, String type, boolean required, String description) {
        this(name, type, required, description, null);
    }

    @Override
    public String toString() {
        return name + ":" + type + (required ? "*" : "?") + " — " + description;
    }
}
