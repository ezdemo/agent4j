package site.sorghum.agent4j.tool.terminal;

import java.util.ArrayList;
import java.util.List;

/**
 * 命令分词器 —— 无 env / glob / backtick / $(…) 展开。
 * <p>
 * 参考 Reasonix TS tokenizeCommand：单引号内全部字面量，
 * 双引号内仅 \" 和 \\ 转义，其他 \\X 保持字面（兼容 Windows 路径如 "C:\Users"）。
 * </p>
 */
public class CommandTokenizer {

    /** 双引号内的转义：\" 和 \\ */
    private static boolean isDqEscape(char prev, char next) {
        return prev == '\\' && (next == '"' || next == '\\');
    }

    /**
     * 将命令字符串拆分为 argv 列表。
     *
     * @throws IllegalArgumentException 引号未闭合
     */
    public static List<String> tokenize(String cmd) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        Character quote = null; // '"' or '\'' or null
        for (int i = 0; i < cmd.length(); i++) {
            char ch = cmd.charAt(i);
            if (quote != null) {
                if (ch == quote.charValue()) {
                    quote = null;
                } else if (quote == '"' && i + 1 < cmd.length() && isDqEscape(ch, cmd.charAt(i + 1))) {
                    cur.append(cmd.charAt(++i));
                } else {
                    cur.append(ch);
                }
                continue;
            }
            if (ch == '"' || ch == '\'') {
                quote = ch;
                continue;
            }
            if (ch == ' ' || ch == '\t') {
                if (cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
                continue;
            }
            cur.append(ch);
        }
        if (quote != null) {
            throw new IllegalArgumentException("unclosed " + quote + " in command");
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }
}
