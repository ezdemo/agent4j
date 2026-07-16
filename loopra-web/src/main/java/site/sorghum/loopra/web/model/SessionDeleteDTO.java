package site.sorghum.loopra.web.model;

/**
 * 删除会话结果。
 */
public record SessionDeleteDTO(
        String message,
        String workspaceHash,
        String sessionName
) {
}
