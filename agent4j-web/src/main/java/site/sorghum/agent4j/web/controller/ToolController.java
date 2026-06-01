package site.sorghum.agent4j.web.controller;

import org.noear.solon.annotation.*;
import site.sorghum.agent4j.bin.tool.ToolDef;
import site.sorghum.agent4j.web.common.ServiceException;
import site.sorghum.agent4j.web.model.ApiResponse;
import site.sorghum.agent4j.web.model.ToolInfoDTO;
import site.sorghum.agent4j.web.model.ToolParamInfoDTO;
import site.sorghum.agent4j.web.service.AgentService;

import java.util.ArrayList;
import java.util.List;

/**
 * 工具管理 API 控制器 —— 列出工具、查看详情、直接执行。
 *
 * @author Sorghum
 */
@Controller
@Mapping("/api/tools")
public class ToolController {

    @Inject
    private AgentService agentService;

    /**
     * 将 ToolDef 转为安全序列化的 DTO（剔除 lambda fn 字段避免 Snack4 StackOverflow）
     */
    private static ToolInfoDTO toToolInfoDTO(ToolDef def) {
        List<ToolParamInfoDTO> params = new ArrayList<>();
        for (ToolDef.ParamDef p : def.params()) {
            params.add(new ToolParamInfoDTO(p.name(), p.type(), p.description(), p.required()));
        }
        return new ToolInfoDTO(def.name(), def.description(), def.readOnly(), def.stormExempt(), params);
    }

    /**
     * 列出所有已注册工具 —— GET /api/tools
     */
    @Get
    @Mapping("")
    public ApiResponse<List<ToolInfoDTO>> list() {
        if (!agentService.isReady()) throw new ServiceException("Agent 未初始化");
        List<ToolInfoDTO> tools = new ArrayList<>();
        for (ToolDef def : agentService.getSharedToolRegistry().all().values()) {
            tools.add(toToolInfoDTO(def));
        }
        return ApiResponse.ok(tools);
    }

    /**
     * 获取工具详情 —— GET /api/tools/{name}
     */
    @Get
    @Mapping("/{name}")
    public ApiResponse<ToolInfoDTO> get(@Path("name") String name) {
        if (!agentService.isReady()) throw new ServiceException("Agent 未初始化");
        ToolDef tool = agentService.getSharedToolRegistry().get(name);
        if (tool == null) throw new ServiceException("工具不存在: " + name);
        return ApiResponse.ok(toToolInfoDTO(tool));
    }
}
