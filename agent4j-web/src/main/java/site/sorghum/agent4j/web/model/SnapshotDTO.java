package site.sorghum.agent4j.web.model;

import lombok.Data;

/**
 * 快照信息 DTO。
 *
 * @author Sorghum
 */
@Data
public class SnapshotDTO {

    /** 消息 ID */
    private String msgId;

    /** 快照 commit hash */
    private String commitHash;

    /** 快照 tree hash */
    private String treeHash;

    /** 创建时间（毫秒时间戳） */
    private long createdAt;

    public SnapshotDTO() {}

    public SnapshotDTO(String msgId, String commitHash, String treeHash, long createdAt) {
        this.msgId = msgId;
        this.commitHash = commitHash;
        this.treeHash = treeHash;
        this.createdAt = createdAt;
    }
}
