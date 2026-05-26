package site.sorghum.agent4j.bin.command;

/**
 * ChatCommand — 聊天命令接口。
 * <p>
 * 实现类通过 {@link org.noear.solon.annotation.Component @Component} 注册，
 * 由 {@link ChatCommandRegistry} 自动收集。
 * 在 Agent4jApp 输入循环中，自动匹配 "/" 开头的命令并执行，
 * 替代传统 if-else 硬编码命令处理链。
 * </p>
 *
 * <h3>快速实现</h3>
 * <pre>{@code
 * @Component
 * public class MyCommand implements ChatCommand {
 *     public String getCommand() { return "mycmd"; }
 *     public String getDescription() { return "/mycmd       执行我的自定义命令"; }
 *     public CommandResult execute(String input, ChatCommandContext ctx) {
 *         System.out.println("执行 mycmd！");
 *         return CommandResult.CONTINUE;
 *     }
 * }
 * }</pre>
 *
 * <h3>带参数的命令</h3>
 * 重写 {@link #matches(String)} 方法即可支持参数化匹配：
 * <pre>{@code
 * public boolean matches(String input) {
 *     return input.trim().toLowerCase().startsWith("/mycmd ");
 * }
 * }</pre>
 *
 * @author Sorghum
 */
public interface ChatCommand {

    /**
     * 命令名称（不含 / 前缀），如 "new"、"compact"。
     * 用于注册表索引和帮助信息。
     */
    String getCommand();

    /**
     * 判断输入是否匹配此命令。
     * <p>
     * 默认实现：输入去除首尾空格后，不区分大小写地比较是否等于 {@code /{command}}。
     * 带参数的命令（如 /load N、/rewind N）应重写此方法。
     * </p>
     *
     * @param input 用户原始输入（包含 / 前缀）
     * @return true 如果匹配
     */
    default boolean matches(String input) {
        if (input == null) return false;
        String trimmed = input.trim();
        return trimmed.equalsIgnoreCase("/" + getCommand());
    }

    /**
     * 简短帮助文本（显示在 /help 中）。
     * 建议格式："{@code /&lt;命令&gt;        &lt;说明&gt;}"
     */
    String getDescription();

    /**
     * 执行命令。
     *
     * @param input   用户原始输入
     * @param context 执行上下文（持有 agent、scanner 等依赖）
     * @return {@link CommandResult#CONTINUE} 继续主循环，
     *         {@link CommandResult#EXIT} 退出主循环
     * @throws Exception 执行异常
     */
    CommandResult execute(String input, ChatCommandContext context) throws Exception;

    /**
     * 命令执行结果枚举。
     */
    enum CommandResult {
        /** 命令已处理，主循环继续（读取下一条输入） */
        CONTINUE,
        /** 命令已处理，主循环应退出（如 /exit） */
        EXIT
    }
}
