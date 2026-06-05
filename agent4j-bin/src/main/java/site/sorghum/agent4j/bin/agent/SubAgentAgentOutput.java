package site.sorghum.agent4j.bin.agent;

import site.sorghum.agent4j.tool.AgentOutput;
import site.sorghum.agent4j.tool.ChoiceOption;
import site.sorghum.agent4j.tool.LogLevel;

import java.util.List;

/**
 * 子代理输出包装器 —— 将子代理的所有事件全部以 sub_ 前缀发送独立事件，
 * 不占用主代理的事件通道。前端通过独立 Modal 渲染这些事件。
 *
 * <p>事件映射对照：<pre>
 *   主代理事件                 子代理事件
 *   onContentDelta(token)  →  sendEvent("sub_content", token)
 *   onContentComplete()    →  sendEvent("sub_complete", "")
 *   onReasoningDelta(token)→  sendEvent("sub_reasoning", token)
 *   onReasoning(reasoning) →  sendEvent("sub_reasoning", reasoning)
 *   onToolCall(name,args)  →  sendEvent("sub_tool_call", {"name":"...","args":...})
 *   onToolResult(name,res) →  sendEvent("sub_tool_result", {"name":"...","result":"..."})
 *   onError(error)         →  sendEvent("sub_error", {"error":"..."})
 *   onUsage(...)           →  sendEvent("sub_usage", {...})
 *   onChoice(options)      →  sendEvent("sub_choice", {"options":[...]})
 *   onLog(level, msg)      →  sendEvent("sub_log", {"level":"...","message":"..."})
 * </pre>
 * </p>
 *
 * <p>前端通过监听 type.startsWith("sub_") 来区分是子代理事件，
 * 收集到独立的 blocks 数组中，在子代理 Modal 中渲染。</p>
 *
 * @author Sorghum
 */
public class SubAgentAgentOutput implements AgentOutput {

    private final AgentOutput delegate;
    private final String taskName;

    /**
     * @param delegate 父代理的 AgentOutput 实现（仅用于 sendEvent）
     * @param taskName 子代理的任务名称
     */
    public SubAgentAgentOutput(AgentOutput delegate, String taskName) {
        this.delegate = delegate;
        this.taskName = taskName != null ? taskName : "子代理";
    }

    // ==================== 全部事件 → sub_xxx 独立通道 ====================

    @Override
    public void onContentDelta(String token) {
        delegate.sendEvent("sub_content", escapeJson(token));
    }

    @Override
    public void onContentComplete() {
        delegate.sendEvent("sub_complete", "{\"task\":" + escapeJson(taskName) + "}");
    }

    @Override
    public void onReasoningDelta(String token) {
        delegate.sendEvent("sub_reasoning", escapeJson(token));
    }

    @Override
    public void onReasoning(String reasoning) {
        if (reasoning != null && !reasoning.isEmpty()) {
            delegate.sendEvent("sub_reasoning", escapeJson(reasoning));
        }
    }

    @Override
    public void onToolCall(String name, String args) {
        // JSON: {"name":"xxx","args":{...}}
        StringBuilder sb = new StringBuilder();
        sb.append("{\"name\":").append(escapeJson(name));
        sb.append(",\"args\":").append(args != null ? args : "{}");
        sb.append("}");
        delegate.sendEvent("sub_tool_call", sb.toString());
    }

    @Override
    public void onToolResult(String name, String result) {
        // JSON: {"name":"xxx","result":"..."}
        StringBuilder sb = new StringBuilder();
        sb.append("{\"name\":").append(escapeJson(name));
        sb.append(",\"result\":").append(escapeJson(result != null ? result : ""));
        sb.append("}");
        delegate.sendEvent("sub_tool_result", sb.toString());
    }

    @Override
    public void onUsage(int promptTokens, int completionTokens, int totalTokens,
                        int cacheHit, int cacheMiss) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"promptTokens\":").append(promptTokens).append(",");
        sb.append("\"completionTokens\":").append(completionTokens).append(",");
        sb.append("\"totalTokens\":").append(totalTokens).append(",");
        sb.append("\"cacheHit\":").append(cacheHit).append(",");
        sb.append("\"cacheMiss\":").append(cacheMiss);
        sb.append("}");
        delegate.sendEvent("sub_usage", sb.toString());
    }

    @Override
    public void onUsage(String model, int promptTokens, int completionTokens, int totalTokens,
                        int cacheHit, int cacheMiss) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"model\":").append(escapeJson(model)).append(",");
        sb.append("\"promptTokens\":").append(promptTokens).append(",");
        sb.append("\"completionTokens\":").append(completionTokens).append(",");
        sb.append("\"totalTokens\":").append(totalTokens).append(",");
        sb.append("\"cacheHit\":").append(cacheHit).append(",");
        sb.append("\"cacheMiss\":").append(cacheMiss);
        sb.append("}");
        delegate.sendEvent("sub_usage", sb.toString());
    }

    @Override
    public void onError(String error) {
        delegate.sendEvent("sub_error", "{\"error\":" + escapeJson(error) + "}");
    }

    @Override
    public void onLog(LogLevel level, String message) {
        delegate.sendEvent("sub_log",
                "{\"level\":\"" + level.name() + "\",\"message\":" + escapeJson(message) + "}");
    }

    @Override
    public void onChoice(List<ChoiceOption> options) {
        if (options != null && !options.isEmpty()) {
            StringBuilder sb = new StringBuilder("{\"options\":[");
            for (int i = 0; i < options.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("{\"title\":").append(escapeJson(options.get(i).title()));
                sb.append(",\"value\":").append(escapeJson(options.get(i).value()));
                sb.append("}");
            }
            sb.append("]}");
            delegate.sendEvent("sub_choice", sb.toString());
        }
    }

    // ==================== 委托基础方法 ====================

    @Override
    public void sendEvent(String type, String data) {
        delegate.sendEvent(type, data);
    }

    // ==================== 工具方法 ====================

    private static String escapeJson(String s) {
        if (s == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append("\"");
        return sb.toString();
    }
}
