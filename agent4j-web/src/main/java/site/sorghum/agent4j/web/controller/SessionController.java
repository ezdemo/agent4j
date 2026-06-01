package site.sorghum.agent4j.web.controller;

import lombok.SneakyThrows;
import org.noear.solon.annotation.*;
import org.noear.solon.core.handle.Context;

import site.sorghum.agent4j.tool.interact.InteractionService;
import site.sorghum.agent4j.web.common.ServiceException;
import site.sorghum.agent4j.web.model.*;
import site.sorghum.agent4j.web.service.AgentService;

import java.util.List;

/**
 * 会话管理 API 控制器。
 *
 * @author Sorghum
 */
@Controller
@Mapping("/api/sessions")
public class SessionController {

    @Inject
    private AgentService agentService;

    @Inject
    private InteractionService interactionService;

    /** 列出所有会话 —— GET /api/sessions?workspaceHash=xxx */
    @Get
    @Mapping("")
    public ApiResponse<List<SessionInfoDTO>> list(@Param(value = "workspaceHash", required = false) String workspaceHash) throws Exception {
        if (!agentService.isReady()) throw new ServiceException("Agent 未初始化");
        String workspacePath = agentService.resolveWorkspacePath(workspaceHash);
        if (workspacePath == null) workspacePath = agentService.getWorkspace();
        return ApiResponse.ok(agentService.listSessions(workspacePath));
    }

    /** 获取当前会话信息 —— GET /api/sessions/current?workspaceHash=xxx */
    @Get
    @Mapping("/current")
    public ApiResponse<SessionCurrentDTO> current(@Param(value = "workspaceHash", required = false) String workspaceHash) {
        if (!agentService.isReady()) throw new ServiceException("Agent 未初始化");
        String workspacePath = agentService.resolveWorkspacePath(workspaceHash);
        if (workspacePath == null) workspacePath = agentService.getWorkspace();
        String currentName = agentService.getCurrentSessionName(workspacePath);
        String resolvedHash = workspaceHash != null ? workspaceHash : AgentService.computeWorkspaceHash(workspacePath);
        return ApiResponse.ok(new SessionCurrentDTO(resolvedHash, currentName));
    }

    /** 新建空白会话 —— POST /api/sessions/new?workspaceHash=xxx&sessionName=xxx */
    @Post
    @Mapping("/new")
    public ApiResponse<SessionCreateDTO> createNew(@Param(value = "workspaceHash", required = false) String workspaceHash,
                            @Param(value = "sessionName", required = false) String sessionName) {
        if (!agentService.isReady()) throw new ServiceException("Agent 未初始化");
        String workspacePath = agentService.resolveWorkspacePath(workspaceHash);
        if (workspacePath == null) workspacePath = agentService.getWorkspace();
        String actualName = agentService.newSession(workspacePath, sessionName);
        String resolvedHash = workspaceHash != null ? workspaceHash : AgentService.computeWorkspaceHash(workspacePath);
        return ApiResponse.ok(new SessionCreateDTO("已创建新会话", resolvedHash, actualName));
    }

    /** 切换会话 —— POST /api/sessions/{name}?workspaceHash=xxx */
    @SneakyThrows
    @Post
    @Mapping("/{name}")
    public ApiResponse<SessionSwitchDTO> switchSession(@Path("name") String name,
                                @Param(value = "workspaceHash", required = false) String workspaceHash) {
        if (!agentService.isReady()) throw new ServiceException("Agent 未初始化");
        String workspacePath = agentService.resolveWorkspacePath(workspaceHash);
        if (workspacePath == null) workspacePath = agentService.getWorkspace();
        boolean ok = agentService.switchSession(workspacePath, name);
        if (ok) {
            String confirmedName = agentService.getCurrentSessionName(workspacePath);
            String resolvedHash = workspaceHash != null ? workspaceHash : AgentService.computeWorkspaceHash(workspacePath);
            return ApiResponse.ok(new SessionSwitchDTO(resolvedHash, confirmedName != null ? confirmedName : name, true));
        }
        throw new ServiceException("会话不存在: " + name);
    }

    /** 删除会话 —— DELETE /api/sessions/{name}?workspaceHash=xxx */
    @Delete
    @Mapping("/{name}")
    public ApiResponse<SessionDeleteDTO> deleteSession(@Path("name") String name,
                                @Param(value = "workspaceHash", required = false) String workspaceHash) {
        if (!agentService.isReady()) throw new ServiceException("Agent 未初始化");
        String workspacePath = agentService.resolveWorkspacePath(workspaceHash);
        if (workspacePath == null) workspacePath = agentService.getWorkspace();
        agentService.deleteSession(workspacePath, name);
        String resolvedHash = workspaceHash != null ? workspaceHash : AgentService.computeWorkspaceHash(workspacePath);
        return ApiResponse.ok(new SessionDeleteDTO("会话已删除", resolvedHash, name));
    }

    /** 清除所有 Agent 缓存 —— POST /api/sessions/evict-all */
    @Post
    @Mapping("/evict-all")
    public ApiResponse<String> evictAll() {
        if (!agentService.isReady()) throw new ServiceException("Agent 未初始化");
        agentService.evictAllAgents();
        return ApiResponse.ok("已清除所有 Agent 缓存");
    }

    /** 获取缓存统计 —— GET /api/sessions/stats */
    @Get
    @Mapping("/stats")
    public ApiResponse<SessionStatsDTO> stats() {
        if (!agentService.isReady()) throw new ServiceException("Agent 未初始化");
        return ApiResponse.ok(new SessionStatsDTO(agentService.getCacheSize(), 50));
    }

    /** 获取指定会话的 TODO 列表 —— GET /api/sessions/{name}/todos?workspaceHash=xxx */
    @Get
    @Mapping("/{name}/todos")
    public ApiResponse<List<?>> getTodos(@Path("name") String sessionName,
                           @Param(value = "workspaceHash", required = false) String workspaceHash) {
        if (!agentService.isReady()) throw new ServiceException("Agent 未初始化");
        // InteractionService.getTodos() 返回 List<Map>，这里直接透传
        // TODO: 后续将 InteractionService 也改为返回 DTO
        List<?> todos = interactionService.getTodos(sessionName);
        return ApiResponse.ok(todos);
    }
}
