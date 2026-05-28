package site.sorghum.agent4j.bin.session;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

import site.sorghum.agent4j.bin.util.ONodeUtil;

/**
 * JSONL 格式会话持久化实现。
 * <p>
 * 文件位置：~/.agent4j/workspace/{hash}/sessions/{name}.jsonl 或 ~/.agent4j/sessions/{name}.jsonl
 * 格式：每行一个 JSON 格式的消息对象。
 * </p>
 * <p>
 * 使用 {@link BufferedWriter} 保持文件打开，避免每次写入打开/关闭文件。
 * 提供 {@link #flush()} 方法显式刷入磁盘，并启动定时线程每 30 秒自动刷入。
 * </p>
 *
 * @author Sorghum
 */
public class JsonlSessionStore implements SessionStore {

    private static final Path DEFAULT_SESSIONS_DIR = Paths.get(
            System.getProperty("user.home"), ".agent4j", "sessions");

    /** 当前会话目录（支持工作区隔离） */
    private final Path sessionsDir;

    /** 当前会话名 */
    private String currentName;

    /** 当前会话的 BufferedWriter（保持打开） */
    private BufferedWriter writer;

    /** 当前会话文件路径（用于 rewrite 时重新打开） */
    private Path currentFile;

    /** 线程同步锁 */
    private final ReentrantLock lock = new ReentrantLock();

    /** 定时刷入调度器 */
    private ScheduledExecutorService scheduler;

    /** 定时刷入间隔（秒） */
    private static final int FLUSH_INTERVAL_SEC = 30;

    /**
     * 默认构造函数，使用默认会话目录
     */
    public JsonlSessionStore() throws IOException {
        this(DEFAULT_SESSIONS_DIR);
    }

    /**
     * 指定会话目录的构造函数（支持工作区隔离）
     *
     * @param sessionsDir 会话目录路径
     */
    public JsonlSessionStore(Path sessionsDir) throws IOException {
        this.sessionsDir = sessionsDir;
        Files.createDirectories(sessionsDir);
        this.currentName = newSessionName();
        // 不立即创建空文件——延迟到首次 append 时通过 ensureWriter() 按需创建
        this.writer = null;
        startPeriodicFlush();
    }

    // ---- 文件管理 ----

    /** 打开（或重新打开）当前会话的 BufferedWriter */
    private void openWriter() throws IOException {
        closeWriter();
        this.currentFile = sessionPath(currentName);
        Files.createDirectories(currentFile.getParent());
        this.writer = Files.newBufferedWriter(currentFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /**
     * 确保 writer 已打开，若未打开则按需创建文件和 writer。
     * 延迟创建：仅在首次写入消息时才真正创建 .jsonl 文件，
     * 避免空白会话产生空文件。
     */
    private void ensureWriter() throws IOException {
        if (writer == null) {
            this.currentFile = sessionPath(currentName);
            Files.createDirectories(currentFile.getParent());
            this.writer = Files.newBufferedWriter(currentFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }

    /** 关闭当前 writer（刷入后关闭） */
    private void closeWriter() {
        lock.lock();
        try {
            if (writer != null) {
                try {
                    writer.flush();
                } catch (IOException ignored) {}
                try {
                    writer.close();
                } catch (IOException ignored) {}
                writer = null;
            }
        } finally {
            lock.unlock();
        }
    }

    // ---- 定时刷入 ----

    /** 启动定时刷入线程（daemon） */
    private void startPeriodicFlush() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "jsonl-flusher");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::flush,
                FLUSH_INTERVAL_SEC, FLUSH_INTERVAL_SEC, TimeUnit.SECONDS);
    }

    /** 停止定时刷入 */
    private void stopPeriodicFlush() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                scheduler.shutdownNow();
            }
        }
    }

    // ---- 关闭/清理 ----

    /**
     * 关闭 store，释放所有资源（定时器 + writer）。
     * 调用后不能再使用此 store 实例。
     */
    public void shutdown() {
        stopPeriodicFlush();
        closeWriter();
    }

    // ---- SessionStore 接口实现 ----

    @Override
    public String currentName() { return currentName; }

    @Override
    public String newSessionName() {
        String ts = new SimpleDateFormat("yyyyMMddHHmm").format(new Date());
        return "agent4j-" + ts;
    }

    @Override
    public boolean switchTo(String name) {
        if (name == null || name.isEmpty()) return false;
        lock.lock();
        try {
            // 关闭旧 writer，不创建新文件——延迟到首次 append 时创建
            closeWriter();
            this.currentName = sanitize(name);
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void append(Map<String, Object> message) throws IOException {
        String json = serializeMessage(message);
        lock.lock();
        try {
            // 延迟创建：首次写入消息时才真正创建文件
            ensureWriter();
            writer.write(json);
            writer.newLine();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void flush() {
        lock.lock();
        try {
            if (writer != null) {
                writer.flush();
            }
        } catch (IOException e) {
            System.err.println("[jsonl] flush 失败: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<Map<String, Object>> load() throws IOException {
        return load(currentName);
    }

    @Override
    public List<Map<String, Object>> load(String name) throws IOException {
        Path file = sessionPath(name);
        if (!Files.exists(file)) return new ArrayList<>();
        List<Map<String, Object>> messages = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("//")) continue;
            try {
                org.noear.snack4.ONode node = org.noear.snack4.ONode.ofJson(line);
                messages.add(ONodeUtil.toMap(node));
            } catch (Exception ignored) {}
        }
        return messages;
    }

    @Override
    public void rewrite(List<Map<String, Object>> messages) throws IOException {
        lock.lock();
        try {
            // 关闭当前 writer
            closeWriter();
            // 写入临时文件，然后替换
            Path file = sessionPath(currentName);
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (BufferedWriter w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                for (Map<String, Object> m : messages) {
                    w.write(serializeMessage(m));
                    w.newLine();
                }
                w.flush();
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            // 重新打开 writer
            openWriter();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String archive() throws IOException {
        lock.lock();
        try {
            Path file = sessionPath(currentName);
            if (!Files.exists(file)) return null;
            // 关闭当前 writer
            closeWriter();
            String ts = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            String archiveName = currentName + "__archive_" + ts;
            Path archiveFile = sessionPath(archiveName);
            Files.move(file, archiveFile);
            // 创建新会话，不创建空文件——writer 仍为 null，延迟到首次 append 时创建
            this.currentName = newSessionName();
            return archiveName;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<SessionInfo> list() throws IOException {
        if (!Files.isDirectory(sessionsDir)) return new ArrayList<>();
        List<SessionInfo> list = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(sessionsDir, "*.jsonl")) {
            for (Path p : ds) {
                String name = p.getFileName().toString().replace(".jsonl", "");
                if (name.contains("__archive")) continue;
                BasicFileAttributes attr = Files.readAttributes(p, BasicFileAttributes.class);
                long size = attr.size();
                long lines = 0;
                try (BufferedReader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                    while (r.readLine() != null) lines++;
                }
                // 读取标题
                String title = null;
                Path metaFile = sessionsDir.resolve(sanitize(name) + ".meta");
                if (Files.exists(metaFile)) {
                    try {
                        String metaJson = new String(Files.readAllBytes(metaFile), StandardCharsets.UTF_8);
                        org.noear.snack4.ONode metaNode = org.noear.snack4.ONode.ofJson(metaJson);
                        title = metaNode.get("title").getString();
                    } catch (Exception ignored) {}
                }
                list.add(new SessionInfo(name, size, lines, attr.lastModifiedTime().toMillis(), title));
            }
        }
        list.sort((a, b) -> Long.compare(b.mtime, a.mtime));
        return list;
    }

    @Override
    public boolean delete(String name) throws IOException {
        flush();
        Path p = sessionPath(name);
        return Files.deleteIfExists(p);
    }

    @Override
    public void saveUsage(String name, long prompt, long completion, long cacheHit, long cacheMiss) throws IOException {
        saveUsage(name, prompt, completion, cacheHit, cacheMiss, 0);
    }

    @Override
    public void saveUsage(String name, long prompt, long completion, long cacheHit, long cacheMiss, long lastPromptTokens) throws IOException {
        Path file = sessionsDir.resolve(sanitize(name) + ".usage");
        
        // 如果所有值都是 0，则删除文件（如果存在），避免创建空文件
        if (prompt == 0 && completion == 0 && cacheHit == 0 && cacheMiss == 0) {
            Files.deleteIfExists(file);
            return;
        }
        
        String json = "{\"prompt\":" + prompt + ",\"completion\":" + completion
                + ",\"cacheHit\":" + cacheHit + ",\"cacheMiss\":" + cacheMiss
                + ",\"lastPromptTokens\":" + lastPromptTokens + "}";
        Files.write(file, json.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void updateTitle(String name, String title) throws IOException {
        Path file = sessionsDir.resolve(sanitize(name) + ".meta");
        org.noear.snack4.ONode node = org.noear.snack4.ONode.ofJson("{}");
        node.set("title", title);
        Files.write(file, node.toJson().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String getTitle(String name) throws IOException {
        Path file = sessionsDir.resolve(sanitize(name) + ".meta");
        if (!Files.exists(file)) return null;
        try {
            String metaJson = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            org.noear.snack4.ONode metaNode = org.noear.snack4.ONode.ofJson(metaJson);
            return metaNode.get("title").getString();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public long[] loadUsage(String name) {
        Path file = sessionsDir.resolve(sanitize(name) + ".usage");
        if (!Files.exists(file)) return new long[]{0, 0, 0, 0, 0};
        try {
            String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            org.noear.snack4.ONode node = org.noear.snack4.ONode.ofJson(json);
            return new long[]{
                    node.get("prompt").getLong(),
                    node.get("completion").getLong(),
                    node.get("cacheHit").getLong(),
                    node.get("cacheMiss").getLong(),
                    node.get("lastPromptTokens").getLong()
            };
        } catch (Exception e) {
            return new long[]{0, 0, 0, 0, 0};
        }
    }

    // ---- 内部辅助 ----

    private Path sessionPath(String name) {
        return sessionsDir.resolve(sanitize(name) + ".jsonl");
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }



    @SuppressWarnings("unchecked")
    public static String serializeMessage(Map<String, Object> msg) {
        org.noear.snack4.ONode node = org.noear.snack4.ONode.ofJson("{}");
        Object role = msg.get("role");
        node.set("role", role != null ? String.valueOf(role) : "user");
        // content: 只有非 null 才设置，避免 "null" 字符串
        Object content = msg.get("content");
        if (content != null) {
            node.set("content", content.toString());
        }
        // reasoning_content: 只有非 null 才设置
        Object reasoning = msg.get("reasoning_content");
        if (reasoning != null) {
            node.set("reasoning_content", reasoning.toString());
        }
        // tool_call_id: 只有非 null 才设置
        Object toolCallId = msg.get("tool_call_id");
        if (toolCallId != null) {
            node.set("tool_call_id", toolCallId.toString());
        }
        if (msg.containsKey("tool_calls")) {
            org.noear.snack4.ONode tcArr = node.getOrNew("tool_calls").asArray();
            for (Map<String, Object> tc : (List<Map<String, Object>>) msg.get("tool_calls")) {
                org.noear.snack4.ONode tcn = tcArr.addNew();
                Object tcId = tc.get("id");
                tcn.set("id", tcId != null ? String.valueOf(tcId) : "unknown");
                tcn.set("type", "function");
                org.noear.snack4.ONode func = tcn.getOrNew("function");
                Object tcName = tc.get("name");
                func.set("name", tcName != null ? String.valueOf(tcName) : "unknown");
                String args = tc.get("arguments") != null ? tc.get("arguments").toString() : "{}";
                func.set("arguments", args);
            }
        }
        return node.toJson();
    }
}
