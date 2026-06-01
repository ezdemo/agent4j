package site.sorghum.agent4j.web.model;

/**
 * 聊天结果。
 */
public record ChatResultDTO(
    String reply,
    long elapsedMs,
    UsageDTO usage
) {}
