package site.sorghum.loopra.web.model;

import lombok.Data;

/**
 * 快照创建请求体。
 *
 * @author Sorghum
 */
@Data
public class SnapshotCreateRequest {

    /** 消息 ID（用于标识快照，建议使用前端生成的唯一 ID） */
    private String msgId;

    /** 项目 hash */
    private String workspaceHash;
}
