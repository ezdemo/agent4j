package site.sorghum.agent4j.bin;

import lombok.Getter;
import site.sorghum.agent4j.bin.agent.Agent4jAgent;
import site.sorghum.agent4j.bin.agent.AgentLoopListener;

/**
 * CLI 控制台的用量追踪监听器 —— 替代 Agent4jApp 中的匿名 AgentLoopListener。
 *
 * @author Sorghum
 */
public class ConsoleUsageListener implements AgentLoopListener {

    @Getter
    private final int[] lastUsage = {0, 0, 0, 0, 0};
    private final Agent4jAgent agent;

    public ConsoleUsageListener(Agent4jAgent agent) {
        this.agent = agent;
    }

    @Override
    public void onUsage(String model, int promptTokens, int completionTokens, int totalTokens,
                        int cacheHit, int cacheMiss) {
        lastUsage[0] = promptTokens;
        lastUsage[1] = completionTokens;
        lastUsage[2] = totalTokens;
        lastUsage[3] = cacheHit;
        lastUsage[4] = cacheMiss;
        agent.addUsage(model, promptTokens, completionTokens, cacheHit, cacheMiss);
    }

}
