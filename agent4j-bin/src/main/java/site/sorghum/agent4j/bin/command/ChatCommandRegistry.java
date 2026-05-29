package site.sorghum.agent4j.bin.command;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Init;
import org.noear.solon.annotation.Inject;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ChatCommandRegistry — 聊天命令注册表。
 * <p>
 * 通过 Solon IoC 的 {@link Inject @Inject} 自动收集所有 {@link ChatCommand} Bean，
 * 提供命令匹配、执行、帮助信息生成等能力。
 * </p>
 *
 * <p>
 * 匹配策略：遍历所有已注册命令，调用 {@link ChatCommand#matches(String)} 方法。
 * 精确匹配（如 /new、/exit）和参数化匹配（如 /load N、/rewind N）均被支持。
 * 命令按名称长度降序匹配，避免短命令名误匹配长命令。
 * </p>
 *
 * <p>
 * <strong>扩展方式：</strong>新增命令只需实现 {@link ChatCommand} 接口并标注
 * {@link Component @Component}，Solon 会自动将其注入到此注册表，
 * 无需修改任何现有代码。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class ChatCommandRegistry {

    @Inject
    private List<ChatCommand> commands;

    /** 按名称长度降序排列的命令列表 */
    private List<ChatCommand> sortedCommands = Collections.emptyList();

    @Init
    public void init() {
        if (commands == null || commands.isEmpty()) {
            sortedCommands = Collections.emptyList();
            return;
        }
        // 去重 + 按命令名长度降序排列
        Set<String> seen = new HashSet<>();
        List<ChatCommand> list = new ArrayList<>();
        for (ChatCommand cmd : commands) {
            String name = cmd.getCommand();
            if (name != null && !name.isEmpty() && seen.add(name.toLowerCase())) {
                list.add(cmd);
            }
        }
        list.sort((a, b) -> b.getCommand().length() - a.getCommand().length());
        this.sortedCommands = Collections.unmodifiableList(list);
    }

    /**
     * 尝试匹配一个命令。
     *
     * @param input 用户输入（如 "/load 3"）
     * @return 匹配到的命令，无匹配返回 null
     */
    public ChatCommand match(String input) {
        if (input == null || input.trim().isEmpty()) return null;
        String trimmed = input.trim();
        if (!trimmed.startsWith("/")) return null;

        for (ChatCommand cmd : sortedCommands) {
            if (cmd.matches(input)) {
                return cmd;
            }
        }
        return null;
    }

    /**
     * 匹配并执行命令。
     *
     * @param input   用户输入
     * @param context 执行上下文
     * @return 执行结果，无匹配时返回 null
     * @throws Exception 执行异常
     */
    public ChatCommand.CommandResult execute(String input, ChatCommandContext context) throws Exception {
        ChatCommand cmd = match(input);
        if (cmd == null) return null;
        return cmd.execute(input, context);
    }

    /**
     * 获取所有命令的帮助文本列表（按命令名字母序排列）。
     */
    public List<String> getHelpLines() {
        List<String> lines = new ArrayList<>();
        List<ChatCommand> sorted = new ArrayList<>(sortedCommands);
        sorted.sort(Comparator.comparing(c -> c.getCommand().toLowerCase()));
        for (ChatCommand cmd : sorted) {
            lines.add(cmd.getDescription());
        }
        return lines;
    }

    /**
     * 获取已注册的命令数量。
     */
    public int size() {
        return sortedCommands.size();
    }

    /**
     * 获取所有命令的只读列表。
     */
    public List<ChatCommand> getAll() {
        return sortedCommands;
    }

    /**
     * 获取所有命令的元数据列表（供前端命令选择弹窗使用）。
     *
     * @return 命令元数据列表，每项包含 cmd、desc、type、argHint
     */
    public List<Map<String, Object>> getCommandMetaList() {
        List<Map<String, Object>> result = new ArrayList<>();
        List<ChatCommand> sorted = new ArrayList<>(sortedCommands);
        sorted.sort(Comparator.comparing(c -> c.getCommand().toLowerCase()));
        for (ChatCommand cmd : sorted) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("cmd", "/" + cmd.getCommand());
            meta.put("desc", cmd.getShortDescription());
            meta.put("type", cmd.getCommandType());
            if (cmd.getArgHint() != null) {
                meta.put("argHint", cmd.getArgHint());
            }
            result.add(meta);
        }
        return result;
    }
}
