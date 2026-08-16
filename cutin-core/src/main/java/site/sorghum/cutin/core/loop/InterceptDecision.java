package site.sorghum.cutin.core.loop;

import site.sorghum.cutin.core.context.LoopContext;

import java.util.Objects;

/**
 * 拦截器返回的决策。
 *
 * <p>决策类型决定拦截链与循环引擎的下一步行为：
 * PASS/MODIFIED 继续链；SKIP_STEP/GOTO/RETRY/SUSPEND/ABORT 终止当前链并
 * 驱动引擎做对应的流转。</p>
 */
public final class InterceptDecision {

    /** 决策类型。 */
    public enum Type {
        /** 继续链，不改动任何内容。 */
        PASS,
        /** 携带新上下文或替换载荷，继续链。 */
        MODIFIED,
        /** 跳过当前步骤。 */
        SKIP_STEP,
        /** 跳转到指定节点。 */
        GOTO,
        /** 重试当前节点。 */
        RETRY,
        /** 挂起循环，等待外部输入。 */
        SUSPEND,
        /** 中止循环。 */
        ABORT
    }

    /** 决策类型。 */
    private final Type type;
    /** MODIFIED 时携带的新上下文（可为 null）。 */
    private final LoopContext context;
    /** GOTO 时的目标节点 id。 */
    private final String targetNodeId;
    /** 决策原因。 */
    private final String reason;
    /** 替换后的载荷（模型请求、增量块、工具结果等）。 */
    private final Object payload;

    /** 私有构造，统一通过静态工厂创建。 */
    private InterceptDecision(
        Type type,
        LoopContext context,
        String targetNodeId,
        String reason,
        Object payload
    ) {
        this.type = type;
        this.context = context;
        this.targetNodeId = targetNodeId;
        this.reason = reason;
        this.payload = payload;
    }

    /** 继续链，不改动任何内容。 */
    public static InterceptDecision pass() {
        return new InterceptDecision(Type.PASS, null, null, null, null);
    }

    /** 携带新上下文继续链。 */
    public static InterceptDecision modified(LoopContext context) {
        return modified(context, null);
    }

    /** 携带新上下文与替换载荷继续链。 */
    public static InterceptDecision modified(LoopContext context, Object payload) {
        return new InterceptDecision(
            Type.MODIFIED,
            Objects.requireNonNull(context, "context"),
            null,
            null,
            payload
        );
    }

    /**
     * 只替换正在处理的产物（模型请求、流增量块、工具结果等），不改循环上下文。
     */
    public static InterceptDecision replace(Object payload) {
        return new InterceptDecision(Type.MODIFIED, null, null, null, payload);
    }

    /** 跳过当前步骤。 */
    public static InterceptDecision skipStep() {
        return new InterceptDecision(Type.SKIP_STEP, null, null, null, null);
    }

    /** 跳转到指定节点。 */
    public static InterceptDecision gotoNode(String targetNodeId) {
        return new InterceptDecision(Type.GOTO, null, Objects.requireNonNull(targetNodeId, "targetNodeId"), null, null);
    }

    /** 默认原因重试当前节点。 */
    public static InterceptDecision retry() {
        return retry("retry");
    }

    /** 携带原因重试当前节点。 */
    public static InterceptDecision retry(String reason) {
        return new InterceptDecision(Type.RETRY, null, null, reason, null);
    }

    /** 挂起循环，等待外部输入（例如人工审批）。 */
    public static InterceptDecision suspend(String reason) {
        return new InterceptDecision(Type.SUSPEND, null, null, reason, null);
    }

    /** 中止循环。 */
    public static InterceptDecision abort(String reason) {
        return new InterceptDecision(Type.ABORT, null, null, reason, null);
    }

    /** 决策类型。 */
    public Type type() {
        return type;
    }

    /** 是否为终止当前链的决策（除 PASS/MODIFIED 外都是）。 */
    public boolean isTerminal() {
        return type != Type.PASS && type != Type.MODIFIED;
    }

    /** 是否携带了新的上下文或载荷。 */
    public boolean isModified() {
        return type == Type.MODIFIED;
    }

    /** 是否中止。 */
    public boolean isAbort() {
        return type == Type.ABORT;
    }

    /** 是否挂起。 */
    public boolean isSuspend() {
        return type == Type.SUSPEND;
    }

    /** 是否跳过当前步骤。 */
    public boolean isSkipStep() {
        return type == Type.SKIP_STEP;
    }

    /** 是否跳转节点。 */
    public boolean isGoto() {
        return type == Type.GOTO;
    }

    /** 是否重试。 */
    public boolean isRetry() {
        return type == Type.RETRY;
    }

    /** 新上下文。 */
    public LoopContext context() {
        return context;
    }

    /** 目标节点 id。 */
    public String targetNodeId() {
        return targetNodeId;
    }

    /** 决策原因。 */
    public String reason() {
        return reason;
    }

    /** 替换后的载荷。 */
    public Object payload() {
        return payload;
    }
}
