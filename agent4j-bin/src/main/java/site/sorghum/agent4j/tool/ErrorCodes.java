package site.sorghum.agent4j.tool;

/**
 * 错误码常量。
 *
 * @author Sorghum
 */
public final class ErrorCodes {

    private ErrorCodes() {
        // 工具类，禁止实例化
    }

    /**
     * IO 错误
     */
    public static final String IO_ERROR = "IO_ERROR";

    /**
     * 没有可用的 AgentLoop 控制器
     */
    public static final String NO_CONTROLLER = "NO_CONTROLLER";

    /**
     * 没有可用的输出通道
     */
    public static final String NO_OUTPUT = "NO_OUTPUT";

    /**
     * 技能未找到
     */
    public static final String SKILL_NOT_FOUND = "SKILL_NOT_FOUND";

    /**
     * 插件执行错误
     */
    public static final String PLUGIN_EXEC_ERROR = "PLUGIN_EXEC_ERROR";

    /**
     * Solon 工具执行错误
     */
    public static final String SOLON_TOOL_EXEC_ERROR = "SOLON_TOOL_EXEC_ERROR";

    /**
     * 工具执行错误
     */
    public static final String TOOL_EXEC_ERROR = "TOOL_EXEC_ERROR";

    /**
     * 文件未找到
     */
    public static final String FILE_NOT_FOUND = "FILE_NOT_FOUND";

    /**
     * 通用错误
     */
    public static final String ERROR = "ERROR";
}
