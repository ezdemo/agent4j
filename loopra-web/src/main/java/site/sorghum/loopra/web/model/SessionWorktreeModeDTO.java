package site.sorghum.loopra.web.model;

/**
 * 会话工作树隔离模式状态。
 *
 * @param worktreeMode 是否开启工作树隔离模式
 * @param mergeMode    工作树合并模式：manual / ai-auto / ai-auto-approve
 */
public record SessionWorktreeModeDTO(
        String workspaceHash,
        String sessionName,
        boolean worktreeMode,
        String mergeMode
) {
}
