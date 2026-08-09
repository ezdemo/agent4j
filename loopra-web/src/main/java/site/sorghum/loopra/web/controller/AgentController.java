package site.sorghum.loopra.web.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.ai.talents.mount.SkillDir;
import org.noear.solon.annotation.*;
import site.sorghum.loopra.tool.solon.common.LoopraSkillProvider;
import site.sorghum.loopra.web.common.ServiceException;
import site.sorghum.loopra.web.common.WebErrorMessages;
import site.sorghum.loopra.web.model.*;
import site.sorghum.loopra.web.service.AgentService;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent 状态查询 API。
 *
 * @author Sorghum
 */
@Api(tags = "Agent 控制")
@Controller
@Mapping("/api/agent")
@Slf4j
public class AgentController {

    @Inject
    private AgentService agentService;

    @ApiOperation(value = "获取 Agent 状态", notes = "返回当前 Agent 的运行状态，包括模型、工作区、会话等信息")
    @Get
    @Mapping("/status")
    public ApiResponse<AgentStatusDTO> status() {
        if (!agentService.isReady()) {
            throw new ServiceException("Agent 未初始化，请检查 ~/.loopra/config.json 配置");
        }
        return ApiResponse.ok(agentService.getStatus());
    }

    @ApiOperation(value = "查询会话运行状态", notes = "按工作区和会话精确返回当前后台 Agent 任务是否仍在执行")
    @Get
    @Mapping("/session-status")
    public ApiResponse<SessionStatusDTO> sessionStatus(
            @ApiParam(value = "工作区 hash", required = true)
            @Param(value = "workspaceHash", required = true)
            String workspaceHash,
            @ApiParam(value = "会话名称", required = true)
            @Param(value = "sessionName", required = true)
            String sessionName) {
        if (!agentService.isReady()) {
            throw new ServiceException(WebErrorMessages.AGENT_NOT_READY);
        }
        if (sessionName == null || sessionName.isBlank()) {
            throw new ServiceException("sessionName 不能为空");
        }
        String workspacePath = agentService.resolveWorkspaceHashOrThrow(workspaceHash);
        return ApiResponse.ok(agentService.getSessionStatus(workspacePath, sessionName));
    }

    @ApiOperation(value = "获取历史消息", notes = "根据工作区 hash 和会话名称获取历史消息列表")
    @Get
    @Mapping("/history")
    public ApiResponse<List<?>> history(
            @ApiParam(value = "工作区 hash", required = true)
            @Param(value = "workspaceHash", required = true)
            String workspaceHash,
            @ApiParam(value = "会话名称", required = true)
            @Param(value = "sessionName", required = true)
            String sessionName) {
        if (!agentService.isReady()) {
            throw new ServiceException(WebErrorMessages.AGENT_NOT_READY);
        }
        String workspacePath = agentService.resolveWorkspacePath(workspaceHash);
        return ApiResponse.ok(agentService.getHistory(workspacePath, sessionName));
    }

    @ApiOperation(value = "查询会话计划模式", notes = "返回指定会话是否处于计划模式（前端加载会话时恢复状态用）")
    @Get
    @Mapping("/mode")
    public ApiResponse<java.util.Map<String, Object>> mode(
            @ApiParam(value = "工作区 hash", required = true)
            @Param(value = "workspaceHash", required = true)
            String workspaceHash,
            @ApiParam(value = "会话名称", required = true)
            @Param(value = "sessionName", required = true)
            String sessionName) {
        if (!agentService.isReady()) {
            throw new ServiceException(WebErrorMessages.AGENT_NOT_READY);
        }
        String workspacePath = agentService.resolveWorkspacePath(workspaceHash);
        return ApiResponse.ok(agentService.getPlanState(workspacePath, sessionName));
    }

    @ApiOperation(value = "切换会话计划模式", notes = "由 Web UI 显式开启或关闭计划模式")
    @Post
    @Mapping("/mode")
    public ApiResponse<java.util.Map<String, Object>> updateMode(@Body PlanModeRequest request) {
        if (!agentService.isReady()) {
            throw new ServiceException(WebErrorMessages.AGENT_NOT_READY);
        }
        if (request == null || request.getSessionName() == null || request.getSessionName().isBlank()) {
            throw new ServiceException("请先选择会话");
        }
        String workspacePath = agentService.resolveWorkspacePath(request.getWorkspaceHash());
        return ApiResponse.ok(agentService.setPlanMode(
                workspacePath, request.getSessionName(), request.isEnabled()));
    }

    @ApiOperation(value = "获取可用命令列表", notes = "返回所有可用的聊天命令（如 /help、/retry、/compact 等）")
    @Get
    @Mapping("/commands")
    public ApiResponse<List<CommandMetaDTO>> commands() {
        if (!agentService.isReady()) {
            throw new ServiceException(WebErrorMessages.AGENT_NOT_READY);
        }
        return ApiResponse.ok(agentService.getCommandMetaList());
    }

    @ApiOperation(value = "获取可用 skill 列表", notes = "返回当前已注册的所有 skill")
    @Get
    @Mapping("/skills")
    public ApiResponse<List<SkillMetaDTO>> skills() {
        if (!agentService.isReady()) {
            throw new ServiceException(WebErrorMessages.AGENT_NOT_READY);
        }
        try {
            LoopraSkillProvider skillProvider = LoopraSkillProvider.getOrCreate("~");
            skillProvider.getPoolManager().refresh();
            Collection<SkillDir> skills = skillProvider.getPoolManager().getSkills();

            List<SkillMetaDTO> result = skills.stream()
                    .map(s -> new SkillMetaDTO(
                            s.getName(),
                            s.getDescription(),
                            s.getAliasPath(),
                            "",
                            s.getRealPath().getFileName().toString()
                    ))
                    .collect(Collectors.toList());

            return ApiResponse.ok(result);
        } catch (Exception e) {
            log.warn("获取 skill 列表失败: {}", e.getMessage(), e);
            return ApiResponse.ok(Collections.emptyList());
        }
    }
}
