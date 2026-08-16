package site.sorghum.loopra.web.model;

import java.util.List;

/**
 * Git 综合状态 —— 包含 Git 可用性、仓库初始化状态、当前分支及变更文件列表。
 *
 * @author Sorghum
 */
public record GitStatusDTO(
        boolean gitAvailable,
        boolean initialized,
        String branch,
        String workspacePath,
        List<GitFileChangeDTO> changed,
        List<GitFileChangeDTO> untracked,
        String model,
        String modelChannelId
) {
}
