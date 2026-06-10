package site.sorghum.agent4j.web.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.noear.solon.annotation.*;
import site.sorghum.agent4j.bin.tool.ToolDef;
import site.sorghum.agent4j.web.common.ServiceException;
import site.sorghum.agent4j.web.common.WebErrorMessages;
import site.sorghum.agent4j.web.model.ApiResponse;
import site.sorghum.agent4j.web.model.ToolInfoDTO;
import site.sorghum.agent4j.web.model.ToolParamInfoDTO;
import site.sorghum.agent4j.web.service.AgentService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    private static ToolInfoDTO toToolInfoDTO(ToolDef def) {
        List<ToolParamInfoDTO> params = new ArrayList<>();
        for (ToolDef.ParamDef p : def.params()) {
            params.add(new ToolParamInfoDTO(p.name(), p.type(), p.description(), p.required()));
        }
        return new ToolInfoDTO(def.name(), def.description(), def.readOnly(), def.stormExempt(), params);
    }

    @ApiOperation(value = "列出所有工具", notes = "返回所有已注册的 Agent 工具列表，含参数定义")
    @Get
    @Mapping("")
    public ApiResponse<List<ToolInfoDTO>> list() {
        if (!agentService.isReady()) throw new ServiceException(WebErrorMessages.AGENT_NOT_READY);
        List<ToolInfoDTO> tools = new ArrayList<>();
        agentService.getSharedToolRegistry().refresh();
        Map<String, ToolDef> allTools = agentService.getSharedToolRegistry().all();
        if (allTools != null) {
            for (ToolDef def : allTools.values()) {
                tools.add(toToolInfoDTO(def));
            }
        }
        return ApiResponse.ok(tools);
    }

    @ApiOperation(value = "获取工具详情", notes = "根据工具名称获取详细的参数定义和描述")
    @Get
    @Mapping("/{name}")
    public ApiResponse<ToolInfoDTO> get(@ApiParam(value = "工具名称") @Path("name") String name) {
        if (!agentService.isReady()) throw new ServiceException(WebErrorMessages.AGENT_NOT_READY);
        ToolDef tool = agentService.getSharedToolRegistry().get(name);
        if (tool == null) throw new ServiceException("工具不存在: " + name);
        return ApiResponse.ok(toToolInfoDTO(tool));
    }
}
