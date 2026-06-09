package site.sorghum.agent4j.bin.builtin;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.bin.workspace.DocumentBucket;
import site.sorghum.agent4j.bin.workspace.KVBucket;
import site.sorghum.agent4j.bin.workspace.SharedWorkspace;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Workspace Read 工具 —— 从共享工作区读取 KV 或文档条目。
 * <p>
 * 支持两种读取模式：
 * <ul>
 *   <li><b>KV 模式</b>：通过 {@code key} 读取键值对的值</li>
 *   <li><b>文档模式</b>：通过 {@code key} 读取文档内容及元数据</li>
 * </ul>
 * 读取时优先尝试 KV 模式，若未命中则尝试文档模式，均未命中时返回 NOT_FOUND 并附带相似 key 提示。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class WorkspaceReadTool extends AgentTool {

    @Inject
    private SharedWorkspace workspace;

    /**
     * 无参构造器 —— Solon DI 使用。
     */
    public WorkspaceReadTool() {
    }

    /**
     * 带参构造器 —— SubAgent 手动创建时使用。
     *
     * @param workspace SharedWorkspace 实例
     */
    public WorkspaceReadTool(SharedWorkspace workspace) {
        this.workspace = workspace;
    }

    @Override
    public String getName() {
        return "workspace_read";
    }

    @Override
    public String getDescription() {
        return """
                Read entries from the shared workspace. Supports two modes:
                - KV mode: read a value stored under a key (key)
                - Document mode: read document content and metadata under a key (key)
                Key is always required. KV mode is tried first; if no KV entry is found,
                document mode is attempted. If neither exists, returns NOT_FOUND with suggestions
                for similar keys.""";
    }

    @Override
    public String toToolSpec() {
        return """
                ### workspace_read

                描述：从共享工作区读取 KV 或文档条目。优先尝试 KV 读取，其次尝试文档读取，
                均未命中时返回 NOT_FOUND 错误并附带相似 key 提示。
                参数: key(必填, 条目路径), scope(可选, 作用域过滤)。
                key 为空时返回错误。
                只读。
                """;
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Arrays.asList(
                new ToolParameter("key", "string", true,
                        "Entry path / key for the workspace entry to read"),
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
        // 1. 获取 key，必填
        String key = ctx.getString("key");
        if (key == null || key.isBlank()) {
            return ToolResult.fail("PARAM_MISSING", "Missing required parameter 'key'");
        }

        // 2. 优先尝试 KV 读取
        Optional<KVBucket> kvBucket = workspace.getKVBucket(key);
        if (kvBucket.isPresent()) {
            KVBucket bucket = kvBucket.get();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("mode", "kv");
            data.put("key", key);
            data.put("value", bucket.getValue());
            data.put("creator", bucket.getCreator());
            data.put("createdAt", bucket.getCreatedAt());
            data.put("updatedAt", bucket.getUpdatedAt());
            data.put("version", bucket.getVersion());
            if (bucket.getTtlMs() >= 0) {
                data.put("ttlMs", bucket.getTtlMs());
            }
            if (bucket.getMetadata() != null && !bucket.getMetadata().isEmpty()) {
                data.put("metadata", bucket.getMetadata());
            }
            return ToolResult.ok("KV entry found for key: " + key + "\nValue: " + bucket.getValue(), data);
        }

        // 3. 再尝试文档读取
        Optional<DocumentBucket> docBucket = workspace.readDoc(key);
        if (docBucket.isPresent()) {
            DocumentBucket bucket = docBucket.get();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("mode", "document");
            data.put("key", key);
            data.put("content", bucket.getContent());
            data.put("mimeType", bucket.getMimeType());
            data.put("creator", bucket.getCreator());
            data.put("createdAt", bucket.getCreatedAt());
            data.put("updatedAt", bucket.getUpdatedAt());
            data.put("version", bucket.getVersion());
            if (bucket.getTtlMs() >= 0) {
                data.put("ttlMs", bucket.getTtlMs());
            }
            if (bucket.getMetadata() != null && !bucket.getMetadata().isEmpty()) {
                data.put("metadata", bucket.getMetadata());
            }
            return ToolResult.ok("Document entry found for key: " + key
                    + "\nType: " + bucket.getMimeType()
                    + "\nContent: " + bucket.getContent(), data);
        }

        // 4. 都找不到，返回 NOT_FOUND 并附带相似 key 提示
        String suggestion = buildNotFoundSuggestion(key);
        return ToolResult.fail("NOT_FOUND",
                "No entry found for key: '" + key + "'.\n" + suggestion);
    }

    /**
     * 构建 NOT_FOUND 时的相似 key 提示。
     * <p>
     * 通过逐步缩短前缀的方式从工作区中查找相似 key，
     * 最多返回 10 个匹配结果供用户参考。
     * </p>
     *
     * @param key 用户查询的 key
     * @return 提示文本，包含相似 key 列表（如有）
     */
    private String buildNotFoundSuggestion(String key) {
        // 收集所有相似 key
        Set<String> similarKeys = findSimilarKeys(key);
        if (similarKeys.isEmpty()) {
            return """
                    No similar keys found in workspace. Use workspace_write to create entries, 
                    or check available keys via workspace administration tools.
                    """;
        }

        // 按"相似度"排序：先按编辑距离，再按字母序
        String normalizedKey = key.toLowerCase(Locale.ROOT);
        List<String> sorted = similarKeys.stream()
                .sorted(Comparator.<String, Integer>comparing(
                                k -> levenshteinDistance(normalizedKey,
                                        k.toLowerCase(Locale.ROOT)))
                        .thenComparing(String::compareTo))
                .limit(10)
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("Similar keys found in workspace (").append(sorted.size()).append("):\n");
        for (String sk : sorted) {
            sb.append("  - ").append(sk).append("\n");
        }
        sb.append("Try one of the above keys, or use workspace_write to create a new entry.");
        return sb.toString();
    }

    /**
     * 查找与目标 key 相似的现有工作区 key。
     * <p>
     * 策略：从完整 key 开始，逐步去掉最后一段路径分隔符后的部分，
     * 用前缀匹配方式收集所有可能的 key。同时尝试常用的分隔符 '/'、'.'、'-'、'_'。
     * </p>
     *
     * @param key 目标 key
     * @return 匹配的 key 集合（不包含目标 key 本身）
     */
    private Set<String> findSimilarKeys(String key) {
        Set<String> allKeys = workspace.listKeys("");

        if (allKeys.isEmpty()) {
            return Collections.emptySet();
        }

        // 排除精确匹配的 key 本身
        Set<String> result = new LinkedHashSet<>(allKeys);
        result.remove(key);

        // 如果已有大量 key，直接返回全部（排除自身）
        // 否则尝试缩小范围，使用前缀匹配
        if (result.size() <= 50) {
            return result;
        }

        // 尝试用 key 的前缀缩小范围（处理过长的结果集）
        Set<String> prefixed = new LinkedHashSet<>();
        // 尝试用完整 key 的不同长度前缀来匹配
        for (int len = Math.min(key.length(), 10); len >= 2; len--) {
            String prefix = key.substring(0, len);
            Set<String> matched = workspace.listKeys(prefix);
            matched.remove(key);
            prefixed.addAll(matched);
            if (prefixed.size() >= 10) {
                break; // 已收集足够候选项
            }
        }

        if (!prefixed.isEmpty()) {
            return prefixed;
        }

        // 兜底：返回全部 key（排除自身）
        return result;
    }

    /**
     * 计算两个字符串的 Levenshtein 编辑距离。
     *
     * @param a 第一个字符串
     * @param b 第二个字符串
     * @return 编辑距离
     */
    private static int levenshteinDistance(String a, String b) {
        int m = a.length();
        int n = b.length();
        int[][] dp = new int[m + 1][n + 1];

        for (int i = 0; i <= m; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= n; j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[m][n];
    }
}
