package site.sorghum.agent4j.web.model;

/**
 * Git Diff 内容 —— 包含完整的 unified diff 文本和 stat 变更统计摘要。
 *
 * @author Sorghum
 */
public record GitDiffContentDTO(
        String diff,
        String stat
) {
}
