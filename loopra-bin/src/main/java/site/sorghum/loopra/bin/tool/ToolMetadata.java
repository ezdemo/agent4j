package site.sorghum.loopra.bin.tool;

import org.noear.solon.ai.chat.tool.FunctionTool;

import java.util.Map;
import java.util.Set;

/** Shared classification rules for tool execution and management UIs. */
public final class ToolMetadata {

    /** Runtime override key, kept separate from tool-declared metadata so it can be removed cleanly. */
    private static final String READ_ONLY_OVERRIDE_KEY = "loopraReadOnlyOverride";

    private static final Set<String> BUILT_IN_READ_ONLY_TOOLS = Set.of(
            "read", "glob", "grep", "ls",
            "workspace_read", "workspace_list", "workspace_write",
            "java_source", "codesearch", "codegraph_explore", "webfetch", "finish");

    private ToolMetadata() {
    }

    public static boolean isReadOnly(FunctionTool tool) {
        Boolean override = readOnlyOverride(tool);
        if (override != null) {
            return override;
        }
        Boolean explicit = metaBoolean(tool, "readOnly");
        return explicit != null ? explicit
                : tool != null && BUILT_IN_READ_ONLY_TOOLS.contains(tool.name());
    }

    public static Boolean readOnlyOverride(FunctionTool tool) {
        return metaBoolean(tool, READ_ONLY_OVERRIDE_KEY);
    }

    public static void applyReadOnlyOverride(FunctionTool tool, Boolean readOnly) {
        if (tool == null || tool.meta() == null) {
            return;
        }
        if (readOnly == null) {
            tool.meta().remove(READ_ONLY_OVERRIDE_KEY);
        } else {
            tool.meta().put(READ_ONLY_OVERRIDE_KEY, readOnly);
        }
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
