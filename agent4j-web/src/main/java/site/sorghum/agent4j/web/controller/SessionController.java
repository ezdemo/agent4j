package site.sorghum.agent4j.web.controller;

import lombok.SneakyThrows;
import org.noear.solon.annotation.*;
import org.noear.solon.core.handle.Context;

import site.sorghum.agent4j.tool.interact.InteractionService;
import site.sorghum.agent4j.web.common.ServiceException;
import site.sorghum.agent4j.web.model.ApiResponse;
import site.sorghum.agent4j.web.service.AgentService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    public Object list(@Param(value = "workspaceHash", required = false) String workspaceHash) throws Exception {
        if (!agentService.isReady()) throw new ServiceException("Agent 未初始化");
        String workspacePath = agentService.resolveWorkspacePath(workspaceHash);
        if (workspacePath == null) workspacePath = agentService.getWorkspace();
        return ApiResponse.ok(agentService.listSessions(workspacePath));
    }

    /** 获取当前会话信息 —— GET /api/sessions/current?workspaceHash=xxx */
    @Get
    @Mapping("/current")
    public Object current(@Param(value = "workspaceHash", required = false) String workspaceHash) {
        if (!agentService.isReady()) throw new ServiceException("Agent 未初始化");
        String workspacePath = agentService.resolveWorkspacePath(workspaceHash);
        if (workspacePath == null) workspacePath = agentService.getWorkspace();
        String currentName = agentService.getCurrentSessionName(workspacePath);
        String resolvedHash = workspaceHash != null ? workspaceHash : AgentService.computeWorkspaceHash(workspacePath);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("workspaceHash", resolvedHash);
        data.put("sessionName", currentName);
        return ApiResponse.ok(data);
    }

    /** 新建空白会话 —— POST /api/sessions/new?workspaceHash=xxx&sessionName=xxx */
    @Post
    @Mapping("/new")
    public Object createNew(@Param(value = "workspaceHash", required = false) String workspaceHash,
                            @Param(value = "sessionName", required = false) String sessionName) {
        if (!agentService.isReady()) throw new ServiceException("Agent 未初始化");
        String workspacePath = agentService.resolveWorkspacePath(workspaceHash);
        if (workspacePath == null) workspacePath = agentService.getWorkspace();
        String actualName = agentService.newSession(workspacePath, sessionName);
        String resolvedHash = workspaceHash != null ? workspaceHash : AgentService.computeWorkspaceHash(workspacePath);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", "已创建新会话");
        data.put("workspaceHash", resolvedHash);
        data.put("sessionName", actualName);
        return ApiResponse.ok(data);
    }

    /** 切换会话 —— POST /api/sessions/{name}?workspaceHash=xxx */
    @SneakyThrows
    @Post
    @Mapping("/{name}")
    public Object switchSession(@Path("name") String name,
                                @Param(value = "workspaceHash", required = false) String workspaceHash) {
        if (!agentService.isReady()) throw new ServiceException("Agent 未初始化");
        String workspacePath = agentService.resolveWorkspacePath(workspaceHash);
        if (workspacePath == null) workspacePath = agentService.getWorkspace();
        boolean ok = agentService.switchSession(workspacePath, name);
        if (ok) {
            String confirmedName = agentService.getCurrentSessionName(workspacePath);
            String resolvedHash = workspaceHash != null ? workspaceHash : AgentService.computeWorkspaceHash(workspacePath);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("workspaceHash", resolvedHash);
            data.put("sessionName", confirmedName != null ? confirmedName : name);
            data.put("switched", true);
            return ApiResponse.ok(data);
        }
        throw new ServiceException("会话不存在: " + name);
    }

    /** 删除会话 —— DELETE /api/sessions/{name}?workspaceHash=xxx */
    @Delete
    @Mapping("/{name}")
    public Object deleteSession(@Path("name") String name,
                                @Param(value = "workspaceHash", required = false) String workspaceHash) {
        if (!agentService.isReady()) throw new ServiceException("Agent 未初始化");
        String workspacePath = agentService.resolveWorkspacePath(workspaceHash);
        if (workspacePath == null) workspacePath = agentService.getWorkspace();
        agentService.deleteSession(workspacePath, name);
        String resolvedHash = workspaceHash != null ? workspaceHash : AgentService.computeWorkspaceHash(workspacePath);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", "会话已删除");
        data.put("workspaceHash", resolvedHash);
        data.put("sessionName", name);
        return ApiResponse.ok(data);
    }

    /** 清除所有 Agent 缓存 —— POST /api/sessions/evict-all */
    @Post
    @Mapping("/evict-all")
    public Object evictAll() {
        if (!agentService.isReady()) throw new ServiceException("Agent 未初始化");
        agentService.evictAllAgents();
        return ApiResponse.ok("已清除所有 Agent 缓存");
    }

    /** 获取缓存统计 —— GET /api/sessions/stats */
    @Get
    @Mapping("/stats")
    public Object stats() {
        if (!agentService.isReady()) throw new ServiceException("Agent 未初始化");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cacheSize", agentService.getCacheSize());
        data.put("maxCacheSize", 50);
        return ApiResponse.ok(data);
    }

    /** 获取指定会话的 TODO 列表 —— GET /api/sessions/{name}/todos?workspaceHash=xxx */
    @Get
    @Mapping("/{name}/todos")
    public Object getTodos(@Path("name") String sessionName,
                           @Param(value = "workspaceHash", required = false) String workspaceHash) {
        if (!agentService.isReady()) throw new ServiceException("Agent 未初始化");
        List<Map<String, Object>> todos = interactionService.getTodos(sessionName);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionName", sessionName);
        data.put("todos", todos);
        data.put("count", todos.size());
        return ApiResponse.ok(data);
    }
}
