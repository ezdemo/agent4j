package site.sorghum.agent4j.web.service;

import org.noear.snack4.ONode;
import site.sorghum.agent4j.bin.agent.AgentOutput;

/**
 * API 输出实现 —— 将 {@link AgentOutput} 事件桥接到 {@link SseEmitter}（SSE 流式推送）。
 * <p>
 * Web 模块使用此类替代匿名内部类，实现关注点分离。
 * 所有 Agent 的输出事件（流式内容、思考过程、工具调用、Token 用量等）
 * 通过此对象实时推送到前端 SSE 连接。
 * </p>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * ApiAgentOutPut output = new ApiAgentOutPut(emitter);
 * agent.setOutput(output);
 *
 * // 使用完毕后
 * output.complete();
 * }</pre>
 *
 * @author Sorghum
 */
public class ApiAgentOutPut implements AgentOutput {

    /** 关联的 SSE 发射器 */
    private final SseEmitter emitter;

    /** 是否已完成 */
    private volatile boolean completed = false;

    /** 累计的 usage 数据（用于最终一次性发送） */
    private int lastPromptTokens;
    private int lastCompletionTokens;
    private int lastTotalTokens;
    private int lastCacheHit;
    private int lastCacheMiss;

    /**
     * 创建 API 输出实例。
     *
     * @param emitter SSE 发射器，不能为 null
     */
    public ApiAgentOutPut(SseEmitter emitter) {
        if (emitter == null) {
            throw new IllegalArgumentException("emitter 不能为 null");
        }
        this.emitter = emitter;
    }

    // ==================== 流式输出 ====================

    @Override
    public void onContentDelta(String token) {
        if (completed) return;
        try {
            emitter.sendContent(token);
        } catch (Exception e) {
            // SSE连接断开时忽略异常，继续执行
        }
    }

    @Override
    public void onContentComplete() {
        // 流式内容结束标记 — 前端可据此更新 UI
        if (completed) return;
        try {
            emitter.send("content_complete", "{}");
        } catch (Exception e) {
            // SSE连接断开时忽略异常，继续执行
        }
    }

    @Override
    public void onReasoningDelta(String token) {
        if (completed) return;
        try {
            emitter.sendReasoning(token);
        } catch (Exception e) {
            // SSE连接断开时忽略异常，继续执行
        }
    }

    @Override
    public void onReasoningComplete() {
        if (completed) return;
        try {
            emitter.send("reasoning_complete", "{}");
        } catch (Exception e) {
            // SSE连接断开时忽略异常，继续执行
        }
    }

    // ==================== 事件 ====================

    @Override
    public void onReasoning(String reasoning) {
        if (completed || reasoning == null || reasoning.isEmpty()) return;
        try {
            emitter.sendReasoning(reasoning);
        } catch (Exception e) {
            // SSE连接断开时忽略异常，继续执行
        }
    }

    @Override
    public void onToolCall(String name, String args) {
        if (completed) return;
        try {
            emitter.sendToolCall(name, args);
        } catch (Exception e) {
            // SSE连接断开时忽略异常，继续执行
        }
    }

    @Override
    public void onToolResult(String name, String result) {
        if (completed) return;
        try {
            emitter.sendToolResult(name, result);
        } catch (Exception e) {
            // SSE连接断开时忽略异常，继续执行
        }
    }

    @Override
    public void onUsage(int promptTokens, int completionTokens, int totalTokens,
                        int cacheHit, int cacheMiss) {
        if (completed) return;
        // 缓存最新 usage
        this.lastPromptTokens = promptTokens;
        this.lastCompletionTokens = completionTokens;
        this.lastTotalTokens = totalTokens;
        this.lastCacheHit = cacheHit;
        this.lastCacheMiss = cacheMiss;
        try {
            emitter.sendUsage(promptTokens, completionTokens, totalTokens, cacheHit, cacheMiss);
        } catch (Exception e) {
            // SSE连接断开时忽略异常，继续执行
        }
    }

    @Override
    public void onError(String error) {
        if (completed) return;
        try {
            emitter.sendError(error);
        } catch (Exception e) {
            // SSE连接断开时忽略异常
        }
        completed = true;
    }

    // ==================== 日志 & 消息 ====================

    @Override
    public void onLog(LogLevel level, String message) {
        if (completed || message == null) return;
        // 将日志也推送到 SSE，方便前端调试
        ONode node = ONode.ofJson("{}").asObject();
        node.set("level", level != null ? level.name() : "INFO");
        node.set("message", message);
        try {
            emitter.send("log", node.toJson());
        } catch (Exception e) {
            // SSE连接断开时忽略异常，继续执行
        }
    }

    @Override
    public void onMessage(String message) {
        if (completed || message == null) return;
        try {
            emitter.send("message", escapeJson(message));
        } catch (Exception e) {
            // SSE连接断开时忽略异常，继续执行
        }
    }

    // ==================== 生命周期 ====================

    /**
     * 标记输出完成，发送 done 事件并关闭 SSE 连接。
     */
    public void complete() {
        if (completed) return;
        completed = true;
        try {
            emitter.complete();
        } catch (Exception e) {
            // SSE连接可能已断开，忽略异常
        }
    }

    /**
     * 标记输出完成（带最终回复内容）。
     * <p>
     * 先发送 reply 事件（含完整回复内容），再关闭 SSE 连接。
     * </p>
     *
     * @param reply 最终回复文本
     */
    public void complete(String reply) {
        if (completed) return;
        if (reply != null && !reply.isEmpty()) {
            ONode replyNode = ONode.ofJson("{}").asObject();
            replyNode.set("content", reply);
            try {
                emitter.send("reply", replyNode.toJson());
            } catch (Exception e) {
                // SSE连接可能已断开，忽略异常
            }
        }
        try {
            emitter.complete();
        } catch (Exception e) {
            // SSE连接可能已断开，忽略异常
        }
        completed = true;
    }

    /**
     * 标记输出异常完成。
     * <p>
     * 发送 error 事件后关闭 SSE 连接。
     * </p>
     *
     * @param error 错误信息
     */
    public void completeWithError(String error) {
        if (completed) return;
        try {
            emitter.sendError(error);
        } catch (Exception e) {
            // SSE连接可能已断开，忽略异常
        }
        completed = true;
    }

    /**
     * 是否已完成。
     */
    public boolean isCompleted() {
        return completed;
    }

    // ==================== 辅助 ====================

    private static String escapeJson(String s) {
        // 复用 SseEmitter 的 JSON 转义逻辑
        return SseEmitter.escapeJson(s);
    }
}
