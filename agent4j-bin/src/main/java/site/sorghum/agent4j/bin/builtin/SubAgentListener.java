package site.sorghum.agent4j.bin.builtin;

import site.sorghum.agent4j.bin.agent.AgentLoopListener;

/**
 * 子代理事件监听器 —— 将子代理的推理和工具调用输出到 stderr。
 * 替代 TaskTool 中的匿名 AgentLoopListener。
 *
 * @author Sorghum
 */
public class SubAgentListener implements AgentLoopListener {

    @Override
    public void onReasoning(String r) {
        System.err.println("[sub] " + r);
    }

    @Override
    public void onToolCall(String n, String a) {
        System.err.println("[sub] 🔧 " + n);
    }

    @Override
    public void onToolResult(String n, String r) {
        String d = r != null && r.length() > 100 ? r.substring(0, 100) + "…" : r;
        System.err.println("[sub] 📦 " + n + " → " + d);
    }
}
