package site.sorghum.agent4j.bin.builtin;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.bin.workspace.SharedWorkspace;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Workspace List 工具 —— 列出共享工作区中的条目键。
 * <p>
 * 支持按前缀过滤，返回匹配的所有 KV 和文档条目的 key 列表。
 * 结果按字母序排列，带序号和总数。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class WorkspaceListTool extends AgentTool {

    @Inject
    private SharedWorkspace workspace;

    /**
     * 无参构造器 —— Solon DI 使用。
     */
    public WorkspaceListTool() {
    }

    /**
     * 带参构造器 —— SubAgent 手动创建时使用。
     *
     * @param workspace SharedWorkspace 实例
     */
    public WorkspaceListTool(SharedWorkspace workspace) {
        this.workspace = workspace;
    }

    @Override
    public String getName() {
        return "workspace_list";
    }

    @Override
    public String getDescription() {
        return "List keys in the shared workspace. Supports optional prefix filtering.\n"
                + "Returns all matching KV and document entry keys, sorted alphabetically,\n"
                + "with index numbers and total count. Use without prefix to list all entries.";
    }

    @Override
    public String toToolSpec() {
        return """
                ### workspace_list

                描述：列出共享工作区中的条目键。支持按前缀过滤，返回所有匹配的 KV 和文档条目的 key 列表。
                参数: prefix(可选, key 前缀过滤), scope(可选, 作用域预留)。
                prefix 为空时列出所有条目。
                只读。
                """;
    }

    @Override
    public List<ToolParameter> getParameters() {
        return List.of(
                new ToolParameter("prefix", "string", false,
                        "Optional prefix to filter keys. Only keys starting with this prefix will be returned."),
                new ToolParameter("scope", "string", false,
                        "Scope / namespace filter (reserved for future use)")
        );
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public ToolResult execute(ToolContext ctx) {
        // 1. 获取 prefix，可选，默认为空字符串（列出所有）
        String prefix = ctx.getString("prefix", "");

        // 2. 调用 workspace.listKeys(prefix)
        Set<String> keys;
        try {
            keys = workspace.listKeys(prefix);
        } catch (Exception e) {
            return ToolResult.fail("LIST_FAILED",
                    "Failed to list workspace keys with prefix '" + prefix + "': " + e.getMessage());
        }

        // 3. 格式化输出
        if (keys == null || keys.isEmpty()) {
            if (prefix.isEmpty()) {
                return ToolResult.ok("Workspace is empty. No entries found.");
            } else {
                return ToolResult.ok("No entries found with prefix: '" + prefix + "'.\n"
                        + "Tip: Use workspace_list without prefix to see all available keys.");
            }
        }

        // 按字母序排序，保证输出稳定
        List<String> sortedKeys = keys.stream().sorted().collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(sortedKeys.size()).append(" entr")
                .append(sortedKeys.size() == 1 ? "y" : "ies")
                .append(" in workspace");
        if (!prefix.isEmpty()) {
            sb.append(" (prefix: '").append(prefix).append("')");
        }
        sb.append(":\n\n");

        int index = 1;
        for (String key : sortedKeys) {
            sb.append(String.format("  %2d. %s%n", index++, key));
        }

        sb.append("\nTotal: ").append(sortedKeys.size()).append(" entr")
                .append(sortedKeys.size() == 1 ? "y" : "ies")
                .append(".");

        return ToolResult.ok(sb.toString());
    }
}
