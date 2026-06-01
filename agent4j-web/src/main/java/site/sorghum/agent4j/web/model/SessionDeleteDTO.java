package site.sorghum.agent4j.web.model;

/**
 * 删除会话结果。
 */
public record SessionDeleteDTO(
    String message,
    String workspaceHash,
    String sessionName
) {}
