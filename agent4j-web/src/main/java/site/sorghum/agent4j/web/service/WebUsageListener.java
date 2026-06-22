package site.sorghum.agent4j.web.service;

import site.sorghum.agent4j.bin.agent.core.Agent4jAgent;
import site.sorghum.agent4j.bin.agent.listener.AgentLoopListener;

/**
 * Web 端 token 用量追踪监听器 —— 替代 AgentService 中的匿名 AgentLoopListener。
 *
 * @author Sorghum
 */
public class WebUsageListener implements AgentLoopListener {

    private final Agent4jAgent agent;

    public WebUsageListener(Agent4jAgent agent) {
        this.agent = agent;
    }

    @Override
    public void onUsage(String model, int promptTokens, int completionTokens, int totalTokens,
                        int cacheHit, int cacheMiss) {
        agent.addUsage(model, promptTokens, completionTokens, cacheHit, cacheMiss);
    }
}
