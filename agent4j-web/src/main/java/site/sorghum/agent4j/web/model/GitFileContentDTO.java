package site.sorghum.agent4j.web.model;

/**
 * Git 仓库中指定版本的文件内容。
 *
 * @author Sorghum
 */
public record GitFileContentDTO(
        String content
) {
}
