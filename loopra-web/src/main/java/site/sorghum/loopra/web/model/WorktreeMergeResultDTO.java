package site.sorghum.loopra.web.model;

import java.util.List;

/**
 * 工作树合并回主工作区结果。
 *
 * @param merged         是否已合并（冲突时 false，等待 AI/人工解决后再次调用）
 * @param conflicted     是否存在合并冲突
 * @param conflictFiles  冲突文件列表（相对仓库根）
 * @param commitId       合并后主工作区 HEAD（未合并时为 null）
 * @param worktreeRemoved 工作树是否已清理（合并成功后通常保留，由删除会话负责清理）
 * @param message        结果说明
 */
public record WorktreeMergeResultDTO(
        boolean merged,
        boolean conflicted,
        List<String> conflictFiles,
        String commitId,
        boolean worktreeRemoved,
        String message
) {
}
