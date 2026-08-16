package site.sorghum.loopra.web.model;

/**
 * 会话隔离分支状态。
 *
 * @param exists       隔离分支是否已创建并注册
 * @param worktreePath 隔离分支磁盘路径
 * @param branch       隔离分支检出分支
 * @param dirty        隔离分支是否有未提交改动
 * @param mainBranch   主项目当前分支
 * @param message      状态说明（如"项目非 Git 仓库"）
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
