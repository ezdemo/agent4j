package site.sorghum.loopra.web.model;

/**
 * 当前会话实际使用的开发环境。
 *
 * <p>后端只负责描述 Agent 的执行根目录；Git 提交、合并和推送由 Desktop 端执行。</p>
 */
public record EnvironmentStatusDTO(
        String workspaceHash,
        String sessionName,
        String mode,
        String mainPath,
        String mainBranch,
        boolean mainDirty,
        String currentPath,
        String currentBranch,
        boolean currentDirty,
        boolean worktreeExists,
        boolean agentRunning,
        String message
) {
}
