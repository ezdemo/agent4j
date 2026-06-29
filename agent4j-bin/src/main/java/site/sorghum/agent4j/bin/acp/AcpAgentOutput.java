package site.sorghum.agent4j.bin.acp;

import lombok.extern.slf4j.Slf4j;
import site.sorghum.agent4j.tool.AgentOutput;
import site.sorghum.agent4j.tool.ChoiceOption;
import site.sorghum.agent4j.tool.LogLevel;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * ACP 输出适配器 —— 将 Agent4j 的 AgentOutput 事件转发为 ACP session/update 通知。
 * <p>
 * 通过 {@link AcpPromptContext} 发送流式内容（文本块、思考过程、工具调用状态等），
 * 使 ACP 客户端能实时接收到 Agent 的推理过程。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
public class AcpAgentOutput implements AgentOutput {

    /** ACP session/update 通知发送器 */
    private final AcpNotificationSender notificationSender;

    /** 当前消息的内容累积缓冲区（用于按句号/换行分割发送） */
    private final StringBuilder contentBuffer = new StringBuilder();

    /** 当前思考内容累积缓冲区 */
    private final StringBuilder reasoningBuffer = new StringBuilder();

    /** 消息发送完成的通知（用于等待所有流式消息发送完毕） */
    private final CompletableFuture<Void> completionFuture = new CompletableFuture<>();

    /** 是否已完成 */
    private volatile boolean completed = false;

    public AcpAgentOutput(AcpNotificationSender notificationSender) {
        this.notificationSender = notificationSender;
    }

    @Override
    public void onContentDelta(String token) {
        if (token == null || token.isEmpty() || completed) return;
        contentBuffer.append(token);
        // 每积累到分隔符时发送一个消息块
        if (containsSentenceBoundary(contentBuffer)) {
            flushContent();
        }
    }

    @Override
    public void onContentComplete() {
        flushContent();
        notificationSender.sendContentComplete();
    }

    @Override
    public void onReasoningDelta(String token) {
        if (token == null || token.isEmpty() || completed) return;
        reasoningBuffer.append(token);
        // 思考内容积累到一定长度时发送
        if (reasoningBuffer.length() >= 50) {
            flushReasoning();
        }
    }

    @Override
    public void onReasoning(String reasoning) {
        if (reasoning == null || reasoning.isEmpty() || completed) return;
        flushReasoning();
        if (reasoning.length() > 0) {
            notificationSender.sendThought(reasoning);
        }
    }

    @Override
    public void onToolCall(String name, String args) {
        if (completed) return;
        flushContent();
        notificationSender.sendToolCall(name, args);
    }

    @Override
    public void onToolResult(String name, String result) {
        if (completed) return;
        notificationSender.sendToolResult(name, result);
    }

    @Override
    public void onUsage(int promptTokens, int completionTokens, int totalTokens,
                         int cacheHit, int cacheMiss) {
        // ACP 协议支持 usage_update 通知，可通过 AcpNotificationSender 实现
        log.debug("[acp] token usage: prompt={}, completion={}, total={}, cacheHit={}, cacheMiss={}",
                promptTokens, completionTokens, totalTokens, cacheHit, cacheMiss);
    }

    @Override
    public void onError(String error) {
        if (completed) return;
        flushContent();
        notificationSender.sendError(error);
    }

    @Override
    public void onLog(LogLevel level, String message) {
        log.debug("[acp:{}] {}", level, message);
    }

    @Override
    public void onChoice(List<ChoiceOption> options) {
        // ACP 模式下，HITL 审批通过 session/request_permission 机制处理
        // 此处简单记录
        if (options != null && !options.isEmpty()) {
            log.debug("[acp] 需要用户选择: {}", options);
        }
    }

    /**
     * 标记完成并等待所有流式消息发送完毕。
     */
    public void markCompleted() {
        this.completed = true;
        flushContent();
        flushReasoning();
        completionFuture.complete(null);
    }

    /**
     * 等待流式消息发送完成（带超时）。
     */
    public boolean awaitCompletion(long timeoutMs) {
        try {
            return completionFuture.completeOnTimeout(null, timeoutMs, TimeUnit.MILLISECONDS)
                    .get() != null;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== 内部方法 ====================

    private void flushContent() {
        if (contentBuffer.isEmpty()) return;
        String text = contentBuffer.toString();
        contentBuffer.setLength(0);
        notificationSender.sendMessage(text);
    }

    private void flushReasoning() {
        if (reasoningBuffer.isEmpty()) return;
        String text = reasoningBuffer.toString();
        reasoningBuffer.setLength(0);
        notificationSender.sendThought(text);
    }

    private boolean containsSentenceBoundary(StringBuilder sb) {
        if (sb.length() < 2) return false;
        char lastChar = sb.charAt(sb.length() - 1);
        return lastChar == '.' || lastChar == '!' || lastChar == '?' ||
               lastChar == '\n' || lastChar == '；' || lastChar == '。' ||
               lastChar == '！' || lastChar == '？';
    }

    /**
     * ACP 通知发送接口 —— 由具体的传输层实现。
     * <p>
     * 对于 stdio 传输，通过 StdioAcpAgentTransport 发送 JSON-RPC 通知；
     * 对于 WebSocket 传输，通过 WebSocket 连接发送。
     * </p>
     */
    public interface AcpNotificationSender {
        /** 发送文本消息块（agent_message_chunk） */
        void sendMessage(String text);

        /** 发送思考内容（thought） */
        void sendThought(String text);

        /** 发送内容完成信号 */
        void sendContentComplete();

        /** 发送工具调用事件 */
        void sendToolCall(String name, String args);

        /** 发送工具结果 */
        void sendToolResult(String name, String result);

        /** 发送错误信息 */
        void sendError(String error);
    }
}
