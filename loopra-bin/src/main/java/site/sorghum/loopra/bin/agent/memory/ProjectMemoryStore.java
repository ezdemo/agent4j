package site.sorghum.loopra.bin.agent.memory;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 项目级记忆存储 —— 工作区内 {@code .loopra/loopra-memory.md}。
 * <p>
 * 跨会话沉淀的关键事实（架构决策、约定、踩坑、用户偏好等）。
 * 会话启动时由 {@code ToolSystemInitializer} 注入 system prompt，
 * compact 折叠时由 {@code ContextFolding} 追加新条目。
 * </p>
 * <p>
 * 文件结构：
 * <pre>
 * # Loopra 项目记忆
 * （说明文字）
 * ---
 * ## [2026-07-17 14:30] 会话折叠沉淀
 * - 事实1
 * - 事实2
 * ## [2026-07-17 15:00] 会话折叠沉淀
 * - ...
 * </pre>
 * {@link #load(Path)} 剥离文件头说明，只返回 {@code ---} 之后的正文条目，
 * 避免说明文字占用上下文 token。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
public final class ProjectMemoryStore {

    /** 工作区内记忆目录名 */
    public static final String MEMORY_DIR = ".loopra";
    /** 记忆文件名 */
    public static final String MEMORY_FILE = "loopra-memory.md";

    /** 记忆文件触发截断保护的字符阈值（正文部分） */
    public static final int TRUNCATE_THRESHOLD = 12_000;
    /** 截断后保留的尾部近似字符上限 */
    public static final int TRUNCATE_KEEP = 6_000;
    /** 注入 system prompt 时正文的最大字符数（超出只取尾部，保护上下文预算） */
    public static final int MAX_INJECT_CHARS = 8_000;

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 文件头说明区与正文之间的分隔行 */
    private static final String HEADER_SEPARATOR = "---";

    private ProjectMemoryStore() {
    }

    /**
     * 记忆文件路径。
     *
     * @param workspace 工作区根目录（可为 null，返回 null）
     */
    public static Path memoryFilePath(Path workspace) {
        if (workspace == null) return null;
        return workspace.resolve(MEMORY_DIR).resolve(MEMORY_FILE);
    }

    /**
     * 读取记忆正文（剥离文件头说明），文件不存在或读失败返回空串。
     * 超过 {@link #MAX_INJECT_CHARS} 时只返回尾部，保护上下文预算。
     */
    public static String load(Path workspace) {
        Path file = memoryFilePath(workspace);
        if (file == null) return "";
        try {
            if (!Files.exists(file)) return "";
            String content = Files.readString(file, StandardCharsets.UTF_8);
            String body = stripHeader(content);
            return tailForInject(body);
        } catch (IOException e) {
            log.warn("[memory] 读取记忆失败: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 追加一条带时间戳的记忆条目。facts 为空或"无"时跳过。
     * 自动创建 {@code .loopra} 目录；首次写入时附带文件头说明。
     * 线程安全：同一工作区的追加串行化，避免子代理并发写损坏。
     */
    public static synchronized void append(Path workspace, String facts) {
        if (workspace == null) return;
        if (facts == null) return;
        String trimmed = facts.trim();
        if (trimmed.isEmpty() || "无".equals(trimmed) || "无。".equals(trimmed)) return;

        Path file = memoryFilePath(workspace);
        if (file == null) return;
        try {
            Path dir = file.getParent();
            if (dir != null && !Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            String entry = buildEntry(trimmed);
            if (!Files.exists(file)) {
                Files.writeString(file, buildHeader() + entry, StandardCharsets.UTF_8);
            } else {
                Files.writeString(file, entry, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            }
            log.info("[memory] 已沉淀记忆条目（{} 字符）到 {}", entry.length(), file);
            truncateIfTooLarge(workspace);
        } catch (IOException e) {
            log.warn("[memory] 追加记忆失败: {}", e.getMessage());
        }
    }

    /**
     * 当正文超过 {@link #TRUNCATE_THRESHOLD} 时，保留尾部 {@link #TRUNCATE_KEEP}
     * 字符对应的完整条目，重写文件（保留文件头）。
     * 简单截断保护，避免文件无限增长；不做 LLM 压缩以避免循环依赖。
     */
    private static synchronized void truncateIfTooLarge(Path workspace) throws IOException {
        Path file = memoryFilePath(workspace);
        if (file == null || !Files.exists(file)) return;
        String content = Files.readString(file, StandardCharsets.UTF_8);
        String body = stripHeader(content);
        if (body.length() <= TRUNCATE_THRESHOLD) return;

        List<String> entries = splitEntries(body);
        // 从尾部累积保留，直到达到 TRUNCATE_KEEP
        StringBuilder kept = new StringBuilder();
        int cum = 0;
        int cutIdx = entries.size();
        for (int i = entries.size() - 1; i >= 0; i--) {
            int len = entries.get(i).length();
            if (cum + len > TRUNCATE_KEEP) {
                cutIdx = i;
                break;
            }
            cum += len;
            cutIdx = i;
        }
        if (cutIdx <= 0) return; // 保留全部也不超限，无需截断

        StringBuilder newBody = new StringBuilder();
        for (int i = cutIdx; i < entries.size(); i++) {
            newBody.append(entries.get(i));
        }
        Files.writeString(file, buildHeader() + newBody, StandardCharsets.UTF_8);
        int dropped = cutIdx;
        log.info("[memory] 记忆超阈值截断：丢弃 {} 条较早条目，保留 {} 条",
                dropped, entries.size() - dropped);
    }

    /**
     * 列出所有记忆条目（不截断），返回按文件顺序的条目文本列表。
     * 文件不存在或为空返回空列表。供 list/delete 按编号定位使用。
     * 与 {@link #load} 不同：不做 {@code MAX_INJECT_CHARS} 截断，
     * 保证编号与实际文件条目一一对应，避免删错。
     */
    public static synchronized List<String> listEntries(Path workspace) {
        Path file = memoryFilePath(workspace);
        if (file == null || !Files.exists(file)) return List.of();
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            String body = stripHeader(content);
            return splitEntries(body);
        } catch (IOException e) {
            log.warn("[memory] 列出记忆失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 按 1-based 索引删除一条记忆，重写文件（保留文件头）。
     *
     * @return true 表示删除成功；索引越界或文件缺失返回 false
     */
    public static synchronized boolean deleteByIndex(Path workspace, int index) {
        if (index < 1) return false;
        Path file = memoryFilePath(workspace);
        if (file == null || !Files.exists(file)) return false;
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            String body = stripHeader(content);
            List<String> entries = splitEntries(body);
            if (index > entries.size()) return false;
            entries.remove(index - 1);
            StringBuilder newBody = new StringBuilder();
            for (String e : entries) newBody.append(e);
            Files.writeString(file, buildHeader() + newBody, StandardCharsets.UTF_8);
            log.info("[memory] 删除第 {} 条记忆，剩余 {} 条", index, entries.size());
            return true;
        } catch (IOException e) {
            log.warn("[memory] 删除记忆失败: {}", e.getMessage());
            return false;
        }
    }

    // ==================== 解析与格式化 ====================

    /**
     * 剥离文件头说明区：找到第一个只含 {@code ---} 的行，返回其后的内容。
     * 没有分隔行时返回原文。
     */
    private static String stripHeader(String content) {
        List<String> lines = new ArrayList<>();
        content.lines().forEach(lines::add);
        int sep = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().equals(HEADER_SEPARATOR)) {
                sep = i;
                break;
            }
        }
        if (sep < 0) return content;
        StringBuilder sb = new StringBuilder();
        for (int i = sep + 1; i < lines.size(); i++) {
            sb.append(lines.get(i)).append('\n');
        }
        return sb.toString().trim();
    }

    /**
     * 按条目（{@code ## } 开头）拆分正文，保留每个条目含其起始换行。
     */
    private static List<String> splitEntries(String body) {
        List<String> entries = new ArrayList<>();
        if (body.isEmpty()) return entries;
        String[] parts = body.split("(?m)(?=^## )", 0);
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) entries.add(t + "\n\n");
        }
        return entries;
    }

    /**
     * 注入时若正文超 {@link #MAX_INJECT_CHARS}，只保留尾部（按完整条目切分）。
     * 当单条条目仍超限时，对该条硬截断取尾部。
     */
    private static String tailForInject(String body) {
        if (body.length() <= MAX_INJECT_CHARS) return body;
        List<String> entries = splitEntries(body);
        StringBuilder sb = new StringBuilder();
        for (int i = entries.size() - 1; i >= 0; i--) {
            String e = entries.get(i);
            if (sb.length() + e.length() > MAX_INJECT_CHARS && sb.length() > 0) break;
            sb.insert(0, e);
        }
        // 单条就超限：硬截断取尾部
        if (sb.length() > MAX_INJECT_CHARS) {
            return sb.substring(sb.length() - MAX_INJECT_CHARS);
        }
        return sb.toString().trim();
    }

    private static String buildHeader() {
        return """
                # Loopra 项目记忆

                本文件由 Loopra 自动维护，记录跨会话沉淀的项目关键事实（架构决策、约定、踩坑、用户偏好等）。
                会话启动时自动注入 AI 上下文。可手动删除过期条目，请勿手动修改条目格式。

                ---
                """;
    }

    private static String buildEntry(String facts) {
        String ts = LocalDateTime.now().format(TS_FMT);
        return "\n## [" + ts + "] 会话折叠沉淀\n\n" + facts + "\n";
    }
}
