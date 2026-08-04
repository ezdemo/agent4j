package site.sorghum.loopra.web.model;

import lombok.Data;

/** Web plan-mode state change request. */
@Data
public class PlanModeRequest {
    private String workspaceHash;
    private String sessionName;
    private boolean enabled;
}
