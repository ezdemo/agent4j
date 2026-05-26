package site.sorghum.agent4j.tool.file;

import org.noear.solon.annotation.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Stream;

/**
 * 文件系统服务 —— 目录列表、文件名搜索、路径解析等文件操作。
 *
 * @author Sorghum
 */
@Component
public class FileSystemService {

    /**
     * 列出指定目录的内容。
     *
     * @param root    工作区根目录
     * @param pathStr 相对路径，null 或空表示根目录
     * @return 目录项列表（目录以 / 结尾）
     */
    public String listDirectory(Path root, String pathStr) throws IOException {
        Path abs = resolveSafe(root, pathStr != null ? pathStr : ".");
        if (!Files.isDirectory(abs)) return "[NOT_DIR] " + pathStr;
        List<String> lines = new ArrayList<>();
        try (Stream<Path> stream = Files.list(abs)) {
            stream.sorted().forEach(p -> {
                lines.add(Files.isDirectory(p) ? p.getFileName().toString() + "/"
                        : p.getFileName().toString());
            });
        }
        return lines.isEmpty() ? "(empty directory)" : String.join("\n", lines);
    }

    /**
     * 按文件名模式搜索文件（大小写不敏感）。
     *
     * @param root       工作区根目录
     * @param pathStr    起始路径
     * @param pattern    文件名包含模式
     * @param includeDeps 是否包含依赖目录（node_modules 等）
     * @return 匹配的文件路径列表
     */
    public String searchFiles(Path root, String pathStr, String pattern, Boolean includeDeps)
            throws IOException {
        Path start = pathStr != null ? resolveSafe(root, pathStr) : root;
        if (!Files.isDirectory(start)) return "[NOT_DIR]";
        Set<String> skip = includeDeps == Boolean.TRUE ? Collections.<String>emptySet()
                : new HashSet<>(Arrays.asList("node_modules", ".git", "target", "dist", "build",
                        ".venv", "__pycache__"));
        String lower = pattern != null ? pattern.toLowerCase() : "";
        List<String> results = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(start)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> !pathContainsSkip(p, skip))
                    .filter(p -> p.getFileName().toString().toLowerCase().contains(lower))
                    .forEach(p -> results.add(displayRel(root, p)));
        }
        return results.isEmpty() ? "(no matches)" : String.join("\n", results);
    }

    /**
     * 安全解析路径，防止路径穿越攻击。
     * 确保解析后的路径仍处于工作区根目录内。
     */
    public Path resolveSafe(Path root, String raw) throws IOException {
        if (raw == null || raw.isEmpty()) return root;
        Path resolved = root.resolve(raw).toAbsolutePath().normalize();
        if (!resolved.startsWith(root.toAbsolutePath().normalize()))
            throw new IOException("路径越界: " + raw);
        return resolved;
    }

    /**
     * 将绝对路径转换为相对于工作区根目录的显示路径。
     */
    public String displayRel(Path root, Path abs) {
        return root.relativize(abs).toString().replace('\\', '/');
    }

    /**
     * 读取输入流全部内容为 UTF-8 字符串。
     * 使用 8KB 缓冲，适用于 HTTP 响应等流式读取。
     */
    public static String readFully(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) {
            sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    /** 检查路径中是否包含跳过目录。 */
    private static boolean pathContainsSkip(Path p, Set<String> skip) {
        for (int i = 0; i < p.getNameCount(); i++) {
            if (skip.contains(p.getName(i).toString())) return true;
        }
        return false;
    }
}
