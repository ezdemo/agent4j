package site.sorghum.agent4j.web.model;

/**
 * 切换工作区结果。
 */
public record WorkspaceSwitchDTO(
    String message,
    String workspace,
    SessionCurrentDTO currentSession
) {}
