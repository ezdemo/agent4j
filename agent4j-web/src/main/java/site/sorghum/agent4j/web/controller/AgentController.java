package site.sorghum.agent4j.web.controller;

import org.noear.solon.annotation.*;

import site.sorghum.agent4j.web.model.ApiResponse;
import site.sorghum.agent4j.web.service.AgentService;

import java.util.Map;

/**
 * Agent 状态查询 API。
 * <p>
 * 注意：retry/rewind/compact/plan/hitl 等命令操作不再在此处冗余实现。
 * 这些逻辑已由 {@link site.sorghum.agent4j.bin.agent.Agent4jAgent#chat(String)}
 * 通过 {@link site.sorghum.agent4j.bin.command.ChatCommandRegistry} 统一处理。
 * 前端直接发送命令字符串（如 "/retry"、"/compact"）到 {@code /api/chat} 即可。
 * </p>
 *
 * @author Sorghum
 */
@Controller
@Mapping("/api/agent")
public class AgentController {

    @Inject
    private AgentService agentService;

    /** 获取 Agent 状态 —— GET /api/agent/status */
    @Get
    @Mapping("/status")
    public Object status() {
        if (!agentService.isReady()) {
            return ApiResponse.fail("Agent 未初始化，请检查 ~/.agent4j/config.json 配置");
        }
        return ApiResponse.ok(agentService.getStatus());
    }

    /** 获取历史消息 —— GET /api/agent/history */
    @Get
    @Mapping("/history")
    public Object history() {
        if (!agentService.isReady()) {
            return ApiResponse.fail("Agent 未初始化");
        }
        return ApiResponse.ok(agentService.getHistory());
    }
}
