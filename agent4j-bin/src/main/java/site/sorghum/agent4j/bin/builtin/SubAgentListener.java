package site.sorghum.agent4j.bin.builtin;

import lombok.extern.slf4j.Slf4j;
import site.sorghum.agent4j.bin.agent.listener.AgentLoopListener;

/**
 * 子代理事件监听器 —— 将子代理的推理和工具调用输出到 stderr。
 * 替代 SubAgentTool 中的匿名 AgentLoopListener。
 *
 * @author Sorghum
 */
@Slf4j
public class SubAgentListener implements AgentLoopListener {

    @Override
    public void onReasoning(String r) {
        log.info("[sub] {}", r);
    }

    @Override
    public void onToolCall(String n, String a) {
        log.info("[sub] \uD83D\uDD27 {}", n);
    }

    @Override
    public void onToolResult(String n, String r) {
        String d = r != null && r.length() > 100 ? r.substring(0, 100) + "…" : r;
        log.info("[sub] 📦 " + n + " → " + d);
    }
}
