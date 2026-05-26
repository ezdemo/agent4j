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
 * 文件名匹配工具——按 glob 模式查找文件。
 * <p>
 * 基于工作区索引缓存，毫秒级返回。按修改时间倒序排列。
 * 支持 {@code **} 多级通配、{@code *} 单级通配、{@code ?} 单字符、{@code {a,b}} 分支。
 * </p>
 *
 * <h3>模型调用示例：</h3>
 * <pre>
 * glob({ pattern: "src/**​/*.java" })
 * → [
 *     "src/hashline/HashlineSession.java",
 *     "src/hashline/HashUtil.java",
 *     ...
 *   ]
 * </pre>
 *
 * @author Sorghum
 */
@Component
public class GlobTool extends AgentTool {

    private static final String NAME = "glob";

    private static final String DESCRIPTION =
            "按 glob 模式匹配文件名。\n"
                    + "支持 ** (多级目录)、* (单级)、? (单字符)、{a,b} (分支)。\n"
                    + "结果按修改时间倒序——最近改动的文件排在最前。\n"
                    + "基于工作区索引缓存，毫秒级响应。";

    private static final List<ToolParameter> PARAMETERS = Arrays.asList(
            new ToolParameter("pattern", "string", true,
                    "glob 模式，如 \"**​/*.java\"、\"src/**​/*Test*.ts\"、\"*.{md,mdx}\""),
            new ToolParameter("maxResults", "int", false,
                    "最大返回条数，默认 200")
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
    public String toToolSpec() {
        return "### glob\n\n"
                + "描述：按 glob 模式匹配文件名。基于工作区索引缓存，毫秒级响应。\n"
                + "结果按修改时间倒序排列（最近改动的文件排在最前）。\n\n"
                + "## 模式语法\n\n"
                + "| 模式 | 匹配 |\n"
                + "|------|------|\n"
                + "| `**` | 任意多级目录 |\n"
                + "| `*` | 单级内任意字符（不含路径分隔符） |\n"
                + "| `?` | 单个任意字符 |\n"
                + "| `{a,b}` | 分支选择 |\n\n"
                + "## 示例\n\n"
                + "- `**/*.java` — 所有 Java 文件\n"
                + "- `src/**/*Test*.ts` — src 目录下所有包含 Test 的 TypeScript 文件\n"
                + "- `*.{md,mdx}` — 根目录下的 Markdown 文件\n\n"
                + "参数：\n"
                + "  - pattern (string, 必填): glob 模式\n"
                + "  - maxResults (int, 可选): 最大返回条数，默认 200\n\n"
                + "只读：是\n"
                + "风暴豁免：是";
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
        String pattern = ctx.getString("pattern");
        if (pattern == null || pattern.isEmpty()) {
            return ToolResult.fail("MISSING_PATTERN", "缺少必填参数 pattern（glob 模式）");
        }

        try {
            java.nio.file.Path root = ctx.getRootDir() != null
                    ? ctx.getRootDir()
                    : Paths.get(".").toAbsolutePath();
            List<String> blockedPaths = ctx.getBlockedPaths();

            int maxResults = Math.min(ctx.getInt("maxResults", 200), 1000);
            List<String> files = GrepTool.getOrCreateIndex(root, blockedPaths).glob(pattern);

            if (files.size() > maxResults) {
                files = files.subList(0, maxResults);
            }

            StringBuilder sb = new StringBuilder();
            sb.append("glob \"").append(pattern).append("\"");
            sb.append(" → ").append(files.size()).append(" 个文件\n");

            if (files.isEmpty()) {
                sb.append("（无匹配）");
            } else {
                for (int i = 0; i < files.size(); i++) {
                    sb.append(files.get(i)).append("\n");
                }
            }

            return ToolResult.ok(sb.toString().trim(), files);
        } catch (IOException e) {
            return ToolResult.fail("IO_ERROR", e.getMessage());
        } catch (Exception e) {
            return ToolResult.fail("INTERNAL_ERROR", e.getMessage());
        }
    }

    private void ensureIndex(ToolContext ctx) throws IOException {
        java.nio.file.Path root = ctx.getRootDir() != null
                ? ctx.getRootDir()
                : Paths.get(".").toAbsolutePath();
        GrepTool.getOrCreateIndex(root, ctx.getBlockedPaths());
    }
}
