package site.sorghum.agent4j.web.model;

import lombok.Data;

/**
 * 快照撤回请求体。
 *
 * @author Sorghum
 */
@Data
public class SnapshotRollbackRequest {

    /** 要撤回的消息 ID */
    private String msgId;

    /** 工作区 hash */
    private String workspaceHash;
}
