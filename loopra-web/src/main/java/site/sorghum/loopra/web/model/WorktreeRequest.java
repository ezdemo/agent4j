package site.sorghum.loopra.web.model;

import lombok.Data;

/**
 * 工作树操作请求（创建 / 删除 / 合并回主工作区）。
 */
@Data
public class WorktreeRequest {

    /**
     * 工作区 hash（必填）
     */
    private String workspaceHash;

    /**
     * 会话名称（必填）
     */
    private String sessionName;

    /**
     * 删除时是否丢弃工作树内未合并的改动（默认 false）
     */
    private Boolean discardChanges;
}
