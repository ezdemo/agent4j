package site.sorghum.loopra.web.model;

import java.util.List;

/**
 * 会话工作树状态。
 *
 * @param exists       工作树是否已创建并注册
 * @param worktreePath 工作树磁盘路径
 * @param branch       工作树检出分支
 * @param dirty        工作树是否有未提交改动
 * @param mainBranch   主工作区当前分支
 * @param message      状态说明（如"工作区非 Git 仓库"）
 */
public record WorktreeStatusDTO(
        String workspaceHash,
        String sessionName,
        boolean exists,
        String worktreePath,
        String branch,
        boolean dirty,
        String mainBranch,
        String message
) {
}
