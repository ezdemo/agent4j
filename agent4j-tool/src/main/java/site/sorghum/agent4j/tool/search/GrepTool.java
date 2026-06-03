package site.sorghum.agent4j.tool.search;

import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内容搜索工具——在工作区文件内按正则表达式搜索。
 * <p>
 * 自动跳过二进制文件、超大文件（&gt;2MB）和 denylist 目录。
 * 首次调用时自动构建工作区索引并缓存。
 * </p>
 *
 * <h3>模型调用示例：</h3>
 * <pre>
 * grep({ pattern: "Hashline", glob: "*.java" })
 * → [
 *     "src/hashline/HashUtil.java:15: public static String hash(...",
 *     "src/hashline/Hashline.java:22: public class Hashline {",
 *     ...
 *   ]
 * </pre>
 *
 * @author Sorghum
 */
@Component
public class GrepTool extends AgentTool {

    private static final String NAME = "grep";

    private static final String DESCRIPTION =
            """
                    在工作区文件中按正则表达式搜索内容。
                    自动跳过 node_modules / .git / target 等目录，
                    以及二进制和大文件（>2MB）。
                    
                    支持正则语法、大小写敏感开关和文件 glob 过滤。
                    首次调用自动构建索引，后续调用复用缓存。""";

    private static final List<ToolParameter> PARAMETERS = Arrays.asList(
            new ToolParameter("pattern", "string", true,
                    "搜索模式（正则表达式），如 \"Hashline\" 或 \"class\\s+\\\\w+\""),
            new ToolParameter("glob", "string", false,
                    "文件过滤 glob，如 \"*.java\" 或 \"src/**​/*.ts\"。不传则搜索所有文本文件"),
            new ToolParameter("caseSensitive", "boolean", false,
                    "是否大小写敏感，默认 true"),
            new ToolParameter("maxResults", "int", false,
                    "最大返回条数，默认 200，上限 500")
    );

    // 按工作区根目录隔离的索引（多 Agent 场景线程安全）
    private static final Map<String, WorkspaceIndex> INDEX_MAP = new ConcurrentHashMap<>();

    /**
     * 获取或创建工作区索引（按 rootDir 隔离）。
     */
    public static WorkspaceIndex getOrCreateIndex(Path rootDir) throws IOException {
        return getOrCreateIndex(rootDir, Collections.emptyList());
    }

    /**
     * 获取或创建工作区索引（按 rootDir + blockedPaths 隔离）。
     */
    public static WorkspaceIndex getOrCreateIndex(Path rootDir, List<String> blockedPaths) throws IOException {
        String rootKey = rootDir.toAbsolutePath().normalize().toString();
        String bpKey = blockedPaths != null ? String.join(",", blockedPaths) : "";
        String key = rootKey + "|" + bpKey;
        WorkspaceIndex idx = INDEX_MAP.get(key);
        if (idx == null) {
            synchronized (GrepTool.class) {
                idx = INDEX_MAP.get(key);
                if (idx == null) {
                    idx = new WorkspaceIndex(rootDir, blockedPaths);
                    INDEX_MAP.put(key, idx);
                }
            }
        }
        idx.refresh();
        return idx;
    }

    /**
     * 设置工作区索引（测试用）。
     */
    public static void setIndex(WorkspaceIndex idx, Path rootDir) {
        String key = rootDir.toAbsolutePath().normalize().toString();
        INDEX_MAP.put(key, idx);
    }

    /**
     * 清除所有索引（测试用）。
     */
    public static void clearIndexes() {
        INDEX_MAP.clear();
    }

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
        return """
                ### grep
                
                描述：在工作区文件中按正则表达式搜索。自动跳过二进制/大文件/denylist 目录，首次调用构建索引缓存。
                参数: pattern(必填，正则), glob(可选，文件过滤如 *.java), caseSensitive(可选，默认true), maxResults(可选，默认200)。只读。""";
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
            return ToolResult.fail("MISSING_PATTERN", "缺少必填参数 pattern（正则表达式）");
        }

        try {
            Path root = ctx.getRootDir() != null
                    ? ctx.getRootDir()
                    : Paths.get(".").toAbsolutePath();
            List<String> blockedPaths = ctx.getBlockedPaths();

            String glob = ctx.getString("glob", "*");
            boolean caseSensitive = ctx.getBool("caseSensitive", true);
            int maxResults = Math.min(ctx.getInt("maxResults", 200), 500);

            // 构建模式（大小写敏感处理）
            String effectivePattern = caseSensitive ? pattern : "(?i)" + pattern;

            List<SearchMatch> matches = getOrCreateIndex(root, blockedPaths).grep(effectivePattern, glob);

            // 截断到 maxResults
            if (matches.size() > maxResults) {
                matches = matches.subList(0, maxResults);
            }

            // 格式化为文本输出
            StringBuilder sb = new StringBuilder();
            sb.append("grep \"").append(pattern).append("\"");
            if (glob != null && !glob.equals("*")) {
                sb.append(" --glob \"").append(glob).append("\"");
            }
            sb.append(" → ").append(matches.size()).append(" 条匹配\n");

            if (matches.isEmpty()) {
                sb.append("（无匹配）");
            } else {
                for (SearchMatch m : matches) {
                    sb.append(m.toString()).append("\n");
                }
            }

            return ToolResult.ok(sb.toString().trim(), matches);
        } catch (IOException e) {
            return ToolResult.fail("IO_ERROR", "[" + e.getClass().getSimpleName() + "] " + e.getMessage());
        } catch (Exception e) {
            return ToolResult.fail("INTERNAL_ERROR", "[" + e.getClass().getSimpleName() + "] " + e.getMessage());
        }
    }

    private void ensureIndex(ToolContext ctx) throws IOException {
        Path root = ctx.getRootDir() != null
                ? ctx.getRootDir()
                : Paths.get(".").toAbsolutePath();
        getOrCreateIndex(root, ctx.getBlockedPaths());
    }
}
