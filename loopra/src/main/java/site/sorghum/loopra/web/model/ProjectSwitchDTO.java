package site.sorghum.loopra.web.model;

/**
  * 项目切换结果。
 */
public record ProjectSwitchDTO(
        String message,
        String workspace,
        SessionCurrentDTO currentSession
) {
}
