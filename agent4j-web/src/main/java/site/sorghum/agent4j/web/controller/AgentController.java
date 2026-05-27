package site.sorghum.agent4j.web.controller;

import org.noear.solon.annotation.*;
import org.noear.solon.core.handle.Context;

import site.sorghum.agent4j.web.model.ApiResponse;
import site.sorghum.agent4j.web.model.RewindRequest;
import site.sorghum.agent4j.web.service.AgentService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent 控制 API —— 状态查询、retry/rewind/compact、Plan Mode、HITL。
 *
 * @author Sorghum
 */
@Controller
@Mapping("/api/agent")
public class AgentController {

    @Inject
    private AgentService agentService;

    // ==================== 状态 ====================

    /** 获取 Agent 状态 —— GET /api/agent/status */
    @Get
    @Mapping("/status")
    public Object status() {
        if (!agentService.isReady()) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("ready", false);
            return ApiResponse.ok(s);
        }
        return ApiResponse.ok(agentService.getStatus());
    }

    /** 获取历史消息 —— GET /api/agent/history */
    @Get
    @Mapping("/history")
    public Object history() {
        if (!agentService.isReady()) return ApiResponse.fail("Agent 未初始化");
        return ApiResponse.ok(agentService.getHistory());
    }

    // ==================== 操作 ====================

    /** 撤回并重试 —— POST /api/agent/retry */
    @Post
    @Mapping("/retry")
    public Object retry() throws Exception {
        if (!agentService.isReady()) return ApiResponse.fail("Agent 未初始化");
        try {
            String reply = agentService.retryLast();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("reply", reply);
            data.put("usage", agentService.getUsage());
            return ApiResponse.ok(data);
        } catch (Exception e) {
            return ApiResponse.fail("重试失败: " + e.getMessage());
        }
    }

    /** 回退到指定轮次 —— POST /api/agent/rewind */
    @Post
    @Mapping("/rewind")
    public Object rewind(@Body RewindRequest request) throws Exception {
        if (!agentService.isReady()) return ApiResponse.fail("Agent 未初始化");
        if (request == null) return ApiResponse.fail("请求体不能为空");
        try {
            String reply = agentService.rewind(request.step);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("reply", reply);
            data.put("usage", agentService.getUsage());
            return ApiResponse.ok(data);
        } catch (Exception e) {
            return ApiResponse.fail("回退失败: " + e.getMessage());
        }
    }

    /** 折叠上下文 —— POST /api/agent/compact */
    @Post
    @Mapping("/compact")
    public Object compact() {
        if (!agentService.isReady()) return ApiResponse.fail("Agent 未初始化");
        try {
            agentService.compact();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("message", "上下文已折叠");
            data.put("historySize", agentService.getStatus().get("historySize"));
            return ApiResponse.ok(data);
        } catch (Exception e) {
            return ApiResponse.fail("折叠失败: " + e.getMessage());
        }
    }

    // ==================== Plan Mode ====================

    /** 进入计划模式 —— POST /api/agent/plan/enable */
    @Post
    @Mapping("/plan/enable")
    public Object enablePlanMode() {
        if (!agentService.isReady()) return ApiResponse.fail("Agent 未初始化");
        agentService.setPlanMode(true);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("planMode", true);
        data.put("message", "已进入计划模式（仅只读工具可用）");
        return ApiResponse.ok(data);
    }

    /** 退出计划模式 —— POST /api/agent/plan/disable */
    @Post
    @Mapping("/plan/disable")
    public Object disablePlanMode() {
        if (!agentService.isReady()) return ApiResponse.fail("Agent 未初始化");
        agentService.setPlanMode(false);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("planMode", false);
        data.put("message", "已退出计划模式（所有工具恢复正常）");
        return ApiResponse.ok(data);
    }

    // ==================== HITL ====================

    /** 获取 HITL 状态 —— GET /api/agent/hitl/status */
    @Get
    @Mapping("/hitl/status")
    public Object hitlStatus() {
        if (!agentService.isReady()) return ApiResponse.fail("Agent 未初始化");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("hitlMode", agentService.getStatus().get("hitlMode"));
        data.put("hasPendingHitl", agentService.getStatus().get("hasPendingHitl"));
        return ApiResponse.ok(data);
    }

    /** 切换 HITL 模式 —— POST /api/agent/hitl/toggle */
    @Post
    @Mapping("/hitl/toggle")
    public Object toggleHitl() {
        if (!agentService.isReady()) return ApiResponse.fail("Agent 未初始化");
        agentService.toggleHitl();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("hitlMode", agentService.getStatus().get("hitlMode"));
        return ApiResponse.ok(data);
    }

    /** 批准 HITL —— POST /api/agent/hitl/approve */
    @Post
    @Mapping("/hitl/approve")
    public Object approveHitl() {
        if (!agentService.isReady()) return ApiResponse.fail("Agent 未初始化");
        agentService.approveHitl();
        return ApiResponse.ok("已批准工具调用");
    }

    /** 拒绝 HITL —— POST /api/agent/hitl/deny */
    @Post
    @Mapping("/hitl/deny")
    public Object denyHitl() {
        if (!agentService.isReady()) return ApiResponse.fail("Agent 未初始化");
        agentService.denyHitl();
        return ApiResponse.ok("已拒绝工具调用");
    }

    /** 获取待审批列表 —— GET /api/agent/hitl/pending */
    @Get
    @Mapping("/hitl/pending")
    public Object pendingHitl() {
        if (!agentService.isReady()) return ApiResponse.fail("Agent 未初始化");
        return ApiResponse.ok(agentService.getPendingHitl());
    }
}
