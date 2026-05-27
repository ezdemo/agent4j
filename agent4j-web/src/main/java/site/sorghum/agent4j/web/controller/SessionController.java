package site.sorghum.agent4j.web.controller;

import org.noear.solon.annotation.*;
import org.noear.solon.core.handle.Context;

import site.sorghum.agent4j.web.model.ApiResponse;
import site.sorghum.agent4j.web.service.AgentService;

import java.util.LinkedHashMap;
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

    /** 列出所有会话 —— GET /api/sessions?workspaceHash=xxx */
    @Get
    @Mapping("")
    public Object list(@Param("workspaceHash") String workspaceHash) throws Exception {
        if (!agentService.isReady()) return ApiResponse.fail("Agent 未初始化");
        return ApiResponse.ok(agentService.listSessions(workspaceHash));
    }

    /** 获取当前会话信息 —— GET /api/sessions/current */
    @Get
    @Mapping("/current")
    public Object current() {
        if (!agentService.isReady()) return ApiResponse.fail("Agent 未初始化");
        return ApiResponse.ok(agentService.getCurrentSession());
    }

    /** 新建空白会话 —— POST /api/sessions/new */
    @Post
    @Mapping("/new")
    public Object createNew() {
        if (!agentService.isReady()) return ApiResponse.fail("Agent 未初始化");
        agentService.newSession();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", "已创建新会话");
        data.put("session", agentService.getCurrentSession());
        return ApiResponse.ok(data);
    }

    /** 切换会话 —— POST /api/sessions/{name} */
    @Post
    @Mapping("/{name}")
    public Object switchSession(@Path("name") String name) {
        if (!agentService.isReady()) return ApiResponse.fail("Agent 未初始化");
        boolean ok = agentService.switchSession(name);
        if (ok) return ApiResponse.ok(agentService.getCurrentSession());
        return ApiResponse.fail("会话不存在: " + name);
    }

    /** 删除会话 —— DELETE /api/sessions/{name} */
    @Delete
    @Mapping("/{name}")
    public Object deleteSession(@Path("name") String name) throws Exception {
        if (!agentService.isReady()) return ApiResponse.fail("Agent 未初始化");
        boolean ok = agentService.deleteSession(name);
        if (ok) return ApiResponse.ok();
        return ApiResponse.fail("会话不存在: " + name);
    }
}
