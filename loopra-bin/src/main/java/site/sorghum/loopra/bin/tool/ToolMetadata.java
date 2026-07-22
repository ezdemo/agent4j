package site.sorghum.loopra.bin.tool;

import org.noear.solon.ai.chat.tool.FunctionTool;

import java.util.Map;

/** Shared classification rules for tool execution and management UIs. */
public final class ToolMetadata {

    private ToolMetadata() {
    }

    public static boolean isReadOnly(FunctionTool tool) {
        return metaFlag(tool, "readOnly");
    }

    public static boolean isStormExempt(FunctionTool tool) {
        return tool != null
                && (isBuiltInStormExempt(tool.name()) || metaFlag(tool, "stormExempt"));
    }

    private static boolean metaFlag(FunctionTool tool, String key) {
        Map<String, Object> meta = tool != null ? tool.meta() : null;
        Object value = meta != null ? meta.get(key) : null;
        if (value instanceof Boolean flag) {
            return flag;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }

    /** Polling tools may legitimately repeat with identical arguments while waiting for state changes. */
    private static boolean isBuiltInStormExempt(String toolName) {
        return toolName != null
                && (toolName.startsWith("browser_") || "bash_wait".equals(toolName));
    }
}
