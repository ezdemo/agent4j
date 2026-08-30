package site.sorghum.loopra.bin.mcp;

import java.util.List;

/**
 * Loopra 内置工具 MCP 发布配置。
 *
 * <p>{@code allowedTools == null} 表示发布当前已启用的全部工具；空列表表示不发布任何工具，
 * 非空列表表示只发布列表中的工具。</p>
 */
public class McpExportConfig {

    /** 默认关闭，避免升级后意外暴露本地工具。 */
    public boolean enabled = false;

    /** MCP server name。 */
    public String name = "loopra";

    /** MCP server version。 */
    public String version = "1.0.0";

    /** Web transport：streamable / streamable_stateless / sse。 */
    public String channel = "streamable";

    /** MCP endpoint path。 */
    public String endpoint = "/mcp";

    /** null=全部当前已启用工具；非 null=显式白名单。 */
    public List<String> allowedTools;

    public McpExportConfig() {
    }
}
