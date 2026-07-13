package site.sorghum.agent4j.web.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.SneakyThrows;
import org.noear.solon.annotation.*;
import site.sorghum.agent4j.bin.workflow2.*;
import site.sorghum.agent4j.bin.workspace.WorkspaceManager;
import site.sorghum.agent4j.tool.interact.InteractionService;
import site.sorghum.agent4j.web.common.ServiceException;
import site.sorghum.agent4j.web.common.WebErrorMessages;
import site.sorghum.agent4j.web.model.*;
import site.sorghum.agent4j.web.service.AgentService;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 会话管理 API 控制器。
 *
 * @author Sorghum
 */
@Api(tags = "会话管理")
@Controller
@Mapping("/api/sessions")
public class SessionController {

    @Inject
    private AgentService agentService;

    @Inject
    private InteractionService interactionService;

    /** 缓存统计默认上限 */
    private static final int DEFAULT_CACHE_LIMIT = 50;
    @Get
    @Mapping("")
    public ApiResponse<List<SessionInfoDTO>> list(
            @ApiParam(value = "工作区 hash")
            @Param(value = "workspaceHash", required = false) String workspaceHash) throws Exception {
        if (!agentService.isReady()) throw new ServiceException(WebErrorMessages.AGENT_NOT_READY);
        String workspacePath = agentService.resolveWorkspacePath(workspaceHash);
        if (workspacePath == null) workspacePath = agentService.getWorkspace();
        return ApiResponse.ok(agentService.listSessions(workspacePath));
    }

    @ApiOperation(value = "获取当前会话", notes = "返回当前活跃的会话信息（工作区 hash + 会话名）")
    @Get
    @Mapping("/current")
    public ApiResponse<SessionCurrentDTO> current(
            @ApiParam(value = "工作区 hash") @Param(value = "workspaceHash", required = false) String workspaceHash) {
        if (!agentService.isReady()) throw new ServiceException(WebErrorMessages.AGENT_NOT_READY);
        String workspacePath = agentService.resolveWorkspacePath(workspaceHash);
        if (workspacePath == null) workspacePath = agentService.getWorkspace();
        String currentName = agentService.getCurrentSessionName(workspacePath);
        String resolvedHash = workspaceHash != null ? workspaceHash : AgentService.computeWorkspaceHash(workspacePath);
        return ApiResponse.ok(new SessionCurrentDTO(resolvedHash, currentName));
    }

    @ApiOperation(value = "新建空白会话", notes = "在指定工作区下创建一个新的空白会话")
    @Post
    @Mapping("/new")
    public ApiResponse<SessionCreateDTO> createNew(
            @ApiParam(value = "工作区 hash", required = true) @Param(value = "workspaceHash", required = true) String workspaceHash,
            @ApiParam(value = "会话名称（可选，自动生成）")
            @Param(value = "sessionName", required = false) String sessionName) {
        if (!agentService.isReady()) throw new ServiceException(WebErrorMessages.AGENT_NOT_READY);
        String workspacePath = agentService.resolveWorkspaceHashOrThrow(workspaceHash);
        String actualName = agentService.newSession(workspacePath, sessionName);
        return ApiResponse.ok(new SessionCreateDTO("已创建新会话", workspaceHash, actualName));
    }

    @ApiOperation(value = "切换会话", notes = "切换到指定工作区下的指定会话")
    @SneakyThrows
    @Post
    @Mapping("/{name}")
    public ApiResponse<SessionSwitchDTO> switchSession(
            @ApiParam(value = "会话名称") @Path("name") String name,
            @ApiParam(value = "工作区 hash", required = true) @Param(value = "workspaceHash", required = true) String workspaceHash) {
        if (!agentService.isReady()) throw new ServiceException(WebErrorMessages.AGENT_NOT_READY);
        String workspacePath = agentService.resolveWorkspaceHashOrThrow(workspaceHash);
        boolean ok = agentService.switchSession(workspacePath, name);
        if (ok) {
            String confirmedName = agentService.getCurrentSessionName(workspacePath);
            return ApiResponse.ok(
                    new SessionSwitchDTO(workspaceHash, confirmedName != null ? confirmedName : name, true));
        }
        throw new ServiceException("会话不存在: " + name);
    }

    @ApiOperation(value = "清空所有会话", notes = "清除指定工作区下的所有会话磁盘文件和缓存")
    @Delete
    @Mapping("")
    public ApiResponse<SessionDeleteDTO> clearAllSessions(
            @ApiParam(value = "工作区 hash", required = true)
            @Param(value = "workspaceHash", required = true) String workspaceHash) {
        if (!agentService.isReady()) throw new ServiceException(WebErrorMessages.AGENT_NOT_READY);
        String workspacePath = agentService.resolveWorkspaceHashOrThrow(workspaceHash);
        agentService.clearAllSessions(workspacePath);
        String resolvedHash = AgentService.computeWorkspaceHash(workspacePath);
        return ApiResponse.ok(new SessionDeleteDTO("所有会话已清空", resolvedHash, null));
    }

    @ApiOperation(value = "删除会话", notes = "根据会话名称和工作区删除指定会话")
    @Delete
    @Mapping("/{name}")
    public ApiResponse<SessionDeleteDTO> deleteSession(
            @ApiParam(value = "会话名称") @Path("name") String name,
            @ApiParam(value = "工作区 hash") @Param(value = "workspaceHash") String workspaceHash) {
        if (!agentService.isReady()) throw new ServiceException(WebErrorMessages.AGENT_NOT_READY);
        String workspacePath = agentService.resolveWorkspacePath(workspaceHash);
        agentService.deleteSession(workspacePath, name);
        String resolvedHash = workspaceHash != null ? workspaceHash : AgentService.computeWorkspaceHash(workspacePath);
        return ApiResponse.ok(new SessionDeleteDTO("会话已删除", resolvedHash, name));
    }

    @ApiOperation(value = "清除 Agent 缓存", notes = "清除所有会话的 Agent 缓存，强制重新初始化")
    @Post
    @Mapping("/evict-all")
    public ApiResponse<String> evictAll() {
        if (!agentService.isReady()) throw new ServiceException(WebErrorMessages.AGENT_NOT_READY);
        agentService.evictAllAgents();
        return ApiResponse.ok("已清除所有 Agent 缓存");
    }

    @ApiOperation(value = "分支会话", notes = "复制原始历史中从开头到指定排他结束位置的消息到新会话，并切换过去")
    @Post
    @Mapping("/{name}/branch")
    public ApiResponse<SessionCreateDTO> branchSession(
            @ApiParam(value = "源会话名称") @Path("name") String name,
            @ApiParam(value = "工作区 hash", required = true) @Param(value = "workspaceHash", required = true) String workspaceHash,
            @ApiParam(value = "原始历史的排他结束位置", required = true) @Param(value = "messageCount", required = true) Integer messageCount) throws Exception {
        if (!agentService.isReady()) throw new ServiceException(WebErrorMessages.AGENT_NOT_READY);
        if (messageCount == null || messageCount <= 0) throw new ServiceException("messageCount 必须大于 0");
        String workspacePath = agentService.resolveWorkspaceHashOrThrow(workspaceHash);
        String newName = agentService.branchSession(workspacePath, name, messageCount);
        return ApiResponse.ok(new SessionCreateDTO("已分支到新会话", workspaceHash, newName));
    }

    @ApiOperation(value = "获取缓存统计", notes = "返回当前 Agent 缓存的数量和上限")
    @Get
    @Mapping("/stats")
    public ApiResponse<SessionStatsDTO> stats() {
        if (!agentService.isReady()) throw new ServiceException("Agent 未初始化");
        return ApiResponse.ok(new SessionStatsDTO(agentService.getCacheSize(), DEFAULT_CACHE_LIMIT));
    }

    @ApiOperation(value = "获取会话工作流", notes = "返回指定会话的步骤列表工作流状态")
    @Get
    @Mapping("/{name}/workflow")
    public ApiResponse<WorkflowStatusDTO> getWorkflow(
            @ApiParam(value = "会话名称") @Path("name") String sessionName,
            @ApiParam(value = "工作区 hash", required = true) @Param(value = "workspaceHash", required = true) String workspaceHash) {
        if (!agentService.isReady()) throw new ServiceException(WebErrorMessages.AGENT_NOT_READY);
        String workspacePath = agentService.resolveWorkspaceHashOrThrow(workspaceHash);
        
        WorkspaceManager workspaceManager = WorkspaceManager.getOrCreate(workspacePath);
        SimpleWorkflowStore store = workspaceManager.getWorkflowStore2();
        
        try {
            SimpleWorkflow wf = store.findBySession(sessionName);
            if (wf == null) {
                return ApiResponse.ok(null);
            }
            return ApiResponse.ok(WorkflowStatusDTO.builder()
                    .workflowId(wf.getId())
                    .title(wf.getTitle())
                    .status(wf.getStatus())
                    .currentStepIndex(wf.getCurrentStepIndex())
                    .totalSteps(wf.getSteps().size())
                    .progress(wf.progressText())
                    .steps(wf.getSteps().stream().map(s -> WorkflowStatusDTO.StepDTO.builder()
                            .id(s.getId())
                            .description(s.getDescription())
                            .kind(s.getKind().name())
                            .status(s.getStatus().name())
                            .result(s.getResult())
                            .build()).collect(java.util.stream.Collectors.toList()))
                    .build());
        } catch (Exception e) {
            throw new ServiceException("获取工作流失败: " + e.getMessage());
        }
    }
    

}
