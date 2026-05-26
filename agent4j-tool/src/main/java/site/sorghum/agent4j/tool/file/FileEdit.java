package site.sorghum.agent4j.tool.file;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Stream;

/**
 * 文件读写编辑工具 —— 纯 SEARCH/REPLACE。
 * <p>
 * 不再使用 LINE#HASH 注解。编辑时模型直接提供要搜的文本原文。
 * </p>
 *
 * @author Sorghum
 */
public class FileEdit {

    private static final int HARD_MAX_FILE_BYTES = 32 * 1024 * 1024;
    /** 全量内容返回的最大文件大小（64KB） */
    private static final long OUTLINE_THRESHOLD = 64 * 1024;
    /** Outline 模式下显示的前 N 行 */
    private static final int OUTLINE_HEAD_LINES = 80;
    /** Outline 模式下的符号大纲最大行数 */
    private static final int OUTLINE_MAX_SYMBOLS = 30;

    /** read_file 实现 */
    public static String readFile(Path root, String pathStr, Integer head, Integer tail, String range)
            throws IOException {
        Path abs = resolveSafe(root, pathStr);
        if (!Files.exists(abs)) return "[NOT_FOUND] 文件不存在: " + pathStr;

        // 子目录 AGENT4J.md 注入
        String subdirMemory = findSubdirMemory(root, abs);
        String prefix = subdirMemory != null ? subdirMemory + "\n\n" : "";
        if (Files.isDirectory(abs)) return "[IS_DIR] 路径是目录: " + pathStr;

        long size = Files.size(abs);
        if (size > HARD_MAX_FILE_BYTES) {
            return "[REFUSED] 文件过大 (" + size + " 字节)，请用 head/tail/range 分段读取";
        }

        byte[] raw = Files.readAllBytes(abs);
        // 检查是否二进制
        for (int i = 0; i < Math.min(raw.length, 8192); i++) {
            if (raw[i] == 0) return "[REFUSED] 二进制文件";
        }

        String text = new String(raw, StandardCharsets.UTF_8);
        List<String> lines = new ArrayList<>(Arrays.asList(text.split("\n", -1)));
        if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines = lines.subList(0, lines.size() - 1);
        }
        int total = lines.size();

        // 显式 range / head / tail → 返回指定片段
        if (range != null && range.matches("\\d+\\s*-\\s*\\d+")) {
            String[] parts = range.split("-");
            int start = Math.max(1, Integer.parseInt(parts[0].trim()));
            int end = Math.min(total, Math.max(start, Integer.parseInt(parts[1].trim())));
            String body = joinLines(lines.subList(start - 1, end));
            return "[range " + start + "-" + end + " of " + total + " lines]\n" + body;
        }
        if (head != null && head > 0) {
            int n = Math.min(head, total);
            String body = joinLines(lines.subList(0, n));
            return n < total
                    ? body + "\n\n[… " + n + "/" + total + " lines — call with range/tail for more]"
                    : body;
        }
        if (tail != null && tail > 0) {
            int n = Math.min(tail, total);
            String body = joinLines(lines.subList(total - n, total));
            return n < total
                    ? "[… " + (total - n) + " earlier lines …]\n\n" + body
                    : body;
        }

        // 无显式 scope，按大小决定策略
        if (size <= OUTLINE_THRESHOLD) {
            // 小文件 → 全量内容
            return prefix + joinLines(lines);
        }

        // 大文件 → outline 模式（head + 符号结构 + 导航提示）
        String headBlock = joinLines(lines.subList(0, Math.min(OUTLINE_HEAD_LINES, total)));
        String outline = extractSimpleOutline(pathStr, lines);
        StringBuilder sb = new StringBuilder();
        sb.append(prefix);
        sb.append("[large file: ").append(size).append(" bytes, ").append(total).append(" lines — outline mode (threshold ").append(OUTLINE_THRESHOLD / 1024).append(" KiB)]\n\n");
        sb.append("[head ").append(Math.min(OUTLINE_HEAD_LINES, total)).append(" lines for orientation]\n");
        sb.append(headBlock).append("\n");
        if (!outline.isEmpty()) {
            sb.append("\n").append(outline).append("\n");
        }
        sb.append("\n[to read more, call one of:\n");
        sb.append("  - read_file path:\"").append(pathStr).append("\" range:\"A-B\"          — 1-indexed line range\n");
        sb.append("  - read_file path:\"").append(pathStr).append("\" head:N  /  tail:N    — first/last N lines\n");
        sb.append("  - grep pattern:\"...\" path:\"").append(pathStr).append("\"     — grep within this file]");
        return sb.toString();
    }

    /** edit_file: SEARCH → REPLACE，search 必须唯一 */
    public static String editFile(Path root, String pathStr, String search, String replace)
            throws IOException {
        if (search == null || search.isEmpty()) throw new IOException("edit_file: search 不能为空");
        Path abs = resolveSafe(root, pathStr);
        String before = new String(Files.readAllBytes(abs), StandardCharsets.UTF_8);

        // 统一换行符
        String le = before.contains("\r\n") ? "\r\n" : "\n";
        String adaptedSearch = search.replace("\r\n", "\n").replace("\n", le);
        String adaptedReplace = replace.replace("\r\n", "\n").replace("\n", le);

        int firstIdx = before.indexOf(adaptedSearch);
        if (firstIdx < 0) throw new IOException("edit_file: 未找到搜索文本");
        int nextIdx = before.indexOf(adaptedSearch, firstIdx + 1);
        if (nextIdx >= 0) throw new IOException("edit_file: 搜索文本出现多次，请包含更多上下文以消除歧义");

        String after = before.substring(0, firstIdx) + adaptedReplace + before.substring(firstIdx + adaptedSearch.length());
        Files.write(abs, after.getBytes(StandardCharsets.UTF_8));

        // diff 摘要
        int sLines = adaptedSearch.split(le, -1).length;
        int rLines = adaptedReplace.split(le, -1).length;
        int startLine = before.substring(0, firstIdx).split(le, -1).length;
        return "edited " + pathStr + " (" + adaptedSearch.length() + "→" + adaptedReplace.length() + " chars)\n"
                + "@@ -" + startLine + "," + sLines + " +" + startLine + "," + rLines + " @@\n"
                + "- " + truncateLine(firstLine(adaptedSearch))
                + "+ " + truncateLine(firstLine(adaptedReplace));
    }

    /** multi_edit: 跨文件批量编辑，全验证后全写入，失败回滚 */
    public static String multiEdit(Path root, List<Map<String, Object>> edits) throws IOException {
        if (edits == null || edits.isEmpty()) throw new IOException("multi_edit: edits 不能为空");
        // 记录每文件的编辑前内容供回滚
        List<Path> writtenFiles = new ArrayList<>();
        List<String> rollbackContents = new ArrayList<>();
        int totalDelta = 0;
        int applied = 0;

        try {
            for (int i = 0; i < edits.size(); i++) {
                Map<String, Object> ed = edits.get(i);
                String epath = str(ed, "path");
                String search = str(ed, "search");
                String replace = str(ed, "replace");

                Path abs = resolveSafe(root, epath);
                byte[] raw = Files.readAllBytes(abs);
                String before = new String(raw, StandardCharsets.UTF_8);
                String le = before.contains("\r\n") ? "\r\n" : "\n";
                String sa = search.replace("\r\n", "\n").replace("\n", le);
                String ra = replace.replace("\r\n", "\n").replace("\n", le);

                int fi = before.indexOf(sa);
                if (fi < 0) throw new IOException("multi_edit #" + (i + 1) + " (" + epath + "): 未找到搜索文本");
                int ni = before.indexOf(sa, fi + 1);
                if (ni >= 0) throw new IOException("multi_edit #" + (i + 1) + " (" + epath + "): 搜索文本出现多次");

                String after = before.substring(0, fi) + ra + before.substring(fi + sa.length());
                totalDelta += ra.length() - sa.length();

                writtenFiles.add(abs);
                rollbackContents.add(before);
                Files.write(abs, after.getBytes(StandardCharsets.UTF_8));
                applied++;
            }
            String sign = totalDelta >= 0 ? "+" : "";
            return "multi_edit: applied " + applied + " edits (" + sign + totalDelta + " chars)";
        } catch (IOException e) {
            // 回滚
            for (int i = writtenFiles.size() - 1; i >= 0; i--) {
                try { Files.write(writtenFiles.get(i), rollbackContents.get(i).getBytes(StandardCharsets.UTF_8)); } catch (IOException ignored) {}
            }
            throw new IOException("multi_edit failed after " + applied + " edits; rolled back. " + e.getMessage());
        }
    }

    /** write_file: 创建/覆盖 */
    public static String writeFile(Path root, String pathStr, String content) throws IOException {
        Path abs = resolveSafe(root, pathStr);
        Files.createDirectories(abs.getParent());
        Files.write(abs, content.getBytes(StandardCharsets.UTF_8));
        return "wrote " + content.length() + " chars to " + pathStr;
    }

    /** get_file_info: 文件/目录/符号链接元信息 */
    public static String getFileInfo(Path root, String pathStr) throws IOException {
        Path abs = resolveSafe(root, pathStr);
        if (!Files.exists(abs)) return "{\"error\":\"path not found\"}";
        BasicFileAttributes attr = Files.readAttributes(abs, BasicFileAttributes.class);
        String type = attr.isDirectory() ? "directory" : attr.isSymbolicLink() ? "symlink" : "file";
        return "{\"type\":\"" + type + "\",\"size\":" + attr.size()
                + ",\"mtime\":\"" + attr.lastModifiedTime().toString() + "\"}";
    }

    /** copy_file: 复制文件或目录 */
    public static String copyFile(Path root, String srcStr, String dstStr) throws IOException {
        Path src = resolveSafe(root, srcStr);
        Path dst = resolveSafe(root, dstStr);
        if (!Files.exists(src)) throw new IOException("源不存在: " + srcStr);
        Files.createDirectories(dst.getParent());
        if (Files.isDirectory(src)) {
            try (java.util.stream.Stream<Path> walk = java.nio.file.Files.walk(src)) {
                walk.forEach(s -> {
                    try {
                        Path d = dst.resolve(src.relativize(s));
                        if (Files.isDirectory(s)) Files.createDirectories(d);
                        else Files.copy(s, d, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException ignored) {}
                });
            }
        } else {
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
        }
        return "copied " + srcStr + " → " + dstStr;
    }

    /** 查找子目录 AGENT4J.md */
    private static String findSubdirMemory(Path root, Path abs) {
        Path dir = abs.getParent();
        if (dir == null) return null;
        try {
            // 从当前目录向上查找最近的 AGENT4J.md
            while (dir != null && dir.startsWith(root)) {
                Path memFile = dir.resolve("AGENT4J.md");
                if (Files.exists(memFile)) {
                    String content = new String(Files.readAllBytes(memFile), java.nio.charset.StandardCharsets.UTF_8);
                    return "[来自 " + root.relativize(memFile.getParent()).toString().replace('\\', '/')
                            + "/AGENT4J.md 的上下文]\n" + content.trim();
                }
                dir = dir.getParent();
            }
        } catch (IOException ignored) {}
        return null;
    }

    /** 简单符号大纲提取 */
    private static String extractSimpleOutline(String pathStr, List<String> lines) {
        List<String> symbols = new ArrayList<>();
        int total = lines.size();

        // Java 类/接口/枚举/注解/记录
        java.util.regex.Pattern classPat = java.util.regex.Pattern.compile(
                "^(?:public\\s+|private\\s+|protected\\s+|static\\s+|abstract\\s+|final\\s+|sealed\\s+|non-sealed\\s+)*(?:class|interface|enum|@interface|record)\\s+(\\w+)");
        // 方法
        java.util.regex.Pattern methodPat = java.util.regex.Pattern.compile(
                "^(?:\\s{0,3}(?:public|private|protected|static|abstract|final|synchronized|native)\\s+)+(?:<[^>]+>\\s+)?(?:\\w+(?:<[^>]+>)?\\s+)?(\\w+)\\s*\\([^)]*\\)\\s*(?:throws\\s+[^{]+)?\\s*(?:\\{|;)");
        // HTML id = / class = 用作锚点
        java.util.regex.Pattern idPat = java.util.regex.Pattern.compile(
                "id=\\\"?([a-zA-Z_]\\w*)\\\"");

        for (int i = 0; i < total && symbols.size() < OUTLINE_MAX_SYMBOLS; i++) {
            String line = lines.get(i);
            java.util.regex.Matcher cm = classPat.matcher(line);
            if (cm.find()) {
                symbols.add("  L" + (i + 1) + "  class " + cm.group(1));
                continue;
            }
            java.util.regex.Matcher mm = methodPat.matcher(line);
            if (mm.find()) {
                symbols.add("  L" + (i + 1) + "  method " + mm.group(1) + "()");
                continue;
            }
            java.util.regex.Matcher im = idPat.matcher(line);
            if (im.find()) {
                symbols.add("  L" + (i + 1) + "  id=" + im.group(1));
                continue;
            }
        }
        if (symbols.isEmpty()) return "";
        return "[outline: " + symbols.size() + " symbols]\n" + String.join("\n", symbols);
    }

    // ---- 内部辅助 ----

    private static Path resolveSafe(Path root, String raw) throws IOException {
        Path normalized = root.resolve(raw).toAbsolutePath().normalize();
        Path rootAbs = root.toAbsolutePath().normalize();
        if (!normalized.startsWith(rootAbs))
            throw new IOException("路径越界: " + raw);
        return normalized;
    }

    private static String joinLines(List<String> lines) {
        return String.join("\n", lines);
    }

    private static String firstLine(String s) {
        int idx = s.indexOf('\n');
        return idx >= 0 ? s.substring(0, idx) : s;
    }

    private static String truncateLine(String s) {
        if (s == null) return "";
        return s.length() > 80 ? s.substring(0, 80) + "…" : s;
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : null;
    }
}
