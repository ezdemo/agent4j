package site.sorghum.cutin.core.loop;

/**
 * Step 的密封结果类型，表示步骤执行后的流转意图。
 */
public sealed interface StepResult
    permits StepResult.Continue, StepResult.Repeat, StepResult.Goto, StepResult.Suspend, StepResult.Exit, StepResult.Fail {

    /** 继续沿默认边流转到下一节点。 */
    record Continue() implements StepResult {
        /** 单例实例。 */
        public static final Continue INSTANCE = new Continue();
    }

    /** 重复执行当前节点。 */
    record Repeat() implements StepResult {
        /** 单例实例。 */
        public static final Repeat INSTANCE = new Repeat();
    }

    /** 显式跳转到指定节点。 */
    record Goto(String targetNodeId) implements StepResult {
    }

    /** 挂起循环并携带原因。 */
    record Suspend(String reason) implements StepResult {
    }

    /** 正常退出循环。 */
    record Exit() implements StepResult {
        /** 单例实例。 */
        public static final Exit INSTANCE = new Exit();
    }

    /** 失败退出循环并携带原因。 */
    record Fail(String reason) implements StepResult {
    }
}
