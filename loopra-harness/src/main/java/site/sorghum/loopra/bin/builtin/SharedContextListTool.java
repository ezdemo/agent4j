package site.sorghum.loopra.bin.builtin;

import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.ai.chat.tool.AbsToolProvider;
import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Param;
import site.sorghum.loopra.bin.context.SharedContextStore;
import site.sorghum.loopra.tool.ToolContext;
import site.sorghum.loopra.tool.SolonToTools;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shared-context list tool. The legacy {@code workspace_list} tool name is
 * kept for protocol compatibility.
 * <p>
 * 支持按前缀过滤，返回匹配的所有 KV 和文档条目的 key 列表。
 * 结果按字母序排列，带序号和总数。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class SharedContextListTool extends AbsToolProvider implements SolonToTools {

    @Inject
    private SharedContextStore contextStore;

    /**
     * 无参构造器 —— Solon DI 使用。
     */
    public SharedContextListTool() {
    }

    /**
     * 带参构造器 —— SubAgent 手动创建时使用。
     *
     * @param contextStore shared project context
     */
    public SharedContextListTool(SharedContextStore contextStore) {
        this.contextStore = contextStore;
    }

    @ToolMapping(name = "workspace_list", description = """
                列出当前项目持久化在 `.loopra/workspace/` 的共享上下文条目键。支持按前缀过滤，返回所有匹配的 KV 和文档条目的 key 列表。
                参数: prefix(可选, key 前缀过滤), scope(可选, 作用域预留)。
                prefix 为空时列出所有条目。
                """)
    public String workspaceList(@Param(name = "prefix", description = "Optional prefix to filter keys. Only keys starting with this prefix will be returned.", required = false) String prefix,
                                @Param(name = "scope", description = "Scope / namespace filter (reserved for future use)", required = false) String scope,
                                ToolContext ctx) {
        // 1. 获取 prefix，可选，默认为空字符串（列出所有）
        if (prefix == null) {
            prefix = "";
        }

        // 2. 调用 contextStore.listKeys(prefix)
        Path projectRoot = ctx == null ? null : ctx.getRootDir();
        Set<String> keys;
        try {
            keys = contextStore.listKeys(projectRoot, prefix);
        } catch (Exception e) {
            return "LIST_FAILED: Failed to list shared context keys with prefix '" + prefix + "': " + e.getMessage();
        }

        // 3. 格式化输出
        if (keys == null || keys.isEmpty()) {
            if (prefix.isEmpty()) {
                return "Shared context is empty. No entries found.";
            } else {
                return "No entries found with prefix: '" + prefix + "'.\n"
                        + "Tip: Use workspace_list without prefix to see all available keys.";
            }
        }

        // 按字母序排序，保证输出稳定
        List<String> sortedKeys = keys.stream().sorted().collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(sortedKeys.size()).append(" entr")
                .append(sortedKeys.size() == 1 ? "y" : "ies")
                .append(" in shared context");
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

        return sb.toString();
    }

    @Override
    public Collection<FunctionTool> getSolonTools() {
        return this.getTools();
    }
}
