package site.sorghum.agent4j.web.model;

import java.util.List;

/**
 * Git 提交历史记录。
 *
 * @author Sorghum
 */
public record GitCommitDTO(
        String hash,
        String shortHash,
        String author,
        String email,
        String date,
        String message
) {
    /**
     * 提交历史列表封装。
     */
    public record ListWrapper(
            List<GitCommitDTO> commits,
            int count
    ) {
    }
}
