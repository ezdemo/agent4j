package site.sorghum.agent4j.tool.search;

import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * 目录树工具——输出工作区的缩进树结构。
 * <p>
 * 自动跳过 node_modules / .git / target 等 denylist 目录。
 * 支持深度控制，避免输出过于庞大。
 * </p>
 *
 * <h3>模型调用示例：</h3>
 * <pre>
 * tree({ maxDepth: 3 })
 * → agent4j/
 *     ├── pom.xml
 *     ├── agent4j-tool/
 *     │   ├── pom.xml
 *     │   └── src/
 *     │       └── main/
 *     ├── agent4j-bin/
 *     └── agent4j-web/
 * </pre>
 *
 * @author Sorghum
 */
@Component
public class TreeTool extends AgentTool {

    private static final String NAME = "tree";

    private static final String DESCRIPTION =
            "生成工作区的目录树结构。\n"
                    + "自动跳过 node_modules / .git / target 等无关目录。\n"
                    + "可通过 maxDepth 控制输出深度，避免上下文过大。";

    private static final List<ToolParameter> PARAMETERS = Arrays.asList(
            new ToolParameter("maxDepth", "int", false,
                    "最大递归深度，默认 3。0 = 仅根目录，-1 = 不限深度")
    );

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public List<ToolParameter> getParameters() {
        return PARAMETERS;
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public ToolResult execute(ToolContext ctx) {
        try {
            java.nio.file.Path root = ctx.getRootDir() != null ? ctx.getRootDir() : Paths.get(".").toAbsolutePath();
            List<String> blockedPaths = ctx.getBlockedPaths();

            int maxDepth = ctx.getInt("maxDepth", 3);
            if (maxDepth < 0) maxDepth = Integer.MAX_VALUE;

            WorkspaceIndex idx = GrepTool.getOrCreateIndex(root, blockedPaths);
            String tree = idx.tree(maxDepth);
            String stats = "\n" + idx.fileCount() + " 个文件，" + formatSize(idx.totalSize());

            return ToolResult.ok(tree + stats, tree);
        } catch (IOException e) {
            return ToolResult.fail("IO_ERROR", e.getMessage());
        } catch (Exception e) {
            return ToolResult.fail("INTERNAL_ERROR", e.getMessage());
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
