package site.sorghum.agent4j.bin.mcp;

import lombok.Data;

/**
 * MCP 工具信息 DTO。
 *
 * @author Sorghum
 */
@Data
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
}
