package site.sorghum.cutin.core.loop;

/**
 * 重试信号：插件完成恢复动作后，Step 或网关抛出该异常让当前节点重试。
 */
public final class LoopRetryException extends RuntimeException {

    /** 创建异常并携带重试原因。 */
    public LoopRetryException(String reason) {
        super(reason);
    }
}
