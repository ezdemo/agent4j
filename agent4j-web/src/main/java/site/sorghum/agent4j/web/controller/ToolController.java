package site.sorghum.agent4j.web.controller;

import org.noear.solon.annotation.*;

import site.sorghum.agent4j.bin.tool.ToolDef;
import site.sorghum.agent4j.web.model.ApiResponse;
import site.sorghum.agent4j.web.model.ToolExecuteRequest;
import site.sorghum.agent4j.web.service.AgentService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        List<Map<String, Object>> tools = new ArrayList<>();
        for (ToolDef def : agentService.getSharedToolRegistry().all().values()) {
            tools.add(toToolMap(def));
        }
        return ApiResponse.ok(tools);
    }

    /** 获取工具详情 —— GET /api/tools/{name} */
    @Get
    @Mapping("/{name}")
    public Object get(@Path("name") String name) {
        if (!agentService.isReady()) return ApiResponse.fail("Agent 未初始化");
        ToolDef tool = agentService.getSharedToolRegistry().get(name);
        if (tool == null) return ApiResponse.fail("工具不存在: " + name);
        return ApiResponse.ok(toToolMap(tool));
    }

    /** 将 ToolDef 转为可安全序列化的 Map（剔除 lambda fn 字段避免 Snack4 StackOverflow） */
    private static Map<String, Object> toToolMap(ToolDef def) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", def.name);
        m.put("description", def.description);
        m.put("readOnly", def.readOnly);
        m.put("stormExempt", def.stormExempt);
        // 参数列表
        List<Map<String, Object>> params = new ArrayList<>();
        for (ToolDef.ParamDef p : def.params) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("name", p.name);
            pm.put("type", p.type);
            pm.put("description", p.description);
            pm.put("required", p.required);
            params.add(pm);
        }
        m.put("params", params);
        return m;
    }

}
