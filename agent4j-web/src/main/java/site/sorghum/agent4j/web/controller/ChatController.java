package site.sorghum.agent4j.web.controller;

import io.swagger.annotations.*;
import lombok.extern.slf4j.Slf4j;
import org.noear.snack4.ONode;
import org.noear.solon.annotation.*;
import org.noear.solon.core.handle.Context;
import site.sorghum.agent4j.bin.agent.UserMessage;
import site.sorghum.agent4j.web.common.WebErrorMessages;
import site.sorghum.agent4j.web.model.ApiResponse;
import site.sorghum.agent4j.web.model.ChatRequest;
import site.sorghum.agent4j.web.model.ChatResultDTO;
import site.sorghum.agent4j.web.service.AgentService;
import site.sorghum.agent4j.web.service.SseEmitter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

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

    /** 流式聊天并发上限 */
    private static final int CHAT_STREAM_MAX_THREADS = 16;

    /**
     * SSE 流式聊天线程池 — 有界固定大小，避免无限制线程创建导致资源耗尽。
     */
    private final ExecutorService chatExecutor = Executors.newFixedThreadPool(
            CHAT_STREAM_MAX_THREADS, new ChatStreamThreadFactory());

    @Inject
    private AgentService agentService;

    @ApiOperation(value = "同步聊天", notes = "发送消息并等待完整回复，返回回复内容、耗时和 Token 用量")
    @Post
    @Mapping("")
    public ApiResponse<ChatResultDTO> chat(
            @ApiParam(value = "聊天请求（含消息、工作区、会话）") @Body ChatRequest request) throws Exception {
        if (!agentService.isReady()) {
            return ApiResponse.fail("Agent 未初始化，请检查 ~/.agent4j/config.json");
        }
        if (request == null || request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            return ApiResponse.fail("message 不能为空");
        }
        long t0 = System.currentTimeMillis();
        String workspacePath = agentService.resolveWorkspacePath(request.getWorkspaceHash());
        if (workspacePath == null) workspacePath = agentService.getWorkspace();
        UserMessage userMsg = UserMessage.of(request.getMessage().trim(), request.getImages());
        String reply = agentService.chat(userMsg, workspacePath, request.getSessionName());
        long elapsed = System.currentTimeMillis() - t0;

        ChatResultDTO data = new ChatResultDTO(
                reply, elapsed,
                agentService.getSessionUsageMap(workspacePath, request.getSessionName())
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
            ctx.outputAsJson(jsonError(WebErrorMessages.AGENT_NOT_READY));
            return;
        }
        if (request == null || request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            ctx.outputAsJson(jsonError(WebErrorMessages.MESSAGE_REQUIRED));
            return;
        }

        SseEmitter emitter = new SseEmitter(ctx);
        final UserMessage userMsg = UserMessage.of(request.getMessage().trim(), request.getImages());
        String workspacePath = agentService.resolveWorkspacePath(request.getWorkspaceHash());
        if (workspacePath == null) workspacePath = agentService.getWorkspace();
        final String resolvedPath = workspacePath;
        final String sessionName = request.getSessionName();

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

    /** 构建 JSON 错误响应字符串。 */
    private static String jsonError(String message) {
        return ONode.ofJson("{}").asObject().set("success", false).set("error", message).toJson();
    }

    /** 流式聊天线程命名工厂。 */
    private static final class ChatStreamThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            final Thread t = new Thread(r, "agent4j-chat-stream-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }
}
