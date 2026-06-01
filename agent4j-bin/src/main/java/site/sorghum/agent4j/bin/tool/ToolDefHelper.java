package site.sorghum.agent4j.bin.tool;

import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.util.ArrayList;
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
     */
    public static List<ToolDef.ParamDef> toParamDefs(List<ToolParameter> params) {
        List<ToolDef.ParamDef> out = new ArrayList<>();
        for (ToolParameter p : params) {
            if (p.required()) {
                out.add(ToolDef.ParamDef.required(p.name(), toJsonType(p.type()), p.description()));
            } else {
                out.add(ToolDef.ParamDef.of(p.name(), toJsonType(p.type()), p.description()));
            }
        }
        return out;
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
