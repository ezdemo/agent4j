package site.sorghum.loopra.web.model;

/**
 * 项目文件树中的一个条目。
 */
public record WorkspaceFileEntryDTO(
        String name,
        String path,
        boolean directory
) {
}
