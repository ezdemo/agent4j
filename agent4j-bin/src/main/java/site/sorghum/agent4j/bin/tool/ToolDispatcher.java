package site.sorghum.agent4j.bin.tool;

import lombok.Getter;
import lombok.Setter;
import org.noear.snack4.ONode;
import site.sorghum.agent4j.bin.agent.resilient.StormBreaker;
import site.sorghum.agent4j.bin.util.ONodeUtil;
import site.sorghum.agent4j.tool.AgentLoopController;
import site.sorghum.agent4j.tool.ToolContext;

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

    /** Plan Mode 拒绝原因标识 */
    public static final String REJECTED_REASON_PLAN_MODE = "plan-mode";
    /** Storm 断路器拒绝原因标识 */
    public static final String REJECTED_REASON_STORM = "storm";

    /**
     * 工具调用前拦截器：接收工具名称，返回拦截结果（null 表示放行）。
     */
    @FunctionalInterface
    public interface PreDispatchHook {
        /** @return 拦截结果字符串，或 null 表示放行继续执行 */
        String apply(String toolName);
    }

    /**
     * 工具调用后拦截器：接收工具名称和结果，返回修改后的结果。
     */
    @FunctionalInterface
    public interface PostDispatchHook {
        /** @return 修改后的结果字符串 */
        String apply(String toolName, String result);
    }

    private final ToolRegistry registry;
    /**
     * Storm 断路器（每回合重置）
     */
    @Getter
    private final StormBreaker stormBreaker = new StormBreaker();
    /**
     * Plan Mode — 开启后仅允许只读工具
     */
    @Setter
    @Getter
    private boolean planMode = false;
    /**
     * 工具调用前拦截器
     */
    @Setter
    private PreDispatchHook preDispatchHook = null;

    /** 向后兼容：接受 {@link java.util.function.Function} 并包装为 {@link PreDispatchHook} */
    public void setPreDispatchHook(Function<String, String> hook) {
        this.preDispatchHook = hook != null ? hook::apply : null;
    }

    /**
     * 工具调用后拦截器
     */
    @Setter
    private PostDispatchHook postDispatchHook = null;

    /** 向后兼容：接受 {@link java.util.function.BiFunction} 并包装为 {@link PostDispatchHook} */
    public void setPostDispatchHook(BiFunction<String, String, String> hook) {
        this.postDispatchHook = hook != null ? hook::apply : null;
    }
    /**
     * 当前会话 ID —— 自动注入到工具调用上下文中。
     */
    @Setter
    @Getter
    private volatile String sessionId;

    public ToolDispatcher(ToolRegistry registry) {
        this.registry = registry;
    }

    /** 构建 JSON 错误字符串。 */
    private static String error(String msg) {
        ONode node = ONode.ofJson("{}").asObject();
        node.set("error", msg);
        return node.toJson();
    }

    /** 构建含 rejectedReason 的 JSON 拒绝响应。 */
    private static String rejected(String msg, String reason) {
        ONode node = ONode.ofJson("{}").asObject();
        node.set("error", msg);
        node.set("rejectedReason", reason);
        return node.toJson();
    }

    /** 重置 Storm 断路器（每回合开始时调用）。 */
    public void resetStorm() {
        stormBreaker.reset();
    }

    // ---- Dispatch ----

    /**
     * 执行工具调用（无 AgentLoopController），返回结果字符串。
     *
     * @param name          工具名称
     * @param argumentsJson 参数 JSON 字符串
     * @return 工具执行结果字符串
     */
    public String dispatch(String name, String argumentsJson) {
        return dispatch(name, argumentsJson, null);
    }

    /**
     * 执行工具调用，依次经过 预拦截 → Plan Mode 门控 → Storm 检测 →
     * 参数解析 → 实际调用 → 后拦截，最终返回结果字符串。
     *
     * @param name          工具名称
     * @param argumentsJson 参数 JSON 字符串
     * @param controller    AgentLoop 控制器（可选，通过 ThreadLocal 注入当前线程）
     * @return 工具执行结果字符串
     */
    public String dispatch(String name, String argumentsJson, AgentLoopController controller) {
        if (name == null || name.equals("null")) {
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
            return rejected(name
                    + ": 计划模式下不可用——当前为只读探索阶段。"
                    + "请使用 read_file / glob / grep / tree / get_file_info 调查代码。"
                    + "准备好后调用 submit_plan 提交计划供审批。",
                    REJECTED_REASON_PLAN_MODE);
        }

        // Storm Breaker 检查
        if (!tool.stormExempt()) {
            StormBreaker.SuppressResult sr = stormBreaker.inspect(name, argumentsJson, tool.readOnly());
            if (sr.suppressed) {
                return rejected(sr.reason, REJECTED_REASON_STORM);
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

        // 通过 ThreadLocal 注入 AgentLoopController，替代 args 传递
        if (controller != null) {
            ToolContext.setCurrentController(controller);
        }

        try {
            String result = tool.fn().call(args);
            result = result != null ? result : "(ok)";

            // Post-dispatch Hook
            if (postDispatchHook != null) {
                result = postDispatchHook.apply(name, result);
            }

            return result;
        } catch (site.sorghum.agent4j.tool.HitlRequiredException e) {
            throw e; // 向上传播到 AgentLoop 触发 HITL 审批
        } catch (Exception e) {
            return error(name + ": " + e.getMessage());
        } finally {
            if (controller != null) {
                ToolContext.clearCurrentController();
            }
        }
    }
}
