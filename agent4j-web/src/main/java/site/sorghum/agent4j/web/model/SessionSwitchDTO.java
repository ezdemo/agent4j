package site.sorghum.agent4j.web.model;

/**
 * 切换会话结果。
 */
public record SessionSwitchDTO(
    String workspaceHash,
    String sessionName,
    boolean switched
) {}
