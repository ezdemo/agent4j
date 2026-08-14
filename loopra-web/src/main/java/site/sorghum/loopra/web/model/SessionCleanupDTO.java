package site.sorghum.loopra.web.model;

import java.util.List;

/**
 * 清理过期会话结果。
 *
 * @param sessionNames 被删除的会话名列表（可能为空）
 */
public record SessionCleanupDTO(
        String message,
        String workspaceHash,
        List<String> sessionNames
) {
}
