package site.sorghum.agent4j.bin.tool;

import org.noear.snack4.ONode;

import org.smartboot.socket.util.StringUtils;
import site.sorghum.agent4j.bin.agent.StormBreaker;
import site.sorghum.agent4j.bin.util.ONodeUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 工具调度器 —— 负责分发工具调用，处理 plan mode / storm breaker / hooks / token 截断。
 * <p>
 * 从 ToolRegistry 中抽出，遵循单一职责原则。
 * </p>
 *
 * @author Sorghum
 */
public class ToolDispatcher {

    private final ToolRegistry registry;

    /** Plan Mode — 开启后仅允许只读工具 */
    private boolean planMode = false;

    /** 工具调用前拦截器 */
    private Function<String, String> preDispatchHook = null;

    /** 工具调用后拦截器 */
    private BiFunction<String, String, String> postDispatchHook = null;

    /** Storm 断路器（每回合重置） */
    private final StormBreaker stormBreaker = new StormBreaker();

    /** 当前会话ID（注入到工具 args 中） */
    private volatile String sessionId;

    public ToolDispatcher(ToolRegistry registry) {
        this.registry = registry;
    }

    /** 设置当前会话ID */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /** 获取当前会话ID */
    public String getSessionId() {
        return sessionId;
    }

    // ---- Plan Mode ----

    public boolean isPlanMode() { return planMode; }

    public void setPlanMode(boolean on) { this.planMode = on; }

    // ---- Hooks ----

    public void setPreDispatchHook(Function<String, String> hook) {
        this.preDispatchHook = hook;
    }

    public void setPostDispatchHook(BiFunction<String, String, String> hook) {
        this.postDispatchHook = hook;
    }

    // ---- Storm ----

    public void resetStorm() {
        stormBreaker.reset();
    }

    public StormBreaker getStormBreaker() {
        return stormBreaker;
    }

    // ---- Dispatch ----

    /** 工具结果最大 token 数 */
    private static final int MAX_RESULT_TOKENS = 8000;

    /** 执行工具调用，返回结果字符串 */
    public String dispatch(String name, String argumentsJson, int maxResultTokens) {
        if (name == null || name.equals("null")){
            return error("请重新思考,调用方式错误，工具名不能为null。");
        }
        ToolDef tool = registry.get(name);
        if (tool == null) {
            return error("unknown tool: " + name);
        }

        // Pre-dispatch Hook
        if (preDispatchHook != null) {
            String intercepted = preDispatchHook.apply(name);
            if (intercepted != null) return intercepted;
        }

        // Plan Mode 门控
        if (planMode && !tool.readOnly()) {
            return "{\"error\":\"" + name
                    + ": 计划模式下不可用——当前为只读探索阶段。"
                    + "请使用 read_file / glob / grep / tree / get_file_info 调查代码。"
                    + "准备好后调用 submit_plan 提交计划供审批。"
                    + "\",\"rejectedReason\":\"plan-mode\"}";
        }

        // Storm Breaker 检查
        if (!tool.stormExempt()) {
            StormBreaker.SuppressResult sr = stormBreaker.inspect(name, argumentsJson, tool.readOnly());
            if (sr.suppressed) {
                return "{\"error\":\"" + sr.reason + "\",\"rejectedReason\":\"storm\"}";
            }
        }

        Map<String, Object> args;
        try {
            ONode node = ONode.ofJson(argumentsJson);
            args = ONodeUtil.toMap(node);
        } catch (Exception e) {
            return error(name + ": invalid arguments JSON — " + e.getMessage());
        }

        // 注入 sessionId 到 args 中（供 ToolContext 使用）
        if (sessionId != null) {
            args.put("__sessionId__", sessionId);
        }

        try {
            String result = tool.fn().call(args);
            result = result != null ? result : "(ok)";

            // Post-dispatch Hook
            if (postDispatchHook != null) {
                result = postDispatchHook.apply(name, result);
            }

            // Token 截断
            int estimatedTokens = result.length() / 2;
            if (estimatedTokens > maxResultTokens) {
                int maxChars = maxResultTokens * 2;
                if (result.length() > maxChars) {
                    result = result.substring(0, maxChars)
                            + "\n\n[… truncated " + (result.length() - maxChars)
                            + " chars (~" + (estimatedTokens - maxResultTokens) + " tokens) …]";
                }
            }
            return result;
        } catch (Exception e) {
            return error(name + ": " + e.getMessage());
        }
    }

    /** 兼容无 token 上限的调用 */
    public String dispatch(String name, String argumentsJson) {
        return dispatch(name, argumentsJson, MAX_RESULT_TOKENS);
    }

    private static String error(String msg) {
        return "{\"error\":\"" + msg.replace("\"", "\\\"") + "\"}";
    }


}
