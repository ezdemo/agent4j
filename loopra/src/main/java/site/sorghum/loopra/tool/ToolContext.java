package site.sorghum.loopra.tool;

import lombok.Getter;
import lombok.Setter;
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
@Setter
public class ToolContext {

    /**
     * AgentLoop 控制器线程局部引用。
     */
    private static final ThreadLocal<AgentLoopController> CONTROLLER_TL = new ThreadLocal<>();
    /**
     * 调用参数
     */
    private Map<String, Object> params;
    /**
     * 项目根目录（可选）
     */
    private String rootDir;
    /**
     * 状态根目录（可选）——会话身份/Goal/Checklist/会话持久化归属的项目。
     * <p>隔离分支模式下，{@code rootDir} 指向隔离分支（AI 文件操作落点），
     * {@code stateRootDir} 仍指向主项目，保证会话级状态跨隔离分支生命周期延续。</p>
     */
    private String stateRootDir;
    /**
     * 当前会话ID（可选，用于按会话隔离数据）
     */
    private String sessionId;

    /**
     * 无参构造器。
     *
     * <p>供 Snack4 等 Bean 解码器还原参数使用；业务代码应优先使用带参构造器。</p>
     */
    public ToolContext() {
        this.params = Collections.emptyMap();
    }

    /**
     * 全参数构造器。
     * <p>不使用的参数传 {@code null} 或合适的默认值。</p>
     */
    public ToolContext(Map<String, Object> params, String rootDir, String sessionId) {
        this(params, rootDir, null, sessionId);
    }

    /**
     * 全参数构造器（含状态根目录）。
     *
     * @param stateRootDir 状态根目录；为 null 时调用方按 rootDir 回退
     */
    public ToolContext(Map<String, Object> params, String rootDir, String stateRootDir, String sessionId) {
        this.params = params != null ? new HashMap<>(params) : Collections.emptyMap();
        this.rootDir = rootDir;
        this.stateRootDir = stateRootDir;
        this.sessionId = sessionId;
    }

    /**
     * 设置调用参数，并做防御性拷贝。
     */
    public void setParams(Map<String, Object> params) {
        this.params = params != null ? new HashMap<>(params) : Collections.emptyMap();
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
        return rootDir == null || rootDir.isBlank() ? null : Paths.get(rootDir);
    }

    /**
     * 获取状态根目录；未设置时回退到 {@link #getRootDir()}。
     * 用于 Goal/Checklist/会话持久化等按项目归档的会话级状态。
     */
    @ONodeAttr(ignore = true)
    public Path getStateRootDir() {
        if (stateRootDir != null && !stateRootDir.isBlank()) {
            return Paths.get(stateRootDir);
        }
        return getRootDir();
    }
}
