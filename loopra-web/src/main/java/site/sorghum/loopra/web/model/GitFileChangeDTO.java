package site.sorghum.loopra.web.model;

/**
 * Git 文件变更项。
 */
public record GitFileChangeDTO(
        String path,
        String index,
        String workTree,
        String status
) {
}
