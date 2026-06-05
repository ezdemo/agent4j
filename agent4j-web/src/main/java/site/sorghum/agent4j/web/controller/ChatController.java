package site.sorghum.agent4j.web.controller;

import io.swagger.annotations.*;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.*;
import org.noear.solon.core.handle.Context;
import site.sorghum.agent4j.bin.agent.UserMessage;
import site.sorghum.agent4j.web.model.ApiResponse;
import site.sorghum.agent4j.web.model.ChatRequest;
import site.sorghum.agent4j.web.model.ChatResultDTO;
import site.sorghum.agent4j.web.service.AgentService;
import site.sorghum.agent4j.web.service.SseEmitter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 聊天 API 控制器 —— 同步聊天 + SSE 流式聊天。
 *
 * @author Sorghum
 */
@Slf4j
@Api(tags = "聊天")
@Controller
@Mapping("/api/chat")
public class ChatController {

    /**
     * SSE 流式聊天线程池 — 隔离流式任务，防止 new Thread() 无限创建导致资源耗尽。
     */
    private final ExecutorService chatExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "agent4j-chat-stream");
        t.setDaemon(true);
        return t;
    });

    @Inject
    private AgentService agentService;

    @ApiOperation(value = "同步聊天", notes = "发送消息并等待完整回复，返回回复内容、耗时和 Token 用量")
    @Post
    @Mapping("")
    public ApiResponse<ChatResultDTO> chat(@ApiParam(value = "聊天请求（含消息、工作区、会话）") @Body ChatRequest request) throws Exception {
        if (!agentService.isReady()) {
            return ApiResponse.fail("Agent 未初始化，请检查 ~/.agent4j/config.json");
        }
        if (request == null || request.message == null || request.message.trim().isEmpty()) {
            return ApiResponse.fail("message 不能为空");
        }
        long t0 = System.currentTimeMillis();
        String workspacePath = agentService.resolveWorkspacePath(request.workspaceHash);
        if (workspacePath == null) workspacePath = agentService.getWorkspace();
        UserMessage userMsg = UserMessage.of(request.message.trim(), request.images);
        String reply = agentService.chat(userMsg, workspacePath, request.sessionName);
        long elapsed = System.currentTimeMillis() - t0;

        ChatResultDTO data = new ChatResultDTO(
                reply, elapsed,
                agentService.getSessionUsageMap(workspacePath, request.sessionName)
        );
        return ApiResponse.ok(data);
    }

    @ApiOperation(value = "中断当前聊天", notes = "发送中断信号给正在进行的聊天会话")
    @Post
    @Mapping("/abort")
    public ApiResponse<String> abort() {
        agentService.abortCurrentChat();
        return ApiResponse.ok("已发送中断请求");
    }

    @ApiOperation(value = "SSE 流式聊天", notes = "通过 Server-Sent Events 流式返回聊天回复，支持实时推送内容片段")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "message", value = "用户消息", required = true),
            @ApiImplicitParam(name = "workspaceHash", value = "工作区 hash"),
            @ApiImplicitParam(name = "sessionName", value = "会话名称")
    })
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
        final UserMessage userMsg = UserMessage.of(request.message.trim(), request.images);
        String workspacePath = agentService.resolveWorkspacePath(request.workspaceHash);
        if (workspacePath == null) workspacePath = agentService.getWorkspace();
        final String resolvedPath = workspacePath;
        final String sessionName = request.sessionName;

        chatExecutor.submit(() -> {
            try {
                agentService.chatStream(userMsg, resolvedPath, sessionName, emitter);
            } catch (Exception e) {
                try {
                    emitter.sendError(e.getMessage());
                } catch (Exception ex) {
                    log.warn("[web] 发送错误信息失败（可能SSE连接已断开）: {}", ex.getMessage());
                }
            }
        });

        // ★ 关键：阻塞 handler 线程直到 SSE 流结束，防止 Solon 提前关闭 OutputStream
        emitter.awaitCompletion();
    }
}
