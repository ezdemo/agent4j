package site.sorghum.loopra.web.model;

import java.util.List;

/**
 * Git 变更总览。
 */
public record GitDiffDTO(
        String branch,
        List<GitFileChangeDTO> changed,
        List<GitFileChangeDTO> untracked,
        int count
) {
}
