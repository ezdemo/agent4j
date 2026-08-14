package site.sorghum.loopra.web.model;

/**
 * 会话隔离分支模式状态。
 *
 * @param worktreeMode 是否开启隔离分支模式
 * @param mergeMode    隔离分支合并模式：manual / ai-auto / ai-auto-approve
 */
public record SessionWorktreeModeDTO(
        String workspaceHash,
        String sessionName,
        boolean worktreeMode,
        String mergeMode
) {
}
