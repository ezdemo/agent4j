package site.sorghum.loopra.bin.mcp;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * MCP 服务器配置数据模型。
 *
 * @author Sorghum
 */
@Data
public class McpServerDTO {

    /** 服务器唯一名称（字母数字下划线连字符） */
    public String name;

    /** 连接类型：stdio / sse / streamable */
    public String type;

    /** 是否启用 */
    public boolean enabled = true;

    // ===== stdio 类型字段 =====

    /** 启动命令（type=stdio 时必填） */
    public String command;

    /** 命令行参数 */
    public List<String> args;

    /** 环境变量键值对 */
    public Map<String, String> env;

    // ===== 远程类型字段（sse/streamable） =====

    /** 远程端点 URL（type=sse/streamable 时必填） */
    public String url;

    /** 自定义请求头 */
    public Map<String, String> headers;

    /** 超时时间，如 "30s" */
    public String timeout;

    public McpServerDTO() {}

    public McpServerDTO(String name, String type, boolean enabled) {
        this.name = name;
        this.type = type;
        this.enabled = enabled;
    }
}
