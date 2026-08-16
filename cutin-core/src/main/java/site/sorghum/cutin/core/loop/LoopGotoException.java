package site.sorghum.cutin.core.loop;

/**
 * 跳转信号：插件决策后，Step 或网关抛出该异常让循环跳到另一个节点。
 */
public final class LoopGotoException extends RuntimeException {

    /** 目标节点 id。 */
    private final String targetNodeId;

    /** 创建跳转异常并携带目标节点与原因。 */
    public LoopGotoException(String targetNodeId, String reason) {
        super(reason);
        this.targetNodeId = targetNodeId;
    }

    /** 目标节点 id。 */
    public String targetNodeId() {
        return targetNodeId;
    }
}
