package site.sorghum.agent4j.tool.file;

import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.HitlRequiredException;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 文件工具——创建工作区内的文件和目录、移动、复制、删除、查看元信息。
 * <p>
 * 所有路径操作限制在工作区根目录内（路径穿越防护）。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class FileTool extends AgentTool {

    private static final String NAME = "file";

    private static final String DESCRIPTION =
            "文件系统操作工具。\n"
                    + "支持以下操作：\n"
                    + "  create_dir  — 创建目录（含父目录）\n"
                    + "  create_file — 创建/覆盖文件\n"
                    + "  delete_file — 删除文件\n"
                    + "  delete_dir  — 递归删除目录\n"
                    + "  move        — 移动/重命名\n"
                    + "  copy        — 复制文件或目录\n"
                    + "  stat        — 查看文件/目录元信息";

    private static final List<ToolParameter> PARAMETERS = Arrays.asList(
            new ToolParameter("action", "string", true,
                    "操作类型：create_dir / create_file / delete_file / delete_dir / move / copy / stat"),
            new ToolParameter("path", "string", true,
                    "目标路径（相对于工作区根目录），如 \"src/main/java/Foo.java\""),
            new ToolParameter("destination", "string", false,
                    "目标路径（move / copy 时必填）"),
            new ToolParameter("content", "string", false,
                    "文件内容（create_file 时使用，不传则创建空文件）")
    );

    @Override
    public String getName() { return NAME; }

    @Override
    public String getDescription() { return DESCRIPTION; }

    @Override
    public String toToolSpec() {
        return "### file\n\n"
                + "描述：工作区文件系统操作工具。\n\n"
                + "## 支持的操作类型\n\n"
                + "| 操作 | 说明 | 必填参数 |\n"
                + "|------|------|---------|\n"
                + "| create_dir | 创建目录（含父目录） | path |\n"
                + "| create_file | 创建/覆盖文件 | path, content? |\n"
                + "| delete_file | 删除文件 | path |\n"
                + "| delete_dir | 递归删除目录 | path |\n"
                + "| move | 移动/重命名 | path, destination |\n"
                + "| copy | 复制文件或目录 | path, destination |\n"
                + "| stat | 查看文件/目录元信息 | path |\n\n"
                + "## 注意事项\n\n"
                + "- 所有路径都是相对于工作区根目录的\n"
                + "- create_file 如果不传 content，会创建空文件\n"
                + "- delete_dir 会递归删除目录及其所有子文件和子目录\n\n"
                + "参数：\n"
                + "  - action (string, 必填): 操作类型\n"
                + "  - path (string, 必填): 目标路径\n"
                + "  - destination (string, 可选): 目标路径（move/copy 时必填）\n"
                + "  - content (string, 可选): 文件内容（create_file 时使用）\n\n"
                + "只读：否\n"
                + "风暴豁免：否";
    }

    @Override
    public List<ToolParameter> getParameters() { return PARAMETERS; }

    @Override
    public ToolResult execute(ToolContext ctx) {
        String action = ctx.getString("action");
        String path = ctx.getString("path");
        if (action == null || path == null) {
            return ToolResult.fail("MISSING_PARAM", "缺少必填参数 action 或 path");
        }

        Path root = ctx.getRootDir() != null ? ctx.getRootDir() : Paths.get(".").toAbsolutePath();

        try {
            // 检查路径是否被屏蔽
            Path pathResolved = root.resolve(path).toAbsolutePath().normalize();
            if (ctx.isPathBlocked(pathResolved)) {
                return ToolResult.fail("PATH_BLOCKED", "路径被屏蔽: " + path);
            }
            // 对 move/copy 操作，同时检查目标路径
            String destination = ctx.getString("destination");
            if (destination != null) {
                Path dstResolved = root.resolve(destination).toAbsolutePath().normalize();
                if (ctx.isPathBlocked(dstResolved)) {
                    return ToolResult.fail("PATH_BLOCKED", "目标路径被屏蔽: " + destination);
                }
            }

            return switch (action.toLowerCase()) {
                case "create_dir" -> doCreateDir(root, path);
                case "create_file" -> doCreateFile(root, path, ctx.getString("content", ""));
                case "delete_file" -> doDeleteFile(root, path);
                case "delete_dir" -> doDeleteDir(root, path);
                case "move" -> doMove(root, path, destination);
                case "copy" -> doCopy(root, path, destination);
                case "stat" -> doStat(root, path);
                default -> ToolResult.fail("UNKNOWN_ACTION",
                        "未知操作: " + action + "，支持: create_dir/create_file/delete_file/delete_dir/move/copy/stat");
            };
        } catch (IOException e) {
            return ToolResult.fail("IO_ERROR", e.getMessage());
        }
    }

    // ==== 内部操作 ====

    private Path resolveSafe(Path root, String rel) throws IOException {
        Path resolved = root.resolve(rel).toAbsolutePath().normalize();
        Path rootAbs = root.toAbsolutePath().normalize();
        if (!resolved.startsWith(rootAbs)) {
            if (ToolContext.isSandboxBypass()) {
                return resolved; // HITL 审批通过，跳过边界检查
            }
            throw new HitlRequiredException("file", "SANDBOX_ESCAPE",
                    "路径越界: " + rel, null);
        }
        return resolved;
    }

    private ToolResult doCreateDir(Path root, String path) throws IOException {
        Path dir = resolveSafe(root, path);
        Files.createDirectories(dir);
        return ToolResult.ok("已创建目录: " + path);
    }

    private ToolResult doCreateFile(Path root, String path, String content) throws IOException {
        Path file = resolveSafe(root, path);
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes());
        return ToolResult.ok("已创建文件: " + path + " (" + content.length() + " 字符)");
    }

    private ToolResult doDeleteFile(Path root, String path) throws IOException {
        Path file = resolveSafe(root, path);
        if (!Files.exists(file)) {
            return ToolResult.fail("NOT_FOUND", "文件不存在: " + path);
        }
        if (Files.isDirectory(file)) {
            return ToolResult.fail("IS_DIR", "路径是目录，请用 delete_dir: " + path);
        }
        // 删除文件强制触发 HITL 审批
        if (!ToolContext.isSandboxBypass()) {
            Map<String, Object> toolArgs = new HashMap<>();
            toolArgs.put("action", "delete_file");
            toolArgs.put("path", path);
            throw new HitlRequiredException("file", "DELETE_FILE",
                    "删除文件: " + path, toolArgs);
        }
        Files.delete(file);
        return ToolResult.ok("已删除: " + path);
    }

    private ToolResult doDeleteDir(Path root, String path) throws IOException {
        Path dir = resolveSafe(root, path);
        if (!Files.exists(dir)) {
            return ToolResult.fail("NOT_FOUND", "目录不存在: " + path);
        }
        if (!Files.isDirectory(dir)) {
            return ToolResult.fail("NOT_DIR", "路径不是目录: " + path);
        }
        // 删除目录强制触发 HITL 审批
        if (!ToolContext.isSandboxBypass()) {
            Map<String, Object> toolArgs = new HashMap<>();
            toolArgs.put("action", "delete_dir");
            toolArgs.put("path", path);
            throw new HitlRequiredException("file", "DELETE_DIR",
                    "递归删除目录: " + path, toolArgs);
        }
        // 递归删除
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
        }
        return ToolResult.ok("已递归删除: " + path);
    }

    private ToolResult doMove(Path root, String source, String dest) throws IOException {
        if (dest == null) {
            return ToolResult.fail("MISSING_DEST", "move 操作需要 destination 参数");
        }
        Path src = resolveSafe(root, source);
        Path dst = resolveSafe(root, dest);
        if (!Files.exists(src)) {
            return ToolResult.fail("NOT_FOUND", "源不存在: " + source);
        }
        Files.createDirectories(dst.getParent());
        Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
        return ToolResult.ok("已移动: " + source + " → " + dest);
    }

    private ToolResult doCopy(Path root, String source, String dest) throws IOException {
        if (dest == null) {
            return ToolResult.fail("MISSING_DEST", "copy 操作需要 destination 参数");
        }
        Path src = resolveSafe(root, source);
        Path dst = resolveSafe(root, dest);
        if (!Files.exists(src)) {
            return ToolResult.fail("NOT_FOUND", "源不存在: " + source);
        }
        Files.createDirectories(dst.getParent());
        if (Files.isDirectory(src)) {
            // 递归复制目录
            try (Stream<Path> walk = Files.walk(src)) {
                walk.forEach(s -> {
                    try {
                        Path d = dst.resolve(src.relativize(s));
                        if (Files.isDirectory(s)) {
                            Files.createDirectories(d);
                        } else {
                            Files.copy(s, d, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException ignored) {}
                });
            }
        } else {
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
        }
        return ToolResult.ok("已复制: " + source + " → " + dest);
    }

    private ToolResult doStat(Path root, String path) throws IOException {
        Path target = resolveSafe(root, path);
        if (!Files.exists(target)) {
            return ToolResult.fail("NOT_FOUND", "路径不存在: " + path);
        }
        BasicFileAttributes attr = Files.readAttributes(target, BasicFileAttributes.class);
        StringBuilder sb = new StringBuilder();
        sb.append(path).append("\n");
        sb.append("  类型: ").append(attr.isDirectory() ? "目录" : "文件").append("\n");
        sb.append("  大小: ").append(formatSize(attr.size())).append("\n");
        sb.append("  创建: ").append(attr.creationTime()).append("\n");
        sb.append("  修改: ").append(attr.lastModifiedTime()).append("\n");
        if (attr.isRegularFile()) {
            sb.append("  读写: ").append(Files.isReadable(target) ? "可读" : "-")
              .append(" / ").append(Files.isWritable(target) ? "可写" : "-");
        }
        return ToolResult.ok(sb.toString());
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
