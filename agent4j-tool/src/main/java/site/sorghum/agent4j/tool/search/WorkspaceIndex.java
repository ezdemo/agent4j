package site.sorghum.agent4j.tool.search;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 工作区文件索引——目录树缓存 + grep/glob/tree 的共享引擎。
 * <p>
 * 首次访问时全量扫描工作区目录，后续通过 mtime 增量刷新。
 * 自动跳过内置 denylist 中的目录，并读取 {@code .gitignore} 规则。
 * </p>
 *
 * <h3>典型用法：</h3>
 * <pre>{@code
 * WorkspaceIndex index = new WorkspaceIndex(Paths.get("/project"));
 * index.refresh();
 *
 * // 目录树
 * String tree = index.tree(3);
 *
 * // 文件名匹配
 * List<String> files = index.glob("**​/*.java");
 *
 * // 内容搜索
 * List<SearchMatch> matches = index.grep("Hashline", "*.java");
 * }</pre>
 *
 * @author Sorghum
 */
public class WorkspaceIndex {

    /**
     * 内置忽略目录（始终跳过，不进入扫描）
     */
    private static final Set<String> BUILTIN_DENY = new HashSet<>(Arrays.asList(
            ".git", ".svn", ".hg",
            "node_modules",
            "target", "build", "dist", "out",
            ".idea", ".vscode", ".eclipse",
            "__pycache__", ".mypy_cache", ".pytest_cache",
            ".next", ".nuxt", ".turbo", ".vercel",
            "coverage", ".cache",
            "venv", ".venv"
    ));

    /**
     * 二进制/大文件的典型扩展名（跳过 grep）
     */
    private static final Set<String> BINARY_EXTENSIONS = new HashSet<>(Arrays.asList(
            "jar", "war", "ear", "zip", "tar", "gz", "bz2", "7z", "rar",
            "png", "jpg", "jpeg", "gif", "bmp", "ico", "svg", "webp",
            "mp3", "mp4", "avi", "mov", "mkv", "wmv", "flv",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "exe", "dll", "so", "dylib", "bin",
            "class", "o", "obj", "a", "lib",
            "ttf", "otf", "woff", "woff2", "eot",
            "db", "sqlite", "sqlite3",
            "iso", "img", "dmg"
    ));

    /**
     * grep 最大文件大小（超过此值的文件跳过内容搜索）
     */
    private static final long MAX_GREP_FILE_SIZE = 2 * 1024 * 1024; // 2 MB

    /**
     * 单次 grep 最大返回匹配数
     */
    private static final int MAX_GREP_MATCHES = 500;

    /**
     * 工作区根目录
     */
    private final Path root;

    /**
     * 文件元数据缓存：相对路径 → FileMeta
     */
    private final Map<String, FileMeta> fileIndex = new LinkedHashMap<>();

    /**
     * .gitignore 中提取的 glob 模式（编译后）
     */
    private final List<Pattern> gitignorePatterns = new ArrayList<>();
    /**
     * 用户配置的屏蔽目录（相对路径）
     */
    private final List<String> blockedPaths;
    /**
     * 上次全量刷新时间戳
     */
    private long lastFullRefresh = 0;
    /**
     * 是否已执行过首次扫描
     */
    private boolean initialized = false;

    public WorkspaceIndex(Path root) {
        this(root, Collections.<String>emptyList());
    }

    public WorkspaceIndex(Path root, List<String> blockedPaths) {
        this.root = root.toAbsolutePath().normalize();
        this.blockedPaths = blockedPaths != null ? blockedPaths : Collections.<String>emptyList();
    }

    // ==================== 刷新 ====================

    /**
     * 简易 glob → regex 转换。
     * 支持 **, *, ?, {a,b}。
     */
    static Pattern globToRegex(String glob) {
        StringBuilder sb = new StringBuilder();
        sb.append('^');
        int i = 0;
        int len = glob.length();
        while (i < len) {
            char c = glob.charAt(i);
            if (c == '*') {
                if (i + 1 < len && glob.charAt(i + 1) == '*') {
                    // **
                    i += 2;
                    if (i < len && glob.charAt(i) == '/') {
                        i++;
                        sb.append("(?:.*/)?");  // **/ → 零或多个目录层级
                    } else {
                        sb.append(".*");        // ** 单独 → 任意字符
                    }
                } else {
                    // *
                    i++;
                    sb.append("[^/]*");
                }
            } else if (c == '?') {
                i++;
                sb.append("[^/]");
            } else if (c == '{') {
                // {a,b,c}
                int close = glob.indexOf('}', i);
                if (close > i) {
                    String inside = glob.substring(i + 1, close);
                    String[] options = inside.split(",");
                    sb.append("(?:");
                    for (int j = 0; j < options.length; j++) {
                        if (j > 0) sb.append('|');
                        sb.append(Pattern.quote(options[j].trim()));
                    }
                    sb.append(')');
                    i = close + 1;
                } else {
                    sb.append(Pattern.quote(String.valueOf(c)));
                    i++;
                }
            } else if (".()[]{}+|^$\\".indexOf(c) >= 0) {
                sb.append('\\').append(c);
                i++;
            } else {
                sb.append(c);
                i++;
            }
        }
        sb.append('$');
        return Pattern.compile(sb.toString());
    }

    /**
     * 构建/刷新索引。首次调用全量扫描，后续增量更新。
     */
    public synchronized void refresh() throws IOException {
        if (!Files.exists(root)) {
            throw new IOException("工作区目录不存在: " + root);
        }

        // 加载 .gitignore
        loadGitignore();

        if (!initialized) {
            fullScan();
            initialized = true;
        } else {
            incrementalRefresh();
        }
        lastFullRefresh = System.currentTimeMillis();
    }

    // ==================== 目录树 ====================

    /**
     * 确保索引已初始化（懒加载）。
     */
    private synchronized void ensureInitialized() throws IOException {
        if (!initialized) {
            refresh();
        }
    }

    // ==================== glob（文件名匹配） ====================

    /**
     * 生成目录树字符串。
     *
     * @param maxDepth 最大递归深度，0 表示仅根目录
     * @return 缩进树格式的字符串
     */
    public String tree(int maxDepth) throws IOException {
        ensureInitialized();

        // 构建树节点
        TreeNode rootNode = new TreeNode(root.getFileName().toString(), true);
        for (Map.Entry<String, FileMeta> entry : fileIndex.entrySet()) {
            String path = entry.getKey();
            boolean isDir = entry.getValue().isDirectory();
            String[] parts = path.split("/");
            TreeNode current = rootNode;
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                boolean partIsDir = (i < parts.length - 1) || isDir;
                String key = partIsDir ? part + "/" : part;
                TreeNode child = current.children.get(key);
                if (child == null) {
                    child = new TreeNode(part, partIsDir);
                    current.children.put(key, child);
                }
                current = child;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(rootNode.name).append("/\n");
        if (maxDepth > 0) {
            renderNode(sb, rootNode, "", 0, maxDepth);
        }
        return sb.toString();
    }

    /**
     * 按 glob 模式匹配文件路径。
     * <p>
     * 支持：{@code *}（单层通配）、{@code **}（多层通配）、{@code ?}（单字符）、
     * {@code {a,b}}（分支选择）。
     * </p>
     *
     * @param glob glob 模式，如 "src/**​/*.java"
     * @return 匹配的文件相对路径列表（按修改时间倒序）
     */
    public List<String> glob(String glob) throws IOException {
        ensureInitialized();

        Pattern regex = globToRegex(glob);
        List<String> results = new ArrayList<>();
        for (String path : fileIndex.keySet()) {
            FileMeta meta = fileIndex.get(path);
            if (!meta.isDirectory() && regex.matcher(path).matches()) {
                results.add(path);
            }
        }
        // 按 mtime 倒序
        results.sort((a, b) -> Long.compare(
                fileIndex.get(b).getLastModified(),
                fileIndex.get(a).getLastModified()));
        return results;
    }

    /**
     * 按 glob 模式匹配，同时支持目录项。
     *
     * @param glob        glob 模式
     * @param includeDirs 是否包含目录
     * @return 匹配的路径列表
     */
    public List<String> glob(String glob, boolean includeDirs) throws IOException {
        ensureInitialized();

        Pattern regex = globToRegex(glob);
        List<String> results = new ArrayList<>();
        for (Map.Entry<String, FileMeta> entry : fileIndex.entrySet()) {
            if (regex.matcher(entry.getKey()).matches()) {
                FileMeta meta = entry.getValue();
                if (!meta.isDirectory() || includeDirs) {
                    results.add(entry.getKey());
                }
            }
        }
        results.sort((a, b) -> Long.compare(
                fileIndex.get(b).getLastModified(),
                fileIndex.get(a).getLastModified()));
        return results;
    }

    // ==================== grep（内容搜索） ====================

    /**
     * @return 匹配的文件数量
     */
    public int globCount(String glob) throws IOException {
        return glob(glob).size();
    }

    /**
     * 在文本文件中搜索匹配指定正则的内容行。
     *
     * @param pattern  正则表达式
     * @param fileGlob 文件过滤 glob（如 "*.java"），null 或 "*" 表示所有文本文件
     * @return 匹配列表（上限 {@value #MAX_GREP_MATCHES}）
     */
    public List<SearchMatch> grep(String pattern, String fileGlob) throws IOException {
        ensureInitialized();

        Pattern contentPattern;
        try {
            contentPattern = Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            throw new IOException("无效正则表达式: " + pattern, e);
        }

        Pattern fileFilter = (fileGlob != null && !fileGlob.equals("*"))
                ? globToRegex(fileGlob) : null;

        List<SearchMatch> results = new ArrayList<>();
        for (Map.Entry<String, FileMeta> entry : fileIndex.entrySet()) {
            if (results.size() >= MAX_GREP_MATCHES) break;

            FileMeta meta = entry.getValue();
            if (meta.isDirectory() || !meta.isTextFile()) continue;
            if (meta.getSize() > MAX_GREP_FILE_SIZE) continue;
            if (fileFilter != null && !fileFilter.matcher(entry.getKey()).matches()) continue;

            Path absPath = root.resolve(entry.getKey());
            try {
                List<String> lines = Files.readAllLines(absPath);
                for (int i = 0; i < lines.size(); i++) {
                    if (results.size() >= MAX_GREP_MATCHES) break;
                    String line = lines.get(i);
                    if (contentPattern.matcher(line).find()) {
                        results.add(new SearchMatch(entry.getKey(), i + 1, line));
                    }
                }
            } catch (IOException ignored) {
                // 文件可能在被读取时被删除，跳过
            }
        }
        return results;
    }

    // ==================== 统计 ====================

    /**
     * 搜索所有文本文件（不限 glob）。
     */
    public List<SearchMatch> grep(String pattern) throws IOException {
        return grep(pattern, null);
    }

    /**
     * 索引中文件总数（不含目录）。
     */
    public int fileCount() throws IOException {
        ensureInitialized();
        return (int) fileIndex.values().stream().filter(m -> !m.isDirectory()).count();
    }

    /**
     * 索引中总文件大小（字节）。
     */
    public long totalSize() throws IOException {
        ensureInitialized();
        return fileIndex.values().stream()
                .filter(m -> !m.isDirectory())
                .mapToLong(FileMeta::getSize)
                .sum();
    }

    /**
     * 工作区根目录。
     */
    public Path getRoot() {
        return root;
    }

    // ==================== 内部：扫描 ====================

    /**
     * 是否已初始化。
     */
    public boolean isInitialized() {
        return initialized;
    }

    private void fullScan() throws IOException {
        fileIndex.clear();
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String name = dir.getFileName().toString();
                if (BUILTIN_DENY.contains(name) || isGitignored(dir) || isBlockedDir(dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                String rel = relativize(dir);
                if (!rel.isEmpty()) {
                    fileIndex.put(rel + "/", new FileMeta(rel + "/", 0,
                            attrs.lastModifiedTime().toMillis(), true, false));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (isGitignored(file)) {
                    return FileVisitResult.CONTINUE;
                }
                String rel = relativize(file);
                String ext = getExtension(rel).toLowerCase();
                boolean isText = !BINARY_EXTENSIONS.contains(ext);
                fileIndex.put(rel, new FileMeta(rel, attrs.size(),
                        attrs.lastModifiedTime().toMillis(), false, isText));
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // ==================== 内部：gitignore ====================

    private void incrementalRefresh() throws IOException {
        // 全量重建：简洁可靠，避免增量扫描的边界问题
        // 对于大多数项目（<10k文件），全量扫描耗时 <100ms
        fullScan();
    }

    private void loadGitignore() throws IOException {
        gitignorePatterns.clear();
        Path gitignoreFile = root.resolve(".gitignore");
        if (!Files.exists(gitignoreFile)) return;

        List<String> lines = Files.readAllLines(gitignoreFile);
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            // 移除前导 /
            if (trimmed.startsWith("/")) trimmed = trimmed.substring(1);
            // 移除尾随 /
            if (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
            if (trimmed.isEmpty()) continue;
            try {
                gitignorePatterns.add(globToRegex(trimmed));
            } catch (Exception ignored) {
                // 不支持的 pattern 跳过
            }
        }
    }

    private boolean isGitignored(Path absPath) {
        String rel = relativize(absPath);
        for (Pattern p : gitignorePatterns) {
            if (p.matcher(rel).matches()) return true;
        }
        return false;
    }

    // ==================== 内部：辅助 ====================

    /**
     * 检查目录是否在用户配置的屏蔽目录列表中
     */
    private boolean isBlockedDir(Path dir) {
        if (blockedPaths.isEmpty()) return false;
        String rel = relativize(dir);
        for (String blocked : blockedPaths) {
            String normalized = blocked.replace('\\', '/');
            if (rel.equals(normalized) || rel.startsWith(normalized + "/")) {
                return true;
            }
        }
        return false;
    }

    private String relativize(Path absPath) {
        String s = root.relativize(absPath).toString().replace('\\', '/');
        if (s.startsWith("/")) s = s.substring(1);
        return s;
    }

    private String getExtension(String path) {
        int dot = path.lastIndexOf('.');
        if (dot < 0) return "";
        // 排除路径分隔符后的点
        int sep = path.lastIndexOf('/');
        if (dot < sep) return "";
        return path.substring(dot + 1);
    }

    private void renderNode(StringBuilder sb, TreeNode node, String prefix,
                            int depth, int maxDepth) {
        if (depth > maxDepth) return;

        List<Map.Entry<String, TreeNode>> sorted = new ArrayList<>(node.children.entrySet());
        sorted.sort((a, b) -> {
            // 目录优先
            if (a.getValue().isDir != b.getValue().isDir) {
                return a.getValue().isDir ? -1 : 1;
            }
            return a.getKey().compareTo(b.getKey());
        });

        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<String, TreeNode> entry = sorted.get(i);
            boolean last = (i == sorted.size() - 1);
            TreeNode child = entry.getValue();

            sb.append(prefix);
            sb.append(last ? "└── " : "├── ");
            sb.append(child.isDir ? child.name + "/" : child.name);
            sb.append("\n");

            if (child.isDir) {
                String childPrefix = prefix + (last ? "    " : "│   ");
                renderNode(sb, child, childPrefix, depth + 1, maxDepth);
            }
        }
    }

    private static class TreeNode {
        final String name;
        final boolean isDir;
        final Map<String, TreeNode> children = new TreeMap<>();

        TreeNode(String name, boolean isDir) {
            this.name = name;
            this.isDir = isDir;
        }
    }
}
