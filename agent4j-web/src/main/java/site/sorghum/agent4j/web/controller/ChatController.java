package site.sorghum.agent4j.web.controller;

import org.noear.solon.annotation.*;
import org.noear.solon.core.handle.Context;
import site.sorghum.agent4j.web.model.ApiResponse;
import site.sorghum.agent4j.web.model.ChatRequest;
import site.sorghum.agent4j.web.model.ChatResultDTO;
import site.sorghum.agent4j.web.service.AgentService;
import site.sorghum.agent4j.web.service.SseEmitter;

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
    public ApiResponse<ChatResultDTO> chat(@Body ChatRequest request) throws Exception {
        if (!agentService.isReady()) {
            return ApiResponse.fail("Agent 未初始化，请检查 ~/.agent4j/config.json");
        }
        if (request == null || request.message == null || request.message.trim().isEmpty()) {
            return ApiResponse.fail("message 不能为空");
        }
        long t0 = System.currentTimeMillis();
        String workspacePath = agentService.resolveWorkspacePath(request.workspaceHash);
        if (workspacePath == null) workspacePath = agentService.getWorkspace();
        String reply = agentService.chat(request.message.trim(), workspacePath, request.sessionName);
        long elapsed = System.currentTimeMillis() - t0;

        ChatResultDTO data = new ChatResultDTO(
                reply, elapsed,
                agentService.getSessionUsageMap(workspacePath, request.sessionName)
        );
        return ApiResponse.ok(data);
    }

    /**
     * 中断当前聊天 —— POST /api/chat/abort
     */
    @Post
    @Mapping("/abort")
    public ApiResponse<String> abort() {
        agentService.abortCurrentChat();
        return ApiResponse.ok("已发送中断请求");
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
        String workspacePath = agentService.resolveWorkspacePath(request.workspaceHash);
        if (workspacePath == null) workspacePath = agentService.getWorkspace();
        final String resolvedPath = workspacePath;
        final String sessionName = request.sessionName;

        Thread chatThread = new Thread(() -> {
            try {
                agentService.chatStream(message, resolvedPath, sessionName, emitter);
            } catch (Exception e) {
                try {
                    emitter.sendError(e.getMessage());
                } catch (Exception ex) {
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
