package site.sorghum.cutin.core.loop;

/** 循环中止信号：通常由 ABORT 决策抛出，引擎会以 ABORTED 状态结束循环。 */
public final class LoopAbortException extends RuntimeException {

    /** 创建异常并携带中止原因。 */
    public LoopAbortException(String message) {
        super(message);
    }
}
