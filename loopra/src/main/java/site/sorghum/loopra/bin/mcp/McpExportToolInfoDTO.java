package site.sorghum.loopra.bin.mcp;

/** Loopra 工具的 MCP 发布选择状态。 */
public class McpExportToolInfoDTO {

    public String name;
    public String description;
    public boolean readOnly;
    public boolean enabled;
    public boolean exposed;

    public McpExportToolInfoDTO() {
    }

    public McpExportToolInfoDTO(String name, String description, boolean readOnly,
                                boolean enabled, boolean exposed) {
        this.name = name;
        this.description = description;
        this.readOnly = readOnly;
        this.enabled = enabled;
        this.exposed = exposed;
    }
}
