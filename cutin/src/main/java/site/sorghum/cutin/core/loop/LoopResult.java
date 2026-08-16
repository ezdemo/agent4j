package site.sorghum.cutin.core.loop;

import site.sorghum.cutin.core.state.LoopSnapshot;

/**
 * 循环执行结果：状态、结束节点、消息与最终快照。
 */
public record LoopResult(
    String loopId,
    Status status,
    String nodeId,
    String message,
    LoopSnapshot finalSnapshot
) {

    /** 生成状态不同的新结果。 */
    public LoopResult withStatus(Status status) {
        return new LoopResult(loopId, status, nodeId, message, finalSnapshot);
    }

    /** 生成消息不同的新结果。 */
    public LoopResult withMessage(String message) {
        return new LoopResult(loopId, status, nodeId, message, finalSnapshot);
    }

    /** 生成结束节点不同的新结果。 */
    public LoopResult withNodeId(String nodeId) {
        return new LoopResult(loopId, status, nodeId, message, finalSnapshot);
    }

    /** 生成最终快照不同的新结果。 */
    public LoopResult withSnapshot(LoopSnapshot snapshot) {
        return new LoopResult(loopId, status, nodeId, message, snapshot);
    }

    /** 循环结束状态。 */
    public enum Status {
        /** 正常完成。 */
        COMPLETED,
        /** 挂起等待外部输入。 */
        SUSPENDED,
        /** 执行失败。 */
        FAILED,
        /** 被取消。 */
        CANCELLED,
        /** 被拦截器中止。 */
        ABORTED
    }
}
