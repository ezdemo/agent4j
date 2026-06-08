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
     * 获取命令的简短描述（不含命令名前缀）。
     * 用于前端命令选择弹窗显示。
     * <p>
     * 默认实现从 getDescription() 中提取 / 后的说明部分。
     * </p>
     *
     * @return 命令描述文本
     */
    default String getShortDescription() {
        String desc = getDescription();
        if (desc == null) return "";
        // 尝试提取 "/command    description" 格式中的 description 部分
        int slashIdx = desc.indexOf('/');
        if (slashIdx >= 0) {
            int spaceIdx = desc.indexOf(' ', slashIdx);
            if (spaceIdx >= 0) {
                return desc.substring(spaceIdx).trim();
            }
        }
        return desc;
    }

    /**
     * 获取命令类型。
     * 用于前端分类显示。
     * <p>
     * 可选值：session / mode / info / tool / system
     * </p>
     *
     * @return 命令类型
     */
    default String getCommandType() {
        return "system";
    }

    /**
     * 获取命令的参数提示（如有）。
     * 用于前端显示参数占位符。
     *
     * @return 参数提示，如 "N" 表示需要数字参数
     */
    default String getArgHint() {
        return null;
    }

    /**
     * 是否为静默命令。静默命令执行后：
     * <ul>
     *   <li>不显示 "✅ 已执行 /xxx 命令" 确认消息</li>
     *   <li>前端不渲染用户消息气泡</li>
     * </ul>
     * 适用于自行处理所有输出的命令（如 HITL 审批命令 /agree、/deny）。
     *
     * @return true 表示静默命令
     */
    default boolean isSilent() {
        return false;
    }

    /**
     * 执行命令。
     *
     * @param input   用户原始输入
     * @param context 执行上下文（持有 agent、scanner 等依赖）
     * @return {@link CommandResult#CONTINUE} 继续主循环，
     * {@link CommandResult#EXIT} 退出主循环
     * @throws Exception 执行异常
     */
    CommandResult execute(MessageWrapper input, ChatCommandContext context) throws Exception;

    /**
     * 命令执行结果枚举。
     */
    enum CommandResult {
        /**
         * 命令已处理，主循环继续（读取下一条输入）
         */
        CONTINUE,
        /**
         * 命令已处理，主循环应退出（如 /exit）
         */
        EXIT,
        /**
         * 继续走主流程
         */
        LOOP
    }
}
