package site.sorghum.agent4j.web.controller;

import org.noear.solon.annotation.*;

import site.sorghum.agent4j.bin.config.Agent4jConfig;
import site.sorghum.agent4j.web.model.ApiResponse;
import site.sorghum.agent4j.web.service.AgentService;

import java.util.LinkedHashMap;
import java.util.Map;

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
            data.put("workspace", config.workspaceDir() != null
                    ? config.workspaceDir().toString() : null);
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

    /** 获取 Token 用量统计 —— GET /api/usage */
    @Get
    @Mapping("/usage")
    public Object getUsage() {
        if (!agentService.isReady()) return ApiResponse.fail("Agent 未初始化");
        return ApiResponse.ok(agentService.getUsage());
    }
}
