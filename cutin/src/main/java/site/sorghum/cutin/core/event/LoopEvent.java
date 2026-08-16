package site.sorghum.cutin.core.event;

import java.time.Instant;
import java.util.Map;

/**
 * 循环事件：类型、所属循环与节点、发生时间与附加属性。
 *
 * <p>事件是观察循环执行的主要通道，例如 PRE_LOOP、CHECKPOINT、ON_MODEL_STREAM
 * 等；事件日志保持追加写，便于追踪与审计。</p>
 */
public record LoopEvent(
    String type,
    String loopId,
    String nodeId,
    Instant timestamp,
    Map<String, Object> attributes
) {

    /** 快捷构造一个时间为当前时刻的循环事件。 */
    public LoopEvent(String type, String loopId, String nodeId, Map<String, Object> attributes) {
        this(type, loopId, nodeId, Instant.now(), attributes);
    }
}
