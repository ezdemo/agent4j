package site.sorghum.agent4j.web.model;

/**
 * 创建会话结果。
 */
public record SessionCreateDTO(
        String message,
        String workspaceHash,
        String sessionName
) {
}
