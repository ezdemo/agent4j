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

    public ToolContext(Map<String, Object> params) {
        this(params, null, null, null, null, Collections.emptyList());
    }

    public ToolContext(Map<String, Object> params, Path rootDir) {
        this(params, rootDir, null, null, null, Collections.emptyList());
    }

    public ToolContext(Map<String, Object> params, Path rootDir, String apiUrl, String apiKey) {
        this(params, rootDir, apiUrl, apiKey, null, Collections.emptyList());
    }

    public ToolContext(Map<String, Object> params, Path rootDir, String apiUrl, String apiKey,
                       Object toolRegistry) {
        this(params, rootDir, apiUrl, apiKey, toolRegistry, Collections.emptyList());
    }

    public ToolContext(Map<String, Object> params, Path rootDir, String apiUrl, String apiKey,
                       Object toolRegistry, List<String> blockedPaths) {
        this(params, rootDir, apiUrl, apiKey, toolRegistry, blockedPaths, null);
    }

    public ToolContext(Map<String, Object> params, Path rootDir, String apiUrl, String apiKey,
                       Object toolRegistry, List<String> blockedPaths, String sessionId) {
        this(params, rootDir, apiUrl, apiKey, toolRegistry, blockedPaths, sessionId, false);
    }

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
