package site.sorghum.agent4j.bin.builtin;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.bin.workspace.SharedWorkspace;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.util.Arrays;
import java.util.List;

/**
 * Workspace Write 工具 —— 向共享工作区写入 KV 或文档条目。
 * <p>
 * 支持两种写入模式：
 * <ul>
 *   <li><b>KV 模式</b>：提供 {@code key} 和 {@code value}，以键值对形式存储</li>
 *   <li><b>文档模式</b>：提供 {@code key}、{@code content} 和可选的 {@code type}，以文档形式存储</li>
 * </ul>
 * 写入时自动进行容量检查与最旧条目淘汰。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class WorkspaceWriteTool extends AgentTool {

    @Inject
    private SharedWorkspace workspace;

    /**
     * 无参构造器 —— Solon DI 使用。
     */
    public WorkspaceWriteTool() {
    }

    /**
     * 带参构造器 —— SubAgent 手动创建时使用。
     *
     * @param workspace SharedWorkspace 实例
     */
    public WorkspaceWriteTool(SharedWorkspace workspace) {
        this.workspace = workspace;
    }

    @Override
    public String getName() {
        return "workspace_write";
    }

    @Override
    public String getDescription() {
        return "Write entries to the shared workspace. Supports two modes:\n"
                + "- KV mode: store a value under a key (key + value)\n"
                + "- Document mode: store document content under a key (key + content + optional type)\n"
                + "Key is always required. If 'value' is provided, KV mode is used. "
                + "If 'content' is provided, document mode is used. "
                + "If both 'value' and 'content' are provided, KV mode takes precedence.";
    }

    @Override
    public String toToolSpec() {
        return """
                ### workspace_write

                描述：向共享工作区写入 KV 或文档条目。KV 模式存储键值对，文档模式存储富文本内容。
                参数: key(必填, 条目路径), value(可选, KV 模式值), content(可选, 文档模式内容),
                      type(可选, 文档 MIME 类型, 默认 text/plain), scope(可选, 作用域预留)。
                key 为空时返回错误；value 和 content 都为空时返回错误。
                可写。
                """;
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Arrays.asList(
                new ToolParameter("key", "string", true,
                        "Entry path / key for the workspace entry"),
                new ToolParameter("value", "string", false,
                        "Value for KV mode. If provided, writes as a key-value pair."),
                new ToolParameter("content", "string", false,
                        "Content for document mode. If provided (and value is null), writes as a document."),
                new ToolParameter("type", "string", false,
                        "Document MIME type (e.g. text/plain, text/markdown, application/json). Default: text/plain"),
                new ToolParameter("scope", "string", false,
                        "Scope / namespace for the entry (reserved for future use)")
        );
    }

    @Override
    public ToolResult execute(ToolContext ctx) {
        // 1. 获取 key，必填
        String key = ctx.getString("key");
        if (key == null || key.isBlank()) {
            return ToolResult.fail("PARAM_MISSING", "Missing required parameter 'key'");
        }

        // 2. 获取 creator（优先使用 sessionId，兜底用 "agent"）
        String creator = ctx.getSessionId();
        if (creator == null || creator.isBlank()) {
            creator = "agent";
        }

        String value = ctx.getString("value");
        String content = ctx.getString("content");

        // 3. 确定模式并写入
        if (value != null) {
            // KV 模式
            try {
                workspace.writeKV(key, value, creator);
                return ToolResult.ok("Successfully wrote KV entry: " + key);
            } catch (Exception e) {
                return ToolResult.fail("WRITE_FAILED", "Failed to write KV entry '" + key + "': " + e.getMessage());
            }
        } else if (content != null) {
            // 文档模式
            String mimeType = ctx.getString("type", "text/plain");
            try {
                workspace.writeDoc(key, content, mimeType, creator);
                return ToolResult.ok("Successfully wrote document entry: " + key + " (type: " + mimeType + ")");
            } catch (Exception e) {
                return ToolResult.fail("WRITE_FAILED", "Failed to write document entry '" + key + "': " + e.getMessage());
            }
        } else {
            // value 和 content 都为空
            return ToolResult.fail("PARAM_MISSING",
                    "Either 'value' (KV mode) or 'content' (document mode) must be provided");
        }
    }
}
