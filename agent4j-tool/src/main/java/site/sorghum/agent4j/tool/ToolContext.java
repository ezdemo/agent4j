package site.sorghum.agent4j.tool;

import lombok.Getter;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具执行上下文——封装一次工具调用的全部入参。
 * <p>
 * 包含调用参数 Map 和一个可选的根目录路径，
 * </p>
 *
 * @author Sorghum
 */
@Getter
public class ToolContext {

    /**
     * 线程局部沙箱旁路标志，供 resolveSafe 等深层方法在不持有 ToolContext 时检查。
     * HITL 审批通过路径越界后，AgentLoop 在重放执行前设置此标志。
     */
    private static final ThreadLocal<Boolean> SANDBOX_BYPASS_TL = new ThreadLocal<>();
    /**
     * AgentLoop 控制器线程局部引用。
     * 由 {@code ToolDispatcher.dispatch()} 在执行工具前注入，
     * 替代通过 args map 传递 {@code __controller__} 的硬编码方式。
     */
    private static final ThreadLocal<AgentLoopController> CONTROLLER_TL = new ThreadLocal<>();
    /**
     * 调用参数
     */
    private final Map<String, Object> params;
    /**
     * 工作区根目录（可选）
     */
    private final Path rootDir;
    /**
     * LLM API 地址（可选，供需要 API 调用的工具使用）
     */
    private final String apiUrl;
    /**
     * LLM API Key（可选）
     */
    private final String apiKey;
    /**
     * 工具注册表引用（可选，供需要创建子代理的工具使用）
     */
    private final Object toolRegistry;
    /**
     * 屏蔽目录列表（相对路径，相对于工作区根目录）
     */
    private final List<String> blockedPaths;
    /**
     * 当前会话ID（可选，用于按会话隔离数据）
     */
    private final String sessionId;

    /**
     * 沙箱旁路标志 — HITL 审批通过路径越界后置为 true，
     * resolveSafe 检测到此标志时跳过边界校验。
     */
    private final boolean skipSandboxCheck;

    /**
     * 全参数构造器。
     * <p>不使用的参数传 {@code null} 或合适的默认值。</p>
     */
    public ToolContext(Map<String, Object> params, Path rootDir, String apiUrl, String apiKey,
                       Object toolRegistry, List<String> blockedPaths, String sessionId,
                       boolean skipSandboxCheck) {
        this.params = params != null ? new HashMap<>(params) : Collections.emptyMap();
        this.rootDir = rootDir;
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.toolRegistry = toolRegistry;
        this.blockedPaths = blockedPaths != null ? blockedPaths : Collections.emptyList();
        this.sessionId = sessionId;
        this.skipSandboxCheck = skipSandboxCheck;
    }

    // ==================== 线程级沙箱旁路 ====================

    /**
     * 开启当前线程的沙箱旁路（resolveSafe 跳过边界检查）
     */
    public static void enableSandboxBypass() {
        SANDBOX_BYPASS_TL.set(true);
    }

    /**
     * 关闭当前线程的沙箱旁路
     */
    public static void disableSandboxBypass() {
        SANDBOX_BYPASS_TL.remove();
    }

    /**
     * 当前线程是否开启了沙箱旁路
     */
    public static boolean isSandboxBypass() {
        return Boolean.TRUE.equals(SANDBOX_BYPASS_TL.get());
    }

    /**
     * 沙箱旁路标志 — HITL 审批通过后为 true
     */
    public boolean isSkipSandboxCheck() {
        return skipSandboxCheck;
    }

    // ==================== 参数访问 ====================

    /**
     * 获取当前线程的 AgentLoop 控制器。
     * <p>返回 {@code null} 时表示没有关联的 AgentLoop
     * （如单元测试、后台任务等场景），调用方应做好空安全处理。</p>
     */
    public static AgentLoopController getCurrentController() {
        return CONTROLLER_TL.get();
    }

    /**
     * 在当前线程设置 AgentLoop 控制器。
     * <p>由 {@code ToolDispatcher.dispatch()} 在工具执行前调用，
     * 执行结束后在 {@code finally} 中清除。</p>
     */
    public static void setCurrentController(AgentLoopController controller) {
        if (controller != null) {
            CONTROLLER_TL.set(controller);
        }
    }

    /**
     * 清除当前线程的 AgentLoop 控制器（finally 中调用）。
     */
    public static void clearCurrentController() {
        CONTROLLER_TL.remove();
    }

    /**
     * 获取字符串参数。
     */
    public String getString(String key) {
        Object v = params.get(key);
        return v != null ? v.toString() : null;
    }

    /**
     * 获取字符串参数，带默认值。
     */
    public String getString(String key, String defaultValue) {
        String v = getString(key);
        return v != null ? v : defaultValue;
    }

    // ==================== 线程级 AgentLoopController ====================

    /**
     * 获取整数参数。
     */
    public int getInt(String key, int defaultValue) {
        Object v = params.get(key);
        if (v instanceof Number number) return number.intValue();
        if (v instanceof String str) {
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    /**
     * 获取布尔参数。
     */
    public boolean getBool(String key, boolean defaultValue) {
        Object v = params.get(key);
        if (v instanceof Boolean bool) return bool;
        if (v instanceof String str) return Boolean.parseBoolean(str);
        return defaultValue;
    }

    /**
     * 获取工具注册表（类型安全的调用方应自行转型）。
     */
    @SuppressWarnings("unchecked")
    public <T> T getToolRegistry() {
        return (T) toolRegistry;
    }

    /**
     * 获取 AgentLoop 控制器引用，用于控制推理循环。
     * <p>从当前线程的 ThreadLocal 读取，无需通过构造器或 args 传递。</p>
     */
    public AgentLoopController getLoopController() {
        return CONTROLLER_TL.get();
    }

    /**
     * 请求停止推理循环（空安全）。
     */
    public void requestStopLoop() {
        AgentLoopController ctrl = CONTROLLER_TL.get();
        if (ctrl != null) {
            ctrl.requestStop();
        }
    }

    /**
     * 在下一轮循环前注入一条用户消息（空安全）。
     *
     * @param message 要注入的用户消息
     */
    public void injectUserMessage(String message) {
        AgentLoopController ctrl = CONTROLLER_TL.get();
        if (ctrl != null) {
            ctrl.injectUserMessage(message);
        }
    }

    /**
     * 向下游推送自定义事件（空安全）。
     *
     * @param type 事件类型标识符
     * @param data JSON 格式的事件数据
     */
    public void emitEvent(String type, String data) {
        AgentLoopController ctrl = CONTROLLER_TL.get();
        if (ctrl != null) {
            ctrl.emitEvent(type, data);
        }
    }

    /**
     * 检查参数是否存在。
     */
    public boolean has(String key) {
        return params.containsKey(key);
    }

    /**
     * 参数数量。
     */
    public int paramCount() {
        return params.size();
    }

    /**
     * 检查目标路径是否在屏蔽目录列表中。
     * 目标路径必须是已解析的绝对路径。
     *
     * @param target 已解析的绝对路径
     * @return 如果路径被屏蔽返回 true
     */
    public boolean isPathBlocked(Path target) {
        if (blockedPaths.isEmpty() || rootDir == null || target == null) {
            return false;
        }
        Path rootAbs = rootDir.toAbsolutePath().normalize();
        Path targetAbs = target.toAbsolutePath().normalize();
        if (!targetAbs.startsWith(rootAbs)) {
            return false; // 路径越界由 resolveSafe 处理
        }
        for (String blocked : blockedPaths) {
            Path blockedPath = rootAbs.resolve(blocked).normalize();
            if (targetAbs.equals(blockedPath) || targetAbs.startsWith(blockedPath)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "ToolContext" + params;
    }
}
