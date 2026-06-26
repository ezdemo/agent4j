package site.sorghum.agent4j.tool;

import lombok.Getter;
import org.noear.snack4.annotation.ONodeAttr;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 工具执行上下文——封装一次工具调用的全部入参。
 * <p>
 * 包含调用参数 Map 和一个可选的根目录路径，
 * 以及线程局部的 {@link AgentLoopController} 引用。
 * </p>
 *
 * @author Sorghum
 */
@Getter
public class ToolContext {

    /**
     * AgentLoop 控制器线程局部引用。
     */
    private static final ThreadLocal<AgentLoopController> CONTROLLER_TL = new ThreadLocal<>();
    /**
     * 调用参数
     */
    private final Map<String, Object> params;
    /**
     * 工作区根目录（可选）
     */
    private final String rootDir;
    /**
     * 当前会话ID（可选，用于按会话隔离数据）
     */
    private final String sessionId;

    /**
     * 全参数构造器。
     * <p>不使用的参数传 {@code null} 或合适的默认值。</p>
     */
    public ToolContext(Map<String, Object> params, String rootDir, String sessionId) {
        this.params = params != null ? new HashMap<>(params) : Collections.emptyMap();
        this.rootDir = rootDir;
        this.sessionId = sessionId;
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
                // 字符串无法解析为整数，返回默认值
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
     * 获取 AgentLoop 控制器引用，用于控制推理循环。
     * <p>从当前线程的 ThreadLocal 读取，无需通过构造器或 args 传递。</p>
     */
    public AgentLoopController getLoopController() {
        return CONTROLLER_TL.get();
    }

    /**
     * 检查参数是否存在。
     */
    public boolean has(String key) {
        return params.containsKey(key);
    }

    @Override
    public String toString() {
        return "ToolContext" + params;
    }

    @ONodeAttr(ignore = true)
    public Path getRootDir() {
        return Paths.get(rootDir);
    }
}
