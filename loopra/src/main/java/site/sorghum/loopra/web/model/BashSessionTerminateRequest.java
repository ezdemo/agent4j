package site.sorghum.loopra.web.model;

import lombok.Data;

/** Web 手动关闭后台 bash 会话请求。 */
@Data
public class BashSessionTerminateRequest {
    private String sessionId;
    private String workspaceHash;
}
