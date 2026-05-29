package site.sorghum.agent4j.web.controller;

import org.noear.solon.annotation.*;

import site.sorghum.agent4j.bin.config.Agent4jConfig;
import site.sorghum.agent4j.web.model.ApiResponse;
import site.sorghum.agent4j.web.service.AgentService;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 配置与用量 API 控制器。
 *
 * @author Sorghum
 */
@Controller
@Mapping("/api")
public class ConfigController {

    @Inject
    private AgentService agentService;

    /** 获取当前配置（apiKey 脱敏） —— GET /api/config */
    @Get
    @Mapping("/config")
    public Object getConfig() {
        try {
            Agent4jConfig config = Agent4jConfig.load();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("baseUrl", config.baseUrl());
            data.put("model", config.model());
            data.put("availableModels", config.availableModels());
            // 优先返回当前运行时的工作目录（可能已被动态切换）
            if (agentService.isReady()) {
                data.put("workspace", agentService.getWorkspace());
            } else {
                data.put("workspace", config.workspaceDir() != null
                        ? config.workspaceDir().toString() : null);
            }
            data.put("editMode", config.editMode());
            data.put("reasoningEffort", config.reasoningEffort());
            data.put("lang", config.lang());
            data.put("hitl", config.hitl());
            data.put("disabledTools", config.disabledTools());
            data.put("blockedPaths", config.blockedPaths());
            String apiKey = config.apiKey();
            if (apiKey != null && apiKey.length() > 8) {
                data.put("apiKey", apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4));
            } else {
                data.put("apiKey", "****");
            }
            return ApiResponse.ok(data);
        } catch (Exception e) {
            return ApiResponse.fail("读取配置失败: " + e.getMessage());
        }
    }

    /** 更新配置（合并不为空的字段） —— PUT /api/config */
    @Put
    @Mapping("/config")
    public Object updateConfig(@Body Map<String, Object> body) {
        try {
            Agent4jConfig config = Agent4jConfig.load();
            config.updateAndSave(body);
            
            // 如果更新了模型，需要通知 AgentService
            if (body.containsKey("model") && agentService.isReady()) {
                String newModel = body.get("model").toString();
                agentService.updateModel(newModel);
            }
            
            return ApiResponse.ok("配置已更新");
        } catch (Exception e) {
            return ApiResponse.fail("更新配置失败: " + e.getMessage());
        }
    }

    /** 获取可用模型列表 —— GET /api/models */
    @Get
    @Mapping("/models")
    public Object getModels() {
        try {
            Agent4jConfig config = Agent4jConfig.load();
            String currentModel = config.model();
            List<String> available = config.availableModels();
            
            // 合并 currentModel 到列表（去重）
            Set<String> modelSet = new LinkedHashSet<>();
            modelSet.add(currentModel);
            modelSet.addAll(available);
            
            List<Map<String, Object>> models = modelSet.stream()
                .map(m -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", m);
                    item.put("active", m.equals(currentModel));
                    return item;
                })
                .collect(Collectors.toList());
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("current", currentModel);
            result.put("models", models);
            return ApiResponse.ok(result);
        } catch (Exception e) {
            return ApiResponse.fail("获取模型列表失败: " + e.getMessage());
        }
    }

    /** 获取 Token 用量统计 —— GET /api/usage?workspaceHash=xxx&sessionName=xxx */
    @Get
    @Mapping("/usage")
    public Object getUsage(@Param(value = "workspaceHash", required = false) String workspaceHash,
                           @Param(value = "sessionName",required = false) String sessionName) {
        if (!agentService.isReady()) return ApiResponse.fail("Agent 未初始化");
        String workspacePath = agentService.resolveWorkspacePath(workspaceHash);
        if (workspacePath == null) workspacePath = agentService.getWorkspace();
        return ApiResponse.ok(agentService.getSessionUsageMap(workspacePath, sessionName));
    }

    /** 获取当前工作目录 —— GET /api/workspace */
    @Get
    @Mapping("/workspace")
    public Object getWorkspace() {
        if (!agentService.isReady()) return ApiResponse.fail("Agent 未初始化");
        return ApiResponse.ok(agentService.getWorkspace());
    }

    /** 切换工作目录 —— POST /api/workspace */
    @Post
    @Mapping("/workspace")
    public Object switchWorkspace(@Body Map<String, String> body) {
        if (!agentService.isReady()) return ApiResponse.fail("Agent 未初始化");
        String path = body.get("path");
        if (path == null || path.isEmpty()) {
            return ApiResponse.fail("路径不能为空");
        }
        boolean ok = agentService.switchWorkspace(path);
        if (ok) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("message", "工作目录已切换");
            data.put("workspace", agentService.getWorkspace());
            return ApiResponse.ok(data);
        }
        return ApiResponse.fail("无效的工作目录路径: " + path);
    }

    // ==================== 工作区管理 ====================

    /** 获取所有工作区列表 —— GET /api/workspaces */
    @Get
    @Mapping("/workspaces")
    public Object listWorkspaces() {
        if (!agentService.isReady()) return ApiResponse.fail("Agent 未初始化");
        List<Map<String, Object>> workspaces = agentService.listWorkspaces();
        return ApiResponse.ok(workspaces);
    }

    /** 切换到指定工作区 —— POST /api/workspaces/switch */
    @Post
    @Mapping("/workspaces/switch")
    public Object switchToWorkspace(@Body Map<String, String> body) {
        if (!agentService.isReady()) return ApiResponse.fail("Agent 未初始化");
        String hash = body.get("hash");
        if (hash == null || hash.isEmpty()) {
            return ApiResponse.fail("工作区 hash 不能为空");
        }
        boolean ok = agentService.switchToWorkspaceByHash(hash);
        if (ok) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("message", "工作区已切换");
            data.put("workspace", agentService.getWorkspace());
            data.put("currentSession", agentService.getCurrentSession());
            return ApiResponse.ok(data);
        }
        return ApiResponse.fail("切换工作区失败: " + hash);
    }

    /** 删除工作区 —— DELETE /api/workspaces/{hash} */
    @Delete
    @Mapping("/workspaces/{hash}")
    public Object deleteWorkspace(@Param("hash") String hash) {
        if (!agentService.isReady()) return ApiResponse.fail("Agent 未初始化");
        if (hash == null || hash.isEmpty()) {
            return ApiResponse.fail("工作区 hash 不能为空");
        }
        boolean ok = agentService.deleteWorkspace(hash);
        if (ok) {
            return ApiResponse.ok("工作区已删除");
        }
        return ApiResponse.fail("删除工作区失败");
    }
}
