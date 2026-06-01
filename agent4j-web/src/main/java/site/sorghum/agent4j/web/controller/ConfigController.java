package site.sorghum.agent4j.web.controller;

import lombok.SneakyThrows;
import org.noear.solon.annotation.*;

import site.sorghum.agent4j.bin.config.Agent4jConfig;
import site.sorghum.agent4j.web.common.ServiceException;
import site.sorghum.agent4j.web.model.*;
import site.sorghum.agent4j.web.service.AgentService;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    @SneakyThrows
    @Get
    @Mapping("/config")
    public Object getConfig() {
        Agent4jConfig config = Agent4jConfig.load();
        String workspace = null;
        if (agentService.isReady()) {
            workspace = agentService.getWorkspace();
        } else if (config.workspaceDir() != null) {
            workspace = config.workspaceDir().toString();
        }

        String apiKey = config.apiKey();
        String maskedKey;
        if (apiKey != null && apiKey.length() > 8) {
            maskedKey = apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
        } else {
            maskedKey = "****";
        }

        ConfigDTO data = new ConfigDTO(
                config.baseUrl(),
                config.model(),
                config.availableModels(),
                workspace,
                config.editMode(),
                config.reasoningEffort(),
                config.lang(),
                config.hitl(),
                config.disabledTools(),
                config.blockedPaths(),
                maskedKey
        );
        return ApiResponse.ok(data);
    }

    /** 更新配置（合并不为空的字段） —— PUT /api/config */
    @SneakyThrows
    @Put
    @Mapping("/config")
    public Object updateConfig(@Body Map<String, Object> body) {
        Agent4jConfig config = Agent4jConfig.load();
        config.updateAndSave(body);

        if (body.containsKey("model") && agentService.isReady()) {
            String newModel = body.get("model").toString();
            agentService.updateModel(newModel);
        }

        if (body.containsKey("hitl") && agentService.isReady()) {
            Object hitlVal = body.get("hitl");
            boolean newHitl = hitlVal instanceof Boolean ? (Boolean) hitlVal : Boolean.parseBoolean(hitlVal.toString());
            agentService.updateHitlMode(newHitl);
        }

        return ApiResponse.ok("配置已更新");
    }

    /** 获取可用模型列表 —— GET /api/models */
    @SneakyThrows
    @Get
    @Mapping("/models")
    public Object getModels() {
        Agent4jConfig config = Agent4jConfig.load();
        String currentModel = config.model();
        List<String> available = config.availableModels();

        Set<String> modelSet = new LinkedHashSet<>();
        modelSet.add(currentModel);
        modelSet.addAll(available);

        List<ModelInfoDTO> models = modelSet.stream()
            .map(m -> new ModelInfoDTO(m, m.equals(currentModel)))
            .collect(Collectors.toList());

        return ApiResponse.ok(new ModelListDTO(currentModel, models));
    }

    /** 获取 Token 用量统计 —— GET /api/usage?workspaceHash=xxx&sessionName=xxx */
    @Get
    @Mapping("/usage")
    public Object getUsage(@Param(value = "workspaceHash", required = false) String workspaceHash,
                           @Param(value = "sessionName", required = false) String sessionName) {
        if (!agentService.isReady()) throw new ServiceException("Agent 未初始化");
        String workspacePath = agentService.resolveWorkspacePath(workspaceHash);
        if (workspacePath == null) workspacePath = agentService.getWorkspace();
        return ApiResponse.ok(agentService.getSessionUsageMap(workspacePath, sessionName));
    }

    /** 获取当前工作目录 —— GET /api/workspace */
    @Get
    @Mapping("/workspace")
    public Object getWorkspace() {
        if (!agentService.isReady()) throw new ServiceException("Agent 未初始化");
        return ApiResponse.ok(agentService.getWorkspace());
    }

    /** 切换工作目录 —— POST /api/workspace */
    @Post
    @Mapping("/workspace")
    public Object switchWorkspace(@Body Map<String, String> body) {
        if (!agentService.isReady()) throw new ServiceException("Agent 未初始化");
        String path = body.get("path");
        if (path == null || path.isEmpty()) {
            throw new ServiceException("路径不能为空");
        }
        boolean ok = agentService.switchWorkspace(path);
        if (ok) {
            WorkspaceSwitchDTO data = new WorkspaceSwitchDTO(
                    "工作目录已切换", agentService.getWorkspace(), null);
            return ApiResponse.ok(data);
        }
        throw new ServiceException("无效的工作目录路径: " + path);
    }

    // ==================== 工作区管理 ====================

    /** 获取所有工作区列表 —— GET /api/workspaces */
    @Get
    @Mapping("/workspaces")
    public Object listWorkspaces() {
        if (!agentService.isReady()) throw new ServiceException("Agent 未初始化");
        return ApiResponse.ok(agentService.listWorkspaces());
    }

    /** 切换到指定工作区 —— POST /api/workspaces/switch */
    @Post
    @Mapping("/workspaces/switch")
    public Object switchToWorkspace(@Body Map<String, String> body) {
        if (!agentService.isReady()) throw new ServiceException("Agent 未初始化");
        String hash = body.get("hash");
        if (hash == null || hash.isEmpty()) {
            throw new ServiceException("工作区 hash 不能为空");
        }
        boolean ok = agentService.switchToWorkspaceByHash(hash);
        if (ok) {
            WorkspaceSwitchDTO data = new WorkspaceSwitchDTO(
                    "工作区已切换", agentService.getWorkspace(), agentService.getCurrentSession());
            return ApiResponse.ok(data);
        }
        throw new ServiceException("切换工作区失败: " + hash);
    }

    /** 删除工作区 —— DELETE /api/workspaces/{hash} */
    @Delete
    @Mapping("/workspaces/{hash}")
    public Object deleteWorkspace(@Param("hash") String hash) {
        if (!agentService.isReady()) throw new ServiceException("Agent 未初始化");
        if (hash == null || hash.isEmpty()) {
            throw new ServiceException("工作区 hash 不能为空");
        }
        boolean ok = agentService.deleteWorkspace(hash);
        if (ok) {
            return ApiResponse.ok("工作区已删除");
        }
        throw new ServiceException("删除工作区失败");
    }
}
