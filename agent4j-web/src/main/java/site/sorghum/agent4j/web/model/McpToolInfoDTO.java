package site.sorghum.agent4j.web.model;

/**
 * MCP 工具信息 DTO。
 *
 * @author Sorghum
 */
public class McpToolInfoDTO {

    /** 工具唯一名称 */
    public String name;

    /** 工具描述 */
    public String description;

    public McpToolInfoDTO() {}

    public McpToolInfoDTO(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
