package site.sorghum.agent4j.tool.file;

import site.sorghum.agent4j.tool.HitlRequiredException;
import site.sorghum.agent4j.tool.ToolContext;

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

    /** 最大文件大小限制（100 MiB）— 超过此大小才拒绝读取 */
    private static final int HARD_MAX_FILE_BYTES = 100 * 1024 * 1024;

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
            // range 范围必须 >= 500 行
            if (end != total && end - start + 1 < 500) {
                return "[ERROR] range 范围必须 >= 500 行（当前: " + (end - start + 1) + " 行）。请使用更大的范围，如 " + start + "-" + (start + 500);
            }
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

        // 全量返回文件内容
        return prefix + joinLines(lines);
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



    // ---- 内部辅助 ----

    private static Path resolveSafe(Path root, String raw) throws IOException {
        return resolveSafe(root, raw, null);
    }

    /**
     * 解析路径并检查是否在屏蔽目录中。
     *
     * @param root         工作区根目录
     * @param raw          相对路径
     * @param blockedPaths 屏蔽目录列表（相对路径），null 或空列表时不检查
     * @return 已解析的绝对路径
     * @throws IOException 路径越界或路径被屏蔽时抛出
     */
    private static Path resolveSafe(Path root, String raw, List<String> blockedPaths) throws IOException {
        Path normalized = root.resolve(raw).toAbsolutePath().normalize();
        Path rootAbs = root.toAbsolutePath().normalize();
        if (!normalized.startsWith(rootAbs)) {
            if (ToolContext.isSandboxBypass()) {
                return normalized; // HITL 审批通过，跳过边界检查
            }
            throw new HitlRequiredException("file", "SANDBOX_ESCAPE",
                    "路径越界: " + raw, null);
        }
        // 检查屏蔽目录
        if (blockedPaths != null && !blockedPaths.isEmpty()) {
            for (String blocked : blockedPaths) {
                Path blockedAbs = rootAbs.resolve(blocked).normalize();
                if (normalized.equals(blockedAbs) || normalized.startsWith(blockedAbs)) {
                    throw new IOException("路径被屏蔽: " + raw + " (匹配屏蔽目录: " + blocked + ")");
                }
            }
        }
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
