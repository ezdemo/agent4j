package site.sorghum.agent4j.web.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.noear.dami2.Dami;
import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.annotation.*;
import site.sorghum.agent4j.bin.config.ConfigChangedEvent;
import site.sorghum.agent4j.bin.config.ConfigService;
import site.sorghum.agent4j.bin.tool.ToolRegistry;
import site.sorghum.agent4j.web.common.ServiceException;
import site.sorghum.agent4j.web.common.WebErrorMessages;
import site.sorghum.agent4j.web.model.ApiResponse;
import site.sorghum.agent4j.web.model.ToolInfoDTO;
import site.sorghum.agent4j.web.model.ToolParamInfoDTO;
import site.sorghum.agent4j.web.service.AgentService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工具管理 API 控制器 —— 列出工具、查看详情。
 *
 * @author Sorghum
 */
@Api(tags = "工具管理")
@Controller
@Mapping("/api/tools")
public class ToolController {

    @Inject
    private AgentService agentService;

    private static ToolInfoDTO toToolInfoDTO(FunctionTool def, boolean enabled) {
        List<ToolParamInfoDTO> params = new ArrayList<>();
        return new ToolInfoDTO(def.name(), def.description(), false, true, enabled, params);
    }

    @ApiOperation(value = "列出所有工具", notes = "返回所有已注册的 Agent 工具列表（含已禁用的），含启用状态")
    @Get
    @Mapping("")
    public ApiResponse<List<ToolInfoDTO>> list() {
        if (!agentService.isReady()) throw new ServiceException(WebErrorMessages.AGENT_NOT_READY);
        List<ToolInfoDTO> tools = new ArrayList<>();
        ToolRegistry registry = agentService.getSharedToolRegistry();
        registry.refresh();
        // 返回所有扫描到的工具（包括已禁用的）
        Map<String, FunctionTool> allTools = registry.allScanned();
        if (allTools != null) {
            for (FunctionTool def : allTools.values()) {
                boolean enabled = registry.isEnabled(def.name());
                tools.add(toToolInfoDTO(def, enabled));
            }
        }
        return ApiResponse.ok(tools);
    }

    @ApiOperation(value = "切换工具启用/禁用状态", notes = "启用或禁用指定工具，实时生效")
    @Post
    @Mapping("/{name}/toggle")
    public ApiResponse<String> toggle(@ApiParam(value = "工具名称") @Path("name") String name) {
        if (!agentService.isReady()) throw new ServiceException(WebErrorMessages.AGENT_NOT_READY);
        ToolRegistry registry = agentService.getSharedToolRegistry();
        registry.refresh();
        // 检查工具是否存在
        Map<String, FunctionTool> allTools = registry.allScanned();
        if (allTools == null || !allTools.containsKey(name)) {
            throw new ServiceException("工具不存在: " + name);
        }
        boolean currentlyEnabled = registry.isEnabled(name);
        // 从 ConfigService 获取最新的禁用列表，计算新集合
        Set<String> newDisabled = new HashSet<>(ConfigService.getDisabledTools());
        if (currentlyEnabled) {
            newDisabled.add(name);
            ConfigService.addDisabledTools(Collections.singletonList(name));
        } else {
            newDisabled.remove(name);
            ConfigService.removeDisabledTools(Collections.singletonList(name));
        }
        // 更新注册表的静态快照，使 refresh() 能反映变更
        registry.setDisabledTools(newDisabled);
        // 刷新注册表使变更生效
        registry.refresh();
        // 广播配置变更事件，通知所有活跃 Agent 实例刷新工具列表
        Dami.bus().send("config.changed", new ConfigChangedEvent("disabledTools",
                currentlyEnabled ? Collections.singletonList(name) : null));
        return ApiResponse.ok(currentlyEnabled ? "已禁用工具: " + name : "已启用工具: " + name);
    }

    @ApiOperation(value = "获取工具详情", notes = "根据工具名称获取详细的参数定义和描述")
    @Get
    @Mapping("/{name}")
    public ApiResponse<ToolInfoDTO> get(@ApiParam(value = "工具名称") @Path("name") String name) {
        if (!agentService.isReady()) throw new ServiceException(WebErrorMessages.AGENT_NOT_READY);
        ToolRegistry registry = agentService.getSharedToolRegistry();
        FunctionTool tool = registry.allScanned().get(name);
        if (tool == null) throw new ServiceException("工具不存在: " + name);
        boolean enabled = registry.isEnabled(name);
        return ApiResponse.ok(toToolInfoDTO(tool, enabled));
    }
}
