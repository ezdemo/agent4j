package site.sorghum.agent4j.web.controller;

import org.noear.solon.annotation.*;

import site.sorghum.agent4j.web.model.ApiResponse;
import site.sorghum.agent4j.web.model.ToolExecuteRequest;
import site.sorghum.agent4j.web.service.AgentService;

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

    /** 列出所有已注册工具 —— GET /api/tools */
    @Get
    @Mapping("")
    public Object list() {
        if (!agentService.isReady()) return ApiResponse.fail("Agent 未初始化");
        return ApiResponse.ok(agentService.listTools());
    }

    /** 获取工具详情 —— GET /api/tools/{name} */
    @Get
    @Mapping("/{name}")
    public Object get(String name) {
        if (!agentService.isReady()) return ApiResponse.fail("Agent 未初始化");
        Object tool = agentService.getTool(name);
        if (tool == null) return ApiResponse.fail("工具不存在: " + name);
        return ApiResponse.ok(tool);
    }

    /** 直接执行工具 —— POST /api/tools/{name}/execute */
    @Post
    @Mapping("/{name}/execute")
    public Object execute(String name, @Body ToolExecuteRequest request) {
        if (!agentService.isReady()) return ApiResponse.fail("Agent 未初始化");
        try {
            String result = agentService.executeTool(name,
                    request != null ? request.arguments : null);
            return ApiResponse.ok(result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail("执行出错: " + e.getMessage());
        }
    }
}
