package site.sorghum.loopra.web.model;

import java.util.List;

/**
 * 当前项目 MCP 服务器的非敏感运行摘要。
 *
 * <p>故意不返回 command、env、headers、token 等配置内容，只供会话界面展示。</p>
 */
public record ProjectMcpServerDTO(
        String name,
        String type,
        boolean enabled,
        boolean loaded,
        int toolCount,
        List<String> toolNames,
        String error
) {
}
