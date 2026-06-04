package site.sorghum.agent4j.bin.mcp;

import lombok.Data;

import java.util.List;

/**
 * MCP 工具列表响应 DTO。
 *
 * @author Sorghum
 */
@Data
public class McpToolListDTO {

    /** 服务器是否已连接 */
    public boolean connected;

    /** 工具列表 */
    public List<McpToolInfoDTO> tools;

    /** 已被禁用的工具名称列表 */
    public List<String> disallowedTools;

    public McpToolListDTO() {}

    public McpToolListDTO(boolean connected, List<McpToolInfoDTO> tools, List<String> disallowedTools) {
        this.connected = connected;
        this.tools = tools;
        this.disallowedTools = disallowedTools;
    }
}
