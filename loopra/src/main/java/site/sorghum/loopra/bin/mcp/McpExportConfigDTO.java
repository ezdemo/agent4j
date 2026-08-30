package site.sorghum.loopra.bin.mcp;

import java.util.List;

/** Loopra 工具 MCP 发布配置及可选工具清单。 */
public class McpExportConfigDTO {

    public boolean enabled;
    public String name;
    public String version;
    public String channel;
    public String endpoint;
    public List<String> allowedTools;
    public List<McpExportToolInfoDTO> tools;
    public String endpointUrl;

    public McpExportConfigDTO() {
    }

    public McpExportConfigDTO(boolean enabled, String name, String version, String channel,
                              String endpoint, List<String> allowedTools,
                              List<McpExportToolInfoDTO> tools, String endpointUrl) {
        this.enabled = enabled;
        this.name = name;
        this.version = version;
        this.channel = channel;
        this.endpoint = endpoint;
        this.allowedTools = allowedTools;
        this.tools = tools;
        this.endpointUrl = endpointUrl;
    }
}
