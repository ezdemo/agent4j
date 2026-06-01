package site.sorghum.agent4j.web.model;

import java.util.List;

/**
 * Git 变更总览。
 */
public record GitDiffDTO(
    String branch,
    List<GitFileChangeDTO> staged,
    List<GitFileChangeDTO> unstaged,
    List<GitFileChangeDTO> untracked,
    int count
) {}
