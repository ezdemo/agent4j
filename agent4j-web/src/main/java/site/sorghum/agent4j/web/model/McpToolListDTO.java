package site.sorghum.agent4j.web.model;

import java.util.List;

/**
 * MCP 工具列表响应 DTO。
 *
 * @author Sorghum
 */
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

    public boolean isConnected() { return connected; }
    public void setConnected(boolean connected) { this.connected = connected; }

    public List<McpToolInfoDTO> getTools() { return tools; }
    public void setTools(List<McpToolInfoDTO> tools) { this.tools = tools; }

    public List<String> getDisallowedTools() { return disallowedTools; }
    public void setDisallowedTools(List<String> disallowedTools) { this.disallowedTools = disallowedTools; }
}
