package site.sorghum.loopra.web.model;

/**
 * 项目会话中可见的 Skill 元数据。
 */
public record ProjectSkillMetaDTO(
        String name,
        String description,
        String scope,
        String mountAlias,
        String path
) {
}
