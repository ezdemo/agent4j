package site.sorghum.loopra.web.model;

/**
 * 指定工作区会话的后台 Agent 运行状态。
 */
public record SessionStatusDTO(
        boolean running,
        String requestId
) {
}
