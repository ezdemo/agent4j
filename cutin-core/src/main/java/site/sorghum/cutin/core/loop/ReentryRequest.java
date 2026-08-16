package site.sorghum.cutin.core.loop;

import java.util.Map;

/**
 * 重入请求：从指定状态版本回到指定节点继续执行。
 *
 * <p>重入前可合并变量与产物覆盖表，用于注入新一轮输入或修正数据；
 * {@code baseStateVersion} 用于定位要恢复的历史快照。</p>
 */
public record ReentryRequest(
    String nodeId,
    long baseStateVersion,
    Map<String, Object> overrides,
    Map<String, Object> artifactOverrides,
    String reason
) {

    /** 记录构造校验：对覆盖表做不可变拷贝。 */
    public ReentryRequest {
        overrides = overrides == null ? Map.of() : Map.copyOf(overrides);
        artifactOverrides = artifactOverrides == null ? Map.of() : Map.copyOf(artifactOverrides);
    }

    /** 快捷构造一个不覆盖产物的重入请求。 */
    public ReentryRequest(
        String nodeId,
        long baseStateVersion,
        Map<String, Object> overrides,
        String reason
    ) {
        this(nodeId, baseStateVersion, overrides, Map.of(), reason);
    }
}
