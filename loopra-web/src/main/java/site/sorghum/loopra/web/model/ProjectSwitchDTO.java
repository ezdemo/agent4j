package site.sorghum.loopra.web.model;

/**
 * Project switch result.
 */
public record ProjectSwitchDTO(
        String message,
        String workspace,
        SessionCurrentDTO currentSession
) {
}
