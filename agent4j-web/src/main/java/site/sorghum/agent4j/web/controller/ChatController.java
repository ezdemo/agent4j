package site.sorghum.agent4j.web.controller;

import org.noear.solon.annotation.*;
import org.noear.solon.core.handle.Context;

import site.sorghum.agent4j.web.model.ApiResponse;
import site.sorghum.agent4j.web.model.ChatRequest;
import site.sorghum.agent4j.web.service.AgentService;
import site.sorghum.agent4j.web.service.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 聊天 API 控制器 —— 同步聊天 + SSE 流式聊天。
 *
 * @author Sorghum
 */
@Controller
@Mapping("/api/chat")
public class ChatController {

    @Inject
    private AgentService agentService;

    /**
     * 同步聊天 —— POST /api/chat
     */
    @Post
    @Mapping("")
    public Object chat(@Body ChatRequest request) throws Exception {
        if (!agentService.isReady()) {
            return ApiResponse.fail("Agent 未初始化，请检查 ~/.agent4j/config.json");
        }
        if (request == null || request.message == null || request.message.trim().isEmpty()) {
            return ApiResponse.fail("message 不能为空");
        }
        try {
            long t0 = System.currentTimeMillis();
            String reply = agentService.chat(request.message.trim());
            long elapsed = System.currentTimeMillis() - t0;

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("reply", reply);
            data.put("elapsedMs", elapsed);
            data.put("usage", agentService.getUsage());
            return ApiResponse.ok(data);
        } catch (Exception e) {
            return ApiResponse.fail("聊天出错: " + e.getMessage());
        }
    }

    /**
     * SSE 流式聊天 —— POST /api/chat/stream
     */
    @Post
    @Mapping("/stream")
    public void chatStream(@Body ChatRequest request, Context ctx) throws Exception {
        if (!agentService.isReady()) {
            ctx.outputAsJson("{\"success\":false,\"error\":\"Agent 未初始化\"}");
            return;
        }
        if (request == null || request.message == null || request.message.trim().isEmpty()) {
            ctx.outputAsJson("{\"success\":false,\"error\":\"message 不能为空\"}");
            return;
        }

        SseEmitter emitter = new SseEmitter(ctx);
        final String message = request.message.trim();
        Thread chatThread = new Thread(() -> {
            try {
                agentService.chatStream(message, emitter);
            } catch (Exception e) {
                try {
                    emitter.sendError(e.getMessage());
                } catch (Exception ex) {
                    // SSE连接可能已断开，忽略异常
                    System.err.println("[web] 发送错误信息失败（可能SSE连接已断开）: " + ex.getMessage());
                }
            }
        }, "agent4j-chat-stream");
        chatThread.setDaemon(true);
        chatThread.start();

        // ★ 关键：阻塞 handler 线程直到 SSE 流结束，防止 Solon 提前关闭 OutputStream
        emitter.awaitCompletion();
    }
}
