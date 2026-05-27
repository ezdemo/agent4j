package site.sorghum.agent4j.web.service;

import org.noear.snack4.ONode;
import org.noear.solon.core.handle.Context;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SSE (Server-Sent Events) 流式输出工具类。
 *
 * @author Sorghum
 */
public class SseEmitter {

    private final OutputStream out;
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final CompletableFuture<Void> completionFuture = new CompletableFuture<>();

    /**
     * 从 Solon Context 创建 SSE emitter。
     */
    public SseEmitter(Context ctx) throws IOException {
        ctx.contentType("text/event-stream; charset=utf-8");
        ctx.headerSet("Cache-Control", "no-cache");
        ctx.headerSet("Connection", "keep-alive");
        ctx.headerSet("X-Accel-Buffering", "no");
        this.out = ctx.outputStream();
    }

    /**
     * 发送一个 SSE 事件。
     * <p>
     * 当OutputStream已关闭（如页面刷新断开SSE连接）时，会抛出IOException或RuntimeException。
     * 此处捕获所有异常，设置completed标志，后续调用直接返回，让Agent继续执行完成。
     * </p>
     */
    public synchronized void send(String eventType, String data) {
        if (completed.get()) return;
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("event: ").append(eventType).append("\n");
            sb.append("data: ").append(data).append("\n\n");
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (Exception e) {
            // IOException 或 RuntimeException（如 OutputStream 已关闭时抛出的 IllegalStateException）
            // 设置completed标志，后续调用直接返回，让Agent继续执行完成
            completed.set(true);
            completionFuture.complete(null);
        }
    }

    public void sendContent(String token) {
        send("content", escapeJson(token));
    }

    public void sendReasoning(String token) {
        send("reasoning", escapeJson(token));
    }

    public void sendToolCall(String name, String args) {
        ONode node = ONode.ofJson("{}").asObject();
        node.set("name", name);
        if (args != null && !args.isEmpty()) {
            node.set("args", ONode.ofJson(args));
        }
        send("tool_call", node.toJson());
    }

    public void sendToolResult(String name, String result) {
        ONode node = ONode.ofJson("{}").asObject();
        node.set("name", name);
        node.set("result", result != null ? result : "");
        send("tool_result", node.toJson());
    }

    public void sendUsage(int promptTokens, int completionTokens, int totalTokens,
                          int cacheHit, int cacheMiss) {
        ONode node = ONode.ofJson("{}").asObject();
        node.set("promptTokens", promptTokens);
        node.set("completionTokens", completionTokens);
        node.set("totalTokens", totalTokens);
        node.set("cacheHit", cacheHit);
        node.set("cacheMiss", cacheMiss);
        send("usage", node.toJson());
    }

    public void complete() {
        if (completed.compareAndSet(false, true)) {
            try {
                // 尝试发送完成事件，如果OutputStream已关闭则忽略异常
                send("done", "{}" );
            } catch (Exception ignored) {
                // send方法内部已处理异常，此处捕获以防万一
            }
            try {
                out.flush();
                out.close();
            } catch (Exception ignored) {
                // 流可能已关闭，忽略异常
            }
            completionFuture.complete(null);
        }
    }

    public void sendError(String message) {
        ONode node = ONode.ofJson("{}").asObject();
        node.set("error", message != null ? message : "unknown error");
        send("error", node.toJson());
        complete();
    }

    public boolean isCompleted() {
        return completed.get();
    }

    /**
     * 阻塞当前线程直到 SSE 流结束（用于保持 handler 线程存活）。
     */
    public void awaitCompletion() {
        try {
            completionFuture.get();
        } catch (InterruptedException | ExecutionException ignored) {}
    }

    /**
     * 阻塞当前线程直到 SSE 流结束，或超时。
     */
    public void awaitCompletion(long timeoutMs) {
        try {
            completionFuture.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException ignored) {}
    }

    static String escapeJson(String s) {
        if (s == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append("\"");
        return sb.toString();
    }
}
