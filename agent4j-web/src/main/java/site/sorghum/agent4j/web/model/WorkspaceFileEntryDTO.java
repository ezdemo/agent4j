package site.sorghum.agent4j.web.model;

/**
 * 工作区文件树中的一个条目。
 */
public record WorkspaceFileEntryDTO(
        String name,
        String path,
        boolean directory
) {
}
