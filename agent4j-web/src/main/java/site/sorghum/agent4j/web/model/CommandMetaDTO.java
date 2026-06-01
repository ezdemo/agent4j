package site.sorghum.agent4j.web.model;

/**
 * 斜杠命令元数据。
 */
public record CommandMetaDTO(
    String name,
    String description,
    String args
) {}
