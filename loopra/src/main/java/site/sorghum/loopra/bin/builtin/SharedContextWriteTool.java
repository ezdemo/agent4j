package site.sorghum.loopra.bin.builtin;

import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.ai.chat.tool.AbsToolProvider;
import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Param;
import site.sorghum.loopra.bin.context.SharedContextStore;
import site.sorghum.loopra.tool.SolonToTools;
import site.sorghum.loopra.tool.ToolContext;

import java.nio.file.Path;
import java.util.Collection;

/**
  * 共享上下文写入工具。为协议兼容保留旧名 {@code workspace_write}。
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
public class SharedContextWriteTool extends AbsToolProvider implements SolonToTools {

    @Inject
    private SharedContextStore contextStore;

    /**
     * 无参构造器 —— Solon DI 使用。
     */
    public SharedContextWriteTool() {
    }

    /**
     * 带参构造器 —— SubAgent 手动创建时使用。
     *
      * @param contextStore 共享项目上下文
     */
    public SharedContextWriteTool(SharedContextStore contextStore) {
        this.contextStore = contextStore;
    }

    @ToolMapping(name = "workspace_write", description = """
                向当前项目的共享上下文写入 KV 或文档条目，数据持久化到 `.loopra/workspace/`。KV 模式存储键值对，文档模式存储富文本内容。
                参数: key(必填, 条目路径), value(可选, KV 模式值), content(可选, 文档模式内容),
                      type(可选, 文档 MIME 类型, 默认 text/plain), scope(可选, 作用域预留)。
                key 为空时返回错误；value 和 content 都为空时返回错误。
                """)
    public String workspaceWrite(@Param(name = "key", description = "Entry path / key for the workspace entry") String key,
                                 @Param(name = "value", description = "Value for KV mode. If provided, writes as a key-value pair.", required = false) String value,
                                 @Param(name = "content", description = "Content for document mode. If provided (and value is null), writes as a document.", required = false) String content,
                                 @Param(name = "type", description = "Document MIME type (e.g. text/plain, text/markdown, application/json). Default: text/plain", required = false) String type,
                                 @Param(name = "scope", description = "Scope / namespace for the entry (reserved for future use)", required = false) String scope,
                                 @Param(name = "ctx", required = false) ToolContext ctx) {
        // 1. 获取 key，必填
        if (key == null || key.isBlank()) {
            return "PARAM_MISSING: Missing required parameter 'key'";
        }

        // 2. 获取 creator（优先使用 sessionId，兜底用 "agent"）
        String creator = ctx == null ? null : ctx.getSessionId();
        if (creator == null || creator.isBlank()) {
            creator = "agent";
        }
        Path projectRoot = ctx == null ? null : ctx.getRootDir();

        // 3. 确定模式并写入。部分工具桥接层会把未提供的可选字符串转换为空串，
        // 因此只有另一种载荷缺失时，空字符串才表示显式的空内容。
        boolean hasValue = value != null && (!value.isEmpty() || content == null);
        boolean hasContent = content != null && (!content.isEmpty() || value == null);
        if (hasValue) {
            // KV 模式
            try {
                contextStore.writeKV(projectRoot, key, value, creator);
                return "Successfully wrote KV entry: " + key;
            } catch (Exception e) {
                return "WRITE_FAILED: Failed to write KV entry '" + key + "': " + e.getMessage();
            }
        } else if (hasContent) {
            // 文档模式
            String mimeType = (type == null || type.isBlank()) ? "text/plain" : type;
            try {
                contextStore.writeDoc(projectRoot, key, content, mimeType, creator);
                return "Successfully wrote document entry: " + key + " (type: " + mimeType + ")";
            } catch (Exception e) {
                return "WRITE_FAILED: Failed to write document entry '" + key
                        + "': " + e.getMessage();
            }
        } else {
            // value 和 content 都为空
            return "PARAM_MISSING: Either 'value' (KV mode) or 'content' (document mode) must be provided";
        }
    }

    @Override
    public Collection<FunctionTool> getSolonTools() {
        return this.getTools();
    }
}
