package site.sorghum.agent4j.bin.tool;

import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 工具定义辅助类——提取公共的参数映射和结果格式化方法。
 *
 * @author Sorghum
 */
public final class ToolDefHelper {

    private ToolDefHelper() {
    }

    /**
     * 将 AgentTool 的参数类型映射为 JSON Schema 类型。
     */
    public static String toJsonType(String type) {
        if (type == null) return "string";
        return switch (type.toLowerCase()) {
            case "int", "integer", "long" -> "integer";
            case "bool", "boolean" -> "boolean";
            case "number", "float", "double" -> "number";
            case "array", "list" -> "array";
            case "object", "map" -> "object";
            default -> "string";
        };
    }

    /**
     * 将 AgentTool 的参数定义列表转换为 ToolDef.ParamDef 列表。
     * 递归处理嵌套参数（object 的 properties 和 array 的 items）。
     */
    public static List<ToolDef.ParamDef> toParamDefs(List<ToolParameter> params) {
        if (params == null) return Collections.emptyList();
        List<ToolDef.ParamDef> out = new ArrayList<>();
        for (ToolParameter p : params) {
            out.add(toParamDef(p));
        }
        return out;
    }

    /**
     * 单个 ToolParameter → ParamDef 转换，递归处理嵌套。
     */
    private static ToolDef.ParamDef toParamDef(ToolParameter p) {
        String jsonType = toJsonType(p.type());

        // 处理 object 类型：递归转换子属性
        if ("object".equals(jsonType) && p.properties() != null && !p.properties().isEmpty()) {
            List<ToolDef.ParamDef> nested = new ArrayList<>();
            for (ToolParameter sub : p.properties()) {
                nested.add(toParamDef(sub));
            }
            return new ToolDef.ParamDef(p.name(), jsonType, p.description(), p.required(), nested, null);
        }

        // 处理 array 类型：递归转换 items
        if ("array".equals(jsonType) && p.items() != null) {
            ToolDef.ParamDef itemParam = toParamDef(p.items());
            return new ToolDef.ParamDef(p.name(), jsonType, p.description(), p.required(), null, itemParam);
        }

        // 扁平参数
        return new ToolDef.ParamDef(p.name(), jsonType, p.description(), p.required(), null, null);
    }

    /**
     * 将 ToolResult 格式化为工具调用的返回字符串。
     * 失败结果添加 [FAIL:errorCode] 前缀。
     */
    public static String formatResult(ToolResult r) {
        if (r.success()) return r.text();
        return "[FAIL:" + r.errorCode() + "] " + r.text();
    }
}
