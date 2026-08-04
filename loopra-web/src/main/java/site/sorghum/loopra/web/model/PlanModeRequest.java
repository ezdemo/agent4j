package site.sorghum.loopra.web.model;

import lombok.Data;

/** Web 计划模式状态切换请求。 */
@Data
public class PlanModeRequest {
    private String workspaceHash;
    private String sessionName;
    private boolean enabled;
}
