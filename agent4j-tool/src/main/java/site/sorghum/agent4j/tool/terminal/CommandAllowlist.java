package site.sorghum.agent4j.tool.terminal;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 命令白名单 —— 内置 70+ 条 allowlist + 风险参数降级 + 敏感路径检测。
 * <p>
 * 参考 Reasonix TS shell/parse.ts BUILTIN_ALLOWLIST + RISKY_ARGS + SENSITIVE_PATHS。
 * </p>
 */
public class CommandAllowlist {

    // ---- 内置白名单 ----

    private static final List<String> BUILTIN_ALLOWLIST = Arrays.asList(
            // 仓库审查
            "git status", "git diff", "git log", "git show", "git blame", "git branch",
            "git remote", "git rev-parse", "git config --get",
            // 文件系统审查
            "ls", "pwd", "cat", "head", "tail", "wc", "file", "tree", "find",
            "grep", "rg",
            // 语言版本探针
            "node --version", "node -v", "npm --version", "npx --version",
            "python --version", "python3 --version", "cargo --version",
            "go version", "rustc --version", "deno --version", "bun --version",
            // 测试运行器
            "npm test", "npm run test", "npx vitest run", "npx vitest", "npx jest",
            "pytest", "python -m pytest", "cargo test", "cargo check", "cargo clippy",
            "go test", "go vet", "deno test", "bun test",
            // Linters / typecheckers
            "npm run lint", "npm run typecheck", "npx tsc --noEmit",
            "npx biome check", "npx eslint", "npx prettier --check",
            "ruff", "mypy"
    );

    // ---- 风险参数 —— 命中则降级到确认门控 ----

    private static final Map<String, List<String>> RISKY_ARGS = new LinkedHashMap<>();
    private static final List<String> DEFAULT_SENSITIVE_PREFIXES = Arrays.asList(
            "~/.ssh", "~/.aws", "~/.gnupg", "~/.kube",
            "/etc/shadow", "/etc/sudoers"
    );

    // ---- 敏感路径前缀 ----
    private static final List<String> DEFAULT_SENSITIVE_PATTERNS = Arrays.asList(
            "*.env", "*.env.*", "*.key", "*.pem",
            "id_rsa*", "id_ed25519*", "*credentials*", "*secret*"
    );

    // ---- 敏感文件名模式 ----

    static {
        RISKY_ARGS.put("git branch", Arrays.asList(
                "-d", "-D", "--delete", "-m", "-M", "--move", "-c", "-C", "--copy", "--force"));
        RISKY_ARGS.put("git remote", Arrays.asList(
                "add", "remove", "rm", "rename", "set-url", "set-head", "prune"));
        RISKY_ARGS.put("git diff", Arrays.asList("--output", "--ext-diff"));
        RISKY_ARGS.put("git log", List.of("--output"));
        RISKY_ARGS.put("git show", List.of("--output"));
        RISKY_ARGS.put("find", Arrays.asList(
                "-delete", "-exec", "-execdir", "-ok", "-okdir",
                "-fprint", "-fprint0", "-fprintf", "-fls"));
        RISKY_ARGS.put("tree", List.of("-o"));
        RISKY_ARGS.put("npx eslint", Arrays.asList("--fix", "--fix-dry-run"));
        RISKY_ARGS.put("npx biome check", Arrays.asList("--write", "--apply", "--apply-unsafe"));
        RISKY_ARGS.put("ruff", Arrays.asList("--fix", "--unsafe-fixes", "format"));
    }

    // ======== 公共 API ========

    /**
     * 检查命令是否可以通过白名单（含风险参数降级和敏感路径检测）。
     *
     * @param cmd               原始命令字符串
     * @param extraAllowed      额外允许的前缀
     * @param projectRoot       项目根目录（用于敏感路径解析），null 则跳过路径检测
     * @param sensitivePrefixes 额外的敏感路径前缀
     * @param sensitivePatterns 额外的敏感文件名模式
     * @return true = 允许自动执行
     */
    public static boolean isAllowed(String cmd,
                                    List<String> extraAllowed,
                                    Path projectRoot,
                                    List<String> sensitivePrefixes,
                                    List<String> sensitivePatterns) {
        List<String> argv;
        try {
            argv = CommandTokenizer.tokenize(cmd);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (argv.isEmpty()) return false;

        List<String> allowlist = new ArrayList<>(BUILTIN_ALLOWLIST);
        if (extraAllowed != null) allowlist.addAll(extraAllowed);

        for (String prefix : allowlist) {
            List<String> prefixTokens = Arrays.asList(prefix.split(" "));
            if (argv.size() < prefixTokens.size()) continue;
            boolean match = true;
            for (int i = 0; i < prefixTokens.size(); i++) {
                if (!argv.get(i).equals(prefixTokens.get(i))) {
                    match = false;
                    break;
                }
            }
            if (!match) continue;

            // 风险参数检测
            List<String> risky = RISKY_ARGS.get(prefix);
            if (risky != null && tailHasRisky(argv.subList(prefixTokens.size(), argv.size()), risky))
                return false;

            // 敏感路径检测
            return projectRoot == null || !hasSensitivePathArgs(argv, projectRoot,
                    sensitivePrefixes != null ? sensitivePrefixes : Collections.emptyList(),
                    sensitivePatterns != null ? sensitivePatterns : Collections.emptyList());
        }
        return false;
    }

    /**
     * 仅检查命令行字符串句法是否合法（引号闭合等），不走白名单。
     */
    public static boolean isParsable(String cmd) {
        try {
            CommandTokenizer.tokenize(cmd);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // ---- 内部实现 ----

    private static boolean tailHasRisky(List<String> tail, List<String> risky) {
        for (String a : tail) {
            for (String r : risky) {
                if (a.equals(r)) return true;
                if (a.startsWith(r + "=")) return true;
            }
        }
        return false;
    }

    /**
     * 检查 argv 中的路径参数是否触碰敏感位置
     */
    static boolean hasSensitivePathArgs(List<String> argv, Path projectRoot,
                                        List<String> extraPrefixes,
                                        List<String> extraPatterns) {
        List<String> prefixes = new ArrayList<>(DEFAULT_SENSITIVE_PREFIXES);
        prefixes.addAll(extraPrefixes);
        List<String> patterns = new ArrayList<>(DEFAULT_SENSITIVE_PATTERNS);
        patterns.addAll(extraPatterns);

        String home = System.getProperty("user.home");
        for (String token : argv) {
            String resolved = resolveSensitivePath(token, projectRoot, home);
            if (resolved == null) continue;
            Path normalized = Paths.get(resolved).normalize();
            for (String pfx : prefixes) {
                Path pfxPath = expandPrefix(pfx, home).normalize();
                if (pathStartsWithPrefix(normalized, pfxPath)) return true;
            }
            String base = normalized.getFileName() != null ? normalized.getFileName().toString() : "";
            for (String pat : patterns) {
                if (matchesGlob(base, pat)) return true;
            }
        }
        return false;
    }

    private static String resolveSensitivePath(String token, Path projectRoot, String home) {
        if (token == null || token.isEmpty()) return null;
        if (token.startsWith("-") || token.contains("://") || token.startsWith("$")) return null;
        String expanded = token;
        if (expanded.startsWith("~") && home != null) {
            expanded = home + expanded.substring(1);
        }
        return projectRoot.resolve(expanded).normalize().toString();
    }

    private static Path expandPrefix(String prefix, String home) {
        if (prefix.startsWith("~") && home != null) {
            return Paths.get(home + prefix.substring(1)).normalize();
        }
        return Paths.get(prefix).normalize();
    }

    private static boolean pathStartsWithPrefix(Path normalized, Path prefix) {
        return normalized.equals(prefix) || normalized.startsWith(prefix);
    }

    private static boolean matchesGlob(String name, String pattern) {
        String regex = "^" + pattern
                .replace(".", "\\.")
                .replace("*", ".*")
                .replace("?", ".")
                + "$";
        return name.toLowerCase().matches("(?i)" + regex);
    }

    /**
     * 判断 /dev/null 或 Windows nul 等价物
     */
    public static boolean isNullDevice(String target) {
        if (target == null) return false;
        String lower = target.toLowerCase();
        if ("/dev/null".equals(lower)) return true;
        return System.getProperty("os.name", "").toLowerCase().contains("win")
                && "nul".equals(lower);
    }

    /**
     * 检查重定向目标是否在沙箱内
     */
    public static boolean redirectsEscapeSandbox(CommandChainParser.CommandChain chain, Path projectRoot) {
        Path root = projectRoot.normalize().toAbsolutePath();
        for (CommandChainParser.ChainSegment seg : chain.segments()) {
            for (CommandChainParser.Redirect r : seg.redirects()) {
                if (r.kind() == CommandChainParser.RedirectKind.ERR_MERGE) continue;
                if (r.target().isEmpty() || isNullDevice(r.target())) continue;
                Path resolved = root.resolve(r.target()).normalize();
                if (!resolved.startsWith(root)) return true;
            }
        }
        return false;
    }
}
