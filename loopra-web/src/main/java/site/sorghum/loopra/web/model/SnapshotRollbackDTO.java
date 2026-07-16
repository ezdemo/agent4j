package site.sorghum.loopra.web.model;

import lombok.Data;

/**
 * 快照撤回结果 DTO。
 *
 * @author Sorghum
 */
@Data
public class SnapshotRollbackDTO {

    /** 撤回的消息 ID */
    private String msgId;

    /** 快照 commit hash */
    private String commitHash;

    /** 快照 tree hash */
    private String treeHash;

    /** 是否成功 */
    private boolean success;

    /** 结果消息 */
    private String message;

    /**
     * 被删除的用户消息文本（用于前端回填输入框）
     */
    private String rollbackUserText;

    public SnapshotRollbackDTO() {}

    public SnapshotRollbackDTO(String msgId, String commitHash, String treeHash, boolean success, String message) {
        this.msgId = msgId;
        this.commitHash = commitHash;
        this.treeHash = treeHash;
        this.success = success;
        this.message = message;
    }
}
