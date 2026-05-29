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

    /** 获取历史消息 —— GET /api/agent/history?workspaceHash=xxx&sessionName=xxx */
    @Get
    @Mapping("/history")
    public Object history(@Param(value = "workspaceHash", required = false) String workspaceHash,
                          @Param(value = "sessionName", required = false) String sessionName) {
        if (!agentService.isReady()) {
            return ApiResponse.fail("Agent 未初始化");
        }
        String workspacePath = agentService.resolveWorkspacePath(workspaceHash);
        if (workspacePath == null) workspacePath = agentService.getWorkspace();
        return ApiResponse.ok(agentService.getHistory(workspacePath, sessionName));
    }

    /**
     * 获取可用命令列表 —— GET /api/agent/commands
     * <p>
     * 返回前端斜杠命令选择弹窗所需的元数据。
     * </p>
     */
    @Get
    @Mapping("/commands")
    public Object commands() {
        if (!agentService.isReady()) {
            return ApiResponse.fail("Agent 未初始化");
        }
        return ApiResponse.ok(agentService.getCommandMetaList());
    }

    /**
     * 获取可用 skill 列表 —— GET /api/agent/skills
     * <p>
     * 返回前端 skill 选择弹窗所需的元数据。
     * </p>
     */
    @Get
    @Mapping("/skills")
    public Object skills() {
        if (!agentService.isReady()) {
            return ApiResponse.fail("Agent 未初始化");
        }
        return ApiResponse.ok(agentService.getSkillMetaList());
    }
}
