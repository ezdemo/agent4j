package site.sorghum.loopra.bin.agent.output;

import site.sorghum.loopra.tool.AgentOutput;

/**
 * 父代理输出传递桥 —— 在工具执行线程上持有父 Agent 的 {@link AgentOutput}。
 * <p>
 * AgentLoop 在异步分发工具前调用 {@link #set} 设置当前输出通道，
 * 子代理工具在同一线程内通过 {@link #get} 读取，
 * 使子代理的流式输出能经父代理的输出通道（Console/SseEmitter）实时推送给用户。
 * </p>
 *
 * @author Sorghum
 */
public final class ParentOutputHolder {

    private static final ThreadLocal<AgentOutput> PARENT_OUTPUT_TL = new ThreadLocal<>();

    private ParentOutputHolder() {
    }

    /**
     * 在当前工作线程上设置父 AgentOutput（传 null 忽略）。
     */
    public static void set(AgentOutput output) {
        if (output != null) {
            PARENT_OUTPUT_TL.set(output);
        }
    }

    /**
     * 获取当前线程的父 AgentOutput。
     */
    public static AgentOutput get() {
        return PARENT_OUTPUT_TL.get();
    }

    /**
     * 清除当前线程的父 AgentOutput（finally 中调用）。
     */
    public static void clear() {
        PARENT_OUTPUT_TL.remove();
    }
}
