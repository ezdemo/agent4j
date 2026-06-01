package site.sorghum.agent4j.web.model;

/**
 * Skill 元数据。
 */
public record SkillMetaDTO(
    String name,
    String description,
    String scope,
    String runAs
) {}
