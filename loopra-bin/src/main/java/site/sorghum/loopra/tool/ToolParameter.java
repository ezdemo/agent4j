package site.sorghum.loopra.tool;

import java.util.Collections;
import java.util.List;

/**
 * 工具参数定义——描述一个参数的名称、类型、是否必填及含义。
 * <p>
 * 对应 Tool-Use 协议中的 {@code parameters} JSON Schema 字段。
 * 支持嵌套结构：type=object 时使用 {@code properties} 定义子参数，
 * type=array 时使用 {@code items} 定义元素类型。
 * </p>
 *
 * @param name         参数名，如 "path"、"lineId"
 * @param type         参数类型：string / int / boolean / number / array / object
 * @param required     是否必填
 * @param description  参数含义描述
 * @param defaultValue 默认值（可选），无默认值时为 null
 * @param properties   当 type=object 时，子参数定义列表；非 object 时为 null
 * @param items        当 type=array 时，数组元素的参数定义；非 array 时为 null
 * @author Sorghum
 */
public record ToolParameter(String name, String type, boolean required, String description, String defaultValue,
                            List<ToolParameter> properties, ToolParameter items) {

    /**
     * 简单参数构造器（用于 string / int / boolean 等原始类型）。
     *
     * @param name        参数名
     * @param type        参数类型
     * @param required    是否必填
     * @param description 参数含义描述
     */
    public ToolParameter(String name, String type, boolean required, String description) {
        this(name, type, required, description, null, null, null);
    }

    /**
     * 简单参数构造器（带默认值）。
     *
     * @param name         参数名
     * @param type         参数类型
     * @param required     是否必填
     * @param description  参数含义描述
     * @param defaultValue 默认值
     */
    public ToolParameter(String name, String type, boolean required, String description, String defaultValue) {
        this(name, type, required, description, defaultValue, null, null);
    }

    // ==================== 工厂方法 ====================

    /**
     * 创建 object 类型参数。
     *
     * @param name        参数名
     * @param required    是否必填
     * @param description 参数含义描述
     * @param properties  子参数定义列表
     * @return object 类型的 ToolParameter
     */
    public static ToolParameter objectParam(String name, boolean required, String description,
                                            List<ToolParameter> properties) {
        return new ToolParameter(name, "object", required, description, null,
                properties != null ? Collections.unmodifiableList(properties) : List.of(), null);
    }

    /**
     * 创建 array 类型参数。
     *
     * @param name        参数名
     * @param required    是否必填
     * @param description 参数含义描述
     * @param items       数组元素的参数定义
     * @return array 类型的 ToolParameter
     */
    public static ToolParameter arrayParam(String name, boolean required, String description, ToolParameter items) {
        return new ToolParameter(name, "array", required, description, null, null, items);
    }

    // ==================== 工具方法 ====================

    /**
     * 判断当前参数是否为 object 类型（有子属性）。
     */
    public boolean isObject() {
        return "object".equals(type) && properties != null && !properties.isEmpty();
    }

    /**
     * 判断当前参数是否为 array 类型（有 items 定义）。
     */
    public boolean isArray() {
        return "array".equals(type) && items != null;
    }

    @Override
    public String toString() {
        return name + ":" + type + (required ? "*" : "?") + " — " + description;
    }
}
