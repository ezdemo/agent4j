package site.sorghum.loopra.bin.agent.listener;

/**
 * AgentLoopListener 的无操作空实现 —— 替代匿名类 {@code new AgentLoopListener() {}}。
 *
 * @author Sorghum
 */
public final class NoOpAgentLoopListener implements AgentLoopListener {

    public static final NoOpAgentLoopListener INSTANCE = new NoOpAgentLoopListener();

    private NoOpAgentLoopListener() {
    }
}
