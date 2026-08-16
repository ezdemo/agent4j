package site.sorghum.loopra.web.model;

/**
 * 创建会话结果。
 */
public record SessionCreateDTO(
        String message,
        String workspaceHash,
        String sessionName
) {
}
