package site.sorghum.cutin.core.loop;

/** 挂起信号：通常由 SUSPEND 决策抛出，循环会以 SUSPENDED 状态等待外部输入。 */
public final class LoopSuspendException extends RuntimeException {

    /** 创建异常并携带挂起原因。 */
    public LoopSuspendException(String message) {
        super(message);
    }
}
