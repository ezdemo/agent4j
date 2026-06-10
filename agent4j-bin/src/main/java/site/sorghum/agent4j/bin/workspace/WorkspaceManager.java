package site.sorghum.agent4j.bin.workspace;

import lombok.Getter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.SneakyThrows;
import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.bin.goal.GoalStore;
import site.sorghum.agent4j.bin.goal.JsonlGoalStore;

/**
 * 工作区管理器 —— 管理多个工作区的生命周期。
 * <p>
 * 工作区存储结构：
 * ~/.agent4j/workspace/{hash}/
 * ├── workspace.json    (工作区配置)
 * └── sessions/         (会话目录)
 * ├── session1.jsonl
 * └── session2.jsonl
 * </p>
 * <p>
 * hash 是工作目录完整路径的 MD5 哈希值（前12位）。
 * </p>
 *
 * @author Sorghum
 */
@Getter
public class WorkspaceManager {

    private static final Path WORKSPACES_DIR = Paths.get(
            System.getProperty("user.home"), ".agent4j", "workspace");


    private static final Map<String, WorkspaceManager> WORKSPACE_MANAGERS = new ConcurrentHashMap<>();

    /**
     * 当前工作区路径（工作目录的实际路径）
     */
    private String currentWorkspacePath;


    public WorkspaceManager() {
        try {
            Files.createDirectories(WORKSPACES_DIR);
        } catch (IOException e) {
            System.err.println("[workspace] 创建工作区目录失败: " + e.getMessage());
        }
    }

    /**
     * 计算工作目录的 hash 值（MD5 前12位）
     */
    public static String computeHash(String workspacePath) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(workspacePath.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // Fallback: 使用简单的 hash
            return String.format("%012x", workspacePath.hashCode() & 0xFFFFFFFFFFL);
        }
    }

    /**
     * 获取工作区目录路径
     */
    public Path getWorkspaceDir(String workspacePath) {
        String hash = computeHash(workspacePath);
        return WORKSPACES_DIR.resolve(hash);
    }

    /**
     * 获取工作区配置文件路径
     */
    public Path getWorkspaceConfigPath(String workspacePath) {
        return getWorkspaceDir(workspacePath).resolve("workspace.json");
    }

    /**
     * 获取工作区会话目录路径
     */
    public Path getSessionsDir(String workspacePath) {
        return getWorkspaceDir(workspacePath).resolve("sessions");
    }

    /**
     * 获取工作区的目标存储。
     *
     * @throws IllegalStateException 如果工作区未初始化
     */
    public GoalStore getGoalStore() {
        if (currentWorkspacePath == null) {
            throw new IllegalStateException("工作区未初始化，请先初始化工作区");
        }
        Path workspaceDir = getWorkspaceDir(currentWorkspacePath);
        return new JsonlGoalStore(workspaceDir);
    }

    /**
     * 获取或创建工作区管理器
     */
    public static WorkspaceManager getOrCreate(String workspacePath){
        if (WORKSPACE_MANAGERS.containsKey(workspacePath)){
            return WORKSPACE_MANAGERS.get(workspacePath);
        }
        WorkspaceManager manager = new WorkspaceManager();
        manager.initWorkspace(workspacePath);
        WORKSPACE_MANAGERS.put(workspacePath, manager);
        return manager;
    }
    /**
     * 初始化或加载工作区
     */
    @SneakyThrows
    public void initWorkspace(String workspacePath){
        this.currentWorkspacePath = workspacePath;

        Path workspaceDir = getWorkspaceDir(workspacePath);
        Path sessionsDir = getSessionsDir(workspacePath);
        Path configPath = getWorkspaceConfigPath(workspacePath);

        // 创建目录结构
        Files.createDirectories(workspaceDir);
        Files.createDirectories(sessionsDir);

        // 如果配置文件不存在，创建默认配置
        if (!Files.exists(configPath)) {
            createDefaultConfig(workspacePath, configPath);
        }
    }

    /**
     * 创建默认的工作区配置
     */
    private void createDefaultConfig(String workspacePath, Path configPath) throws IOException {
        org.noear.snack4.ONode config = org.noear.snack4.ONode.ofJson("{}");
        config.set("path", workspacePath);
        config.set("name", extractWorkspaceName(workspacePath));
        config.set("createdAt", System.currentTimeMillis());
        config.set("lastAccessedAt", System.currentTimeMillis());
        Files.writeString(configPath, config.toJson());
    }

    /**
     * 从路径中提取工作区名称（最后一级目录名）。
     * 当最后一级是 "." 或 ".." 时，取规范化路径的倒数第二级目录名。
     */
    private String extractWorkspaceName(String workspacePath) {
        Path path = Paths.get(workspacePath).normalize();
        String fileName = path.getFileName() != null ? path.getFileName().toString() : "";
        // "." 或 ".." 或空（根路径）时，取父目录名
        if (fileName.isEmpty() || ".".equals(fileName) || "..".equals(fileName)) {
            Path parent = path.getParent();
            if (parent != null && parent.getFileName() != null) {
                return parent.getFileName().toString();
            }
            // 仍然为空则回退到绝对路径的末级
            Path abs = path.toAbsolutePath().normalize();
            return abs.getFileName() != null ? abs.getFileName().toString() : workspacePath;
        }
        return fileName;
    }

    /**
     * 获取所有已注册的工作区
     */
    public List<WorkspaceInfo> listWorkspaces() throws IOException {
        List<WorkspaceInfo> workspaces = new ArrayList<>();
        if (!Files.isDirectory(WORKSPACES_DIR)) return workspaces;

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(WORKSPACES_DIR)) {
            for (Path dir : ds) {
                if (!Files.isDirectory(dir)) continue;
                Path configPath = dir.resolve("workspace.json");
                if (!Files.exists(configPath)) continue;

                try {
                    String json = Files.readString(configPath);
                    org.noear.snack4.ONode config = org.noear.snack4.ONode.ofJson(json);

                    String path = config.get("path").getString();
                    String name = extractWorkspaceName(path);
                    long createdAt = config.get("createdAt").getLong();
                    long lastAccessedAt = config.get("lastAccessedAt").getLong();

                    // 统计会话数量
                    Path sessionsDir = dir.resolve("sessions");
                    int sessionCount = 0;
                    if (Files.isDirectory(sessionsDir)) {
                        try (DirectoryStream<Path> sessionDs = Files.newDirectoryStream(sessionsDir, "*.jsonl")) {
                            for (Path ignored : sessionDs) {
                                sessionCount++;
                            }
                        }
                    }

                    String hash = dir.getFileName().toString();

                    workspaces.add(new WorkspaceInfo(hash, name, path,
                            createdAt, lastAccessedAt, sessionCount));
                } catch (Exception e) {
                    System.err.println("[workspace] 读取工作区配置失败: " + dir + " - " + e.getMessage());
                }
            }
        }

        // 按创建时间排序（固定顺序，不随切换跳动）
        workspaces.sort(Comparator.comparingLong(a -> a.createdAt));
        return workspaces;
    }

    /**
     * 切换到指定工作区
     */
    public void switchWorkspace(String workspacePath) throws IOException {
        String hash = computeHash(workspacePath);
        Path workspaceDir = WORKSPACES_DIR.resolve(hash);
        Path configPath = workspaceDir.resolve("workspace.json");

        if (!Files.exists(configPath)) {
            // 工作区不存在，自动创建
            initWorkspace(workspacePath);
        }

        this.currentWorkspacePath = workspacePath;

        // 更新最后访问时间
        updateLastAccessed(workspacePath);

    }

    /**
     * 更新工作区最后访问时间
     */
    public void updateLastAccessed(String workspacePath) throws IOException {
        Path configPath = getWorkspaceConfigPath(workspacePath);
        if (!Files.exists(configPath)) return;

        String json = Files.readString(configPath);
        org.noear.snack4.ONode config = org.noear.snack4.ONode.ofJson(json);
        config.set("lastAccessedAt", System.currentTimeMillis());
        Files.writeString(configPath, config.toJson());
    }

    /**
     * 删除工作区
     */
    public boolean deleteWorkspace(String hash) throws IOException {
        Path workspaceDir = WORKSPACES_DIR.resolve(hash);
        if (!Files.exists(workspaceDir)) return false;

        // 递归删除目录
        deleteDirectory(workspaceDir);
        return true;
    }

    /**
     * 递归删除目录
     */
    private void deleteDirectory(Path dir) throws IOException {
        if (Files.isDirectory(dir)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
                for (Path entry : ds) {
                    deleteDirectory(entry);
                }
            }
        }
        Files.delete(dir);
    }

    public String getCurrentWorkspaceHash() {
        return computeHash(currentWorkspacePath);
    }

    /**
     * 工作区信息
     */
    public record WorkspaceInfo(String hash, String name, String path, long createdAt, long lastAccessedAt,
                                int sessionCount) {
    }
}
