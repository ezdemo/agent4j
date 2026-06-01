package site.sorghum.agent4j.web.model;

/**
 * Agent 整体状态信息。
 */
public record AgentStatusDTO(
    boolean ready,
    String model,
    String workspace,
    int cacheSize,
    int historySize,
    boolean planMode,
    boolean hitlMode,
    String sessionName,
    long promptTokens,
    long completionTokens
) {}
