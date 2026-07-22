package site.sorghum.loopra.bin.tool;

import org.noear.solon.ai.chat.tool.FunctionTool;

import java.util.Map;
import java.util.Set;

/** Shared classification rules for tool execution and management UIs. */
public final class ToolMetadata {

    private static final Set<String> BUILT_IN_READ_ONLY_TOOLS = Set.of(
            "read", "glob", "grep", "ls",
            "workspace_read", "workspace_list", "workspace_write",
            "java_source", "codesearch", "codegraph_explore", "webfetch",
            "vision_recognize", "finish");

    private ToolMetadata() {
    }

    public static boolean isReadOnly(FunctionTool tool) {
        Boolean explicit = metaBoolean(tool, "readOnly");
        return explicit != null ? explicit
                : tool != null && BUILT_IN_READ_ONLY_TOOLS.contains(tool.name());
    }

    public static boolean isStormExempt(FunctionTool tool) {
        return tool != null && (isBuiltInStormExempt(tool.name())
                || Boolean.TRUE.equals(metaBoolean(tool, "stormExempt")));
    }

    private static Boolean metaBoolean(FunctionTool tool, String key) {
        Map<String, Object> meta = tool != null ? tool.meta() : null;
        Object value = meta != null ? meta.get(key) : null;
        if (value instanceof Boolean flag) {
            return flag;
        }
        return value != null ? Boolean.parseBoolean(value.toString()) : null;
    }

    /** Polling tools may legitimately repeat with identical arguments while waiting for state changes. */
    private static boolean isBuiltInStormExempt(String toolName) {
        return toolName != null
                && (toolName.startsWith("browser_") || "bash_wait".equals(toolName));
    }
}
