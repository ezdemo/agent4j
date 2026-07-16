package site.sorghum.loopra.web.model;

/**
 * Skill 元数据。
 */
public record SkillMetaDTO(
        String name,
        String description,
        String scope,
        String runAs,
        String directoryName
) {
}
