package site.sorghum.agent4j.web.service;

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
     */
    public synchronized void send(String eventType, String data) {
        if (completed.get()) return;
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("event: ").append(eventType).append("\n");
            sb.append("data: ").append(data).append("\n\n");
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            complete();
        }
    }

    public void sendContent(String token) {
        send("content", escapeJson(token));
    }

    public void sendReasoning(String token) {
        send("reasoning", escapeJson(token));
    }

    public void sendToolCall(String name, String args) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"name\":\"").append(escapeJson(name)).append("\"");
        if (args != null && !args.isEmpty()) {
            sb.append(",\"args\":").append(args.startsWith("{") ? args : escapeJson(args));
        }
        sb.append("}");
        send("tool_call", sb.toString());
    }

    public void sendToolResult(String name, String result) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"name\":\"").append(escapeJson(name)).append("\"");
        sb.append(",\"result\":").append(escapeJson(result != null ? result : ""));
        sb.append("}");
        send("tool_result", sb.toString());
    }

    public void sendUsage(int promptTokens, int completionTokens, int totalTokens,
                          int cacheHit, int cacheMiss) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"promptTokens\":").append(promptTokens).append(",");
        sb.append("\"completionTokens\":").append(completionTokens).append(",");
        sb.append("\"totalTokens\":").append(totalTokens).append(",");
        sb.append("\"cacheHit\":").append(cacheHit).append(",");
        sb.append("\"cacheMiss\":").append(cacheMiss);
        sb.append("}");
        send("usage", sb.toString());
    }

    public void complete() {
        if (completed.compareAndSet(false, true)) {
            try {
                send("done", "{\"done\":true}");
                out.flush();
                out.close();
            } catch (IOException ignored) {}
            completionFuture.complete(null);
        }
    }

    public void sendError(String message) {
        send("error", "{\"error\":\"" + escapeJson(message) + "\"}");
        complete();
    }

    public boolean isCompleted() {
        return completed.get();
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
