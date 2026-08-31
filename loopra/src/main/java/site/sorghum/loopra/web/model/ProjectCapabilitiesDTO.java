package site.sorghum.loopra.web.model;

import java.util.List;

/**
 * 当前项目会话可见的 Skill/MCP 能力摘要。
 */
public record ProjectCapabilitiesDTO(
        String workspacePath,
        String mcpConfigPath,
        boolean mcpConfigExists,
        String projectSkillsPath,
        boolean projectSkillsDirectoryExists,
        List<ProjectSkillMetaDTO> skills,
        List<ProjectMcpServerDTO> mcpServers
) {
}
