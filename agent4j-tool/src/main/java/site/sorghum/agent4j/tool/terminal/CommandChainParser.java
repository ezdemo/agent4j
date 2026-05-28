package site.sorghum.agent4j.tool.terminal;

import java.util.ArrayList;
import java.util.List;

/**
 * 命令链解析器 —— 自解析 && || | ; 和重定向，绝不调用真实 shell。
 * <p>
 * 参考 Reasonix TS shell-chain.ts + shell/parse.ts。
 * </p>
 */
public class CommandChainParser {

    // ---- 数据结构 ----

    public enum ChainOp { PIPE("|"), OR("||"), AND("&&"), SEMI(";");
        final String text; ChainOp(String t) { text = t; } }

    public enum RedirectKind { OUT(">"), APPEND(">>"), IN("<"), ERR_OUT("2>"),
        ERR_APPEND("2>>"), ERR_MERGE("2>&1"), ALL("&>");
        final String text; RedirectKind(String t) { text = t; }
        boolean needsTarget() { return this != ERR_MERGE; } }

    public static class Redirect {
        public final RedirectKind kind;
        public final String target; // "" for 2>&1
        public Redirect(RedirectKind kind, String target) {
            this.kind = kind; this.target = target == null ? "" : target; }
    }

    public static class ChainSegment {
        public final List<String> argv;
        public final List<Redirect> redirects;
        public ChainSegment(List<String> argv, List<Redirect> redirects) {
            this.argv = argv; this.redirects = redirects; }
    }

    public static class CommandChain {
        public final List<ChainSegment> segments;
        public final List<ChainOp> ops; // segs.size() - 1
        public CommandChain(List<ChainSegment> segments, List<ChainOp> ops) {
            this.segments = segments; this.ops = ops; }
    }

    // ---- 公共入口 ----

    /**
     * 解析命令字符串。返回 null 表示无链式操作符也无重定向（调用方可走简单路径）。
     * @throws UnsupportedSyntaxException 不支持的语法
     */
    public static CommandChain parse(String cmd) {
        SplitResult split = splitOnChainOps(cmd);
        List<ChainSegment> segments = new ArrayList<>();
        for (int i = 0; i < split.segs.size(); i++) {
            String trimmed = split.segs.get(i).trim();
            if (trimmed.isEmpty()) {
                String op = i == 0 ? split.ops.get(0).text
                        : i == split.segs.size() - 1 ? split.ops.get(i - 1).text
                        : split.ops.get(i - 1).text + " " + split.ops.get(i).text;
                String msg = i == 0 ? "empty segment before \"" + op + "\""
                        : i == split.segs.size() - 1 ? "chain ends with \"" + op + "\""
                        : "empty segment between operators";
                throw new UnsupportedSyntaxException(msg);
            }
            segments.add(parseSegment(trimmed));
        }

        // 拒绝 cd
        for (ChainSegment seg : segments) {
            if (!seg.argv.isEmpty()) {
                String cmdName = seg.argv.get(0);
                if ("cd".equalsIgnoreCase(cmdName)) {
                    throw new UnsupportedSyntaxException(
                            "cd in parsed command chains does not change cwd for later segments. "
                                    + "Use a command-native cwd flag instead, such as "
                                    + "`npm --prefix <dir> run <script>`, `git -C <dir> ...`, "
                                    + "or `cargo -C <dir> ...`.");
                }
            }
        }

        if (split.ops.isEmpty() && segments.get(0).redirects.isEmpty()) return null;
        return new CommandChain(segments, split.ops);
    }

    // ---- 链操作符分割 ----

    private static class SplitResult {
        final List<String> segs;
        final List<ChainOp> ops;
        SplitResult(List<String> segs, List<ChainOp> ops) { this.segs = segs; this.ops = ops; }
    }

    private static SplitResult splitOnChainOps(String cmd) {
        List<String> segs = new ArrayList<>();
        List<ChainOp> ops = new ArrayList<>();
        int segStart = 0;
        int i = 0;
        Character quote = null;
        boolean atTokenStart = true;
        while (i < cmd.length()) {
            char ch = cmd.charAt(i);
            if (quote != null) {
                if (ch == quote.charValue()) quote = null;
                else if (quote == '"' && i + 1 < cmd.length() && isDqEscape(ch, cmd.charAt(i + 1))) i++;
                i++; atTokenStart = false;
                continue;
            }
            if (ch == '"' || ch == '\'') { quote = ch; i++; atTokenStart = false; continue; }
            if (ch == ' ' || ch == '\t') { i++; atTokenStart = true; continue; }
            if (atTokenStart) {
                ChainOp op = null;
                int opLen = 0;
                if (ch == '|' && i + 1 < cmd.length() && cmd.charAt(i + 1) == '|') { op = ChainOp.OR; opLen = 2; }
                else if (ch == '&' && i + 1 < cmd.length() && cmd.charAt(i + 1) == '&') { op = ChainOp.AND; opLen = 2; }
                else if (ch == '|') { op = ChainOp.PIPE; opLen = 1; }
                else if (ch == ';') { op = ChainOp.SEMI; opLen = 1; }
                if (op != null) {
                    segs.add(cmd.substring(segStart, i));
                    ops.add(op);
                    i += opLen;
                    segStart = i;
                    atTokenStart = true;
                    continue;
                }
            }
            i++; atTokenStart = false;
        }
        segs.add(cmd.substring(segStart));
        return new SplitResult(segs, ops);
    }

    // ---- Segment 解析 ----

    /** 可变的 flush 状态（Java 8 不支持 lambda 捕获可变局部变量，用 holder 替代） */
    private static final class FlushState {
        final List<String> argv;
        final List<Redirect> redirects;
        StringBuilder cur = new StringBuilder();
        boolean curHasContent = false;
        RedirectKind pending = null;
        FlushState(List<String> argv, List<Redirect> redirects) {
            this.argv = argv; this.redirects = redirects; }
    }

    private static void flush(FlushState s) {
        if (!s.curHasContent && s.cur.length() == 0) return;
        if (s.pending != null) {
            s.redirects.add(new Redirect(s.pending, s.cur.toString()));
            s.pending = null;
        } else {
            s.argv.add(s.cur.toString());
        }
        s.cur.setLength(0);
        s.curHasContent = false;
    }

    private static ChainSegment parseSegment(String segStr) {
        List<String> argv = new ArrayList<>();
        List<Redirect> redirects = new ArrayList<>();
        FlushState state = new FlushState(argv, redirects);
        Character quote = null;

        int i = 0;
        while (i < segStr.length()) {
            char ch = segStr.charAt(i);
            if (quote != null) {
                if (ch == quote.charValue()) quote = null;
                else if (quote == '"' && i + 1 < segStr.length() && isDqEscape(ch, segStr.charAt(i + 1))) {
                    state.cur.append(segStr.charAt(++i)); state.curHasContent = true;
                } else { state.cur.append(ch); state.curHasContent = true; }
                i++; continue;
            }
            if (ch == '"' || ch == '\'') { quote = ch; state.curHasContent = true; i++; continue; }
            if (ch == ' ' || ch == '\t') { flush(state); i++; continue; }
            if (state.cur.length() == 0 && !state.curHasContent) {
                String remaining = segStr.substring(i);
                RedirectMatch m = matchRedirect(remaining);
                if (m != null) {
                    if (state.pending != null) throw new UnsupportedSyntaxException(
                            "redirect \"" + state.pending.text + "\" is missing a target file before \"" + m.kind.text + "\"");
                    if (m.kind == RedirectKind.ERR_MERGE) {
                        redirects.add(new Redirect(RedirectKind.ERR_MERGE, ""));
                    } else {
                        state.pending = m.kind;
                    }
                    i += m.len;
                    continue;
                }
                if (ch == '&') throw new UnsupportedSyntaxException(
                        "shell operator \"&\" is not supported — background runs need run_background, not run_command. "
                                + "Wrap a literal `&` arg in quotes.");
            }
            state.cur.append(ch); state.curHasContent = true; i++;
        }
        if (quote != null) throw new IllegalArgumentException("unclosed " + quote + " in command");
        flush(state);
        if (state.pending != null) throw new UnsupportedSyntaxException(
                "redirect \"" + state.pending.text + "\" is missing a target file");
        if (argv.isEmpty() && !redirects.isEmpty()) throw new UnsupportedSyntaxException(
                "redirect without a command — segment must have at least one program argument");
        validateRedirectFds(redirects);
        return new ChainSegment(argv, redirects);
    }

    private static class RedirectMatch { final RedirectKind kind; final int len;
        RedirectMatch(RedirectKind k, int l) { kind = k; len = l; } }

    private static RedirectMatch matchRedirect(String s) {
        if (s.startsWith("2>&1")) return new RedirectMatch(RedirectKind.ERR_MERGE, 4);
        if (s.startsWith("&>"))   return new RedirectMatch(RedirectKind.ALL, 2);
        if (s.startsWith("2>>"))  return new RedirectMatch(RedirectKind.ERR_APPEND, 3);
        if (s.startsWith("2>"))   return new RedirectMatch(RedirectKind.ERR_OUT, 2);
        if (s.startsWith(">>"))   return new RedirectMatch(RedirectKind.APPEND, 2);
        if (s.startsWith("<<"))   throw new UnsupportedSyntaxException(
                "shell operator \"<<\" is not supported — heredoc / here-string is not implemented; "
                        + "pass input via a \"<\" file or the binary's --input flag");
        if (s.startsWith(">"))    return new RedirectMatch(RedirectKind.OUT, 1);
        if (s.startsWith("<"))    return new RedirectMatch(RedirectKind.IN, 1);
        return null;
    }

    /** 每个 fd 最多一个重定向，避免冲突 */
    private static void validateRedirectFds(List<Redirect> redirects) {
        int stdin = 0, stdout = 0, stderr = 0;
        for (Redirect r : redirects) {
            switch (r.kind) {
                case IN -> stdin++;
                case OUT, APPEND -> stdout++;
                case ERR_OUT, ERR_APPEND, ERR_MERGE -> stderr++;
                case ALL -> { stdout++; stderr++; }
            }
        }
        if (stdin > 1) throw new UnsupportedSyntaxException("multiple `<` stdin redirects in one segment");
        if (stdout > 1) throw new UnsupportedSyntaxException("multiple stdout redirects in one segment");
        if (stderr > 1) throw new UnsupportedSyntaxException("multiple stderr redirects in one segment");
    }

    // ---- 工具方法 ----

    private static boolean isDqEscape(char prev, char next) {
        return prev == '\\' && (next == '"' || next == '\\');
    }

    // ---- 异常 ----

    public static class UnsupportedSyntaxException extends IllegalArgumentException {
        public UnsupportedSyntaxException(String detail) {
            super("run_command: " + detail);
        }
    }
}
