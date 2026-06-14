package site.sorghum.agent4j.bin.session;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import site.sorghum.agent4j.bin.agent.ChatMessage;
import site.sorghum.agent4j.bin.agent.ToolCallEntry;
import site.sorghum.agent4j.bin.util.ONodeUtil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * JSONL 格式会话持久化实现。
 * <p>
 * 文件位置：~/.agent4j/workspace/{hash}/sessions/{name}.jsonl 或 ~/.agent4j/sessions/{name}.jsonl
 * 格式：每行一个 JSON 格式的消息对象。
 * </p>
 * <p>
 * 使用 {@link BufferedWriter} 保持文件打开，避免每次写入打开/关闭文件。
 * 将消息先入内存队列，后台消费者线程异步批量写入，减少关键路径 IO 阻塞。
 * 提供 {@link #flush()} 方法显式刷入磁盘，并启动定时线程每 30 秒自动刷入。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
public class JsonlSessionStore implements SessionStore {

    private static final Path DEFAULT_SESSIONS_DIR = Paths.get(
            System.getProperty("user.home"), ".agent4j", "sessions");
    /**
     * 定时刷入间隔（秒）
     */
    private static final int FLUSH_INTERVAL_SEC = 30;
    /**
     * 异步写入缓冲区最大积压数（防止 OOM）
     */
    private static final int MAX_BUFFER_SIZE = 10_000;
    /**
     * 单次批量写入的最大条数
     */
    private static final int BATCH_SIZE = 50;
    /**
     * 当前会话目录（支持工作区隔离）
     */
    private final Path sessionsDir;
    /**
     * 线程同步锁
     */
    private final ReentrantLock lock = new ReentrantLock();
    /**
     * 异步写入缓冲区（关键路径不阻塞 IO）
     */
    private final BlockingQueue<ChatMessage> buffer = new LinkedBlockingQueue<>(MAX_BUFFER_SIZE);
    /**
     * 异步消费者线程
     */
    private final Thread consumerThread;
    /**
     * 消费者线程停止标志
     */
    private volatile boolean consumerRunning = true;
    /**
     * 当前会话名
     */
    private String currentName;
    /**
     * 当前会话的 BufferedWriter（保持打开）
     */
    private BufferedWriter writer;
    /**
     * 当前会话文件路径（用于 rewrite 时重新打开）
     */
    private Path currentFile;
    /**
     * 定时刷入调度器
     */
    private ScheduledExecutorService scheduler;

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
    @SneakyThrows
    public JsonlSessionStore(Path sessionsDir){
        this.sessionsDir = sessionsDir;
        Files.createDirectories(sessionsDir);
        // 不自动分配会话名 —— 等用户主动选择或首次写入时才确定
        this.currentName = null;
        this.writer = null;
        // 启动异步消费者线程
        this.consumerThread = new Thread(this::consumerLoop, "jsonl-consumer");
        this.consumerThread.setDaemon(true);
        this.consumerThread.start();
        startPeriodicFlush();
    }

    // ---- 文件管理 ----

    private static String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    public static String serializeMessage(ChatMessage msg) {
        org.noear.snack4.ONode node = org.noear.snack4.ONode.ofJson("{}");
        node.set("role", msg.getRole());
        // 多模态 contentParts 优先序列化为 JSON array（与 OpenAI API 格式一致）
        if (msg.getContentParts() != null && !msg.getContentParts().isEmpty()) {
            org.noear.snack4.ONode contentArr = node.getOrNew("content").asArray();
            for (ChatMessage.ContentPart part : msg.getContentParts()) {
                org.noear.snack4.ONode partNode = contentArr.addNew();
                partNode.set("type", part.getType());
                if ("text".equals(part.getType())) {
                    partNode.set("text", part.getText() != null ? part.getText() : "");
                } else if ("image_url".equals(part.getType())) {
                    ChatMessage.ContentPart.ImageUrl iu = part.getImageUrl();
                    if (iu != null) {
                        org.noear.snack4.ONode urlNode = partNode.getOrNew("image_url");
                        urlNode.set("url", iu.getUrl() != null ? iu.getUrl() : "");
                        if (iu.getDetail() != null) urlNode.set("detail", iu.getDetail());
                    }
                }
            }
        } else if (msg.getContent() != null) {
            node.set("content", msg.getContent());
        }
        if (msg.getReasoningContent() != null) {
            node.set("reasoning_content", msg.getReasoningContent());
        }
        if (msg.getSnapshotId() != null) {
            node.set("snapshot_id", msg.getSnapshotId());
        }
        if (msg.getTimestamp() != null) {
            node.set("timestamp", msg.getTimestamp());
        }
        if (msg.getToolCallId() != null) {
            node.set("tool_call_id", msg.getToolCallId());
        }
        if (msg.hasToolCalls()) {
            org.noear.snack4.ONode tcArr = node.getOrNew("tool_calls").asArray();
            for (ToolCallEntry tc : msg.getToolCalls()) {
                org.noear.snack4.ONode tcn = tcArr.addNew();
                tcn.set("id", tc.id() != null ? tc.id() : "unknown");
                tcn.set("type", "function");
                org.noear.snack4.ONode func = tcn.getOrNew("function");
                func.set("name", tc.name() != null ? tc.name() : "unknown");
                Object tcArgs = tc.arguments();
                String argsStr = "{}";
                if (tcArgs != null) {
                    if (tcArgs instanceof String) {
                        argsStr = (String) tcArgs;
                    } else {
                        argsStr = org.noear.snack4.ONode.serialize(tcArgs);
                    }
                }
                func.set("arguments", argsStr);
            }
        }
        return node.toJson();
    }

    /**
     * 打开（或重新打开）当前会话的 BufferedWriter
     */
    private void openWriter() throws IOException {
        closeWriter();
        this.currentFile = sessionPath(currentName);
        Files.createDirectories(currentFile.getParent());
        this.writer = Files.newBufferedWriter(currentFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    // ---- 定时刷入 ----

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

    /**
     * 异步消费者：从缓冲区批量取出消息并写入文件。
     * 当缓冲区为空时阻塞等待，减少忙轮询。
     */
    private void consumerLoop() {
        List<ChatMessage> batch = new ArrayList<>(BATCH_SIZE);
        while (consumerRunning) {
            try {
                // 阻塞取一条（避免忙等）
                ChatMessage first = buffer.poll(1, TimeUnit.SECONDS);
                if (first == null) continue;
                batch.add(first);
                // 非阻塞取剩余（最多 BATCH_SIZE 条）
                buffer.drainTo(batch, BATCH_SIZE - 1);
                // 批量写入
                writeBatch(batch);
                batch.clear();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        // 消费者退出前，将剩余消息全部写完
        List<ChatMessage> remaining = new ArrayList<>();
        buffer.drainTo(remaining);
        if (!remaining.isEmpty()) {
            writeBatch(remaining);
        }
    }

    /**
     * 批量写入消息到文件（持有锁）
     */
    private void writeBatch(List<ChatMessage> messages) {
        if (messages.isEmpty()) return;
        lock.lock();
        try {
            // 若尚未选择会话，自动创建新会话
            if (currentName == null) {
                currentName = newSessionName();
            }
            ensureWriter();
            for (ChatMessage msg : messages) {
                writer.write(serializeMessage(msg));
                writer.newLine();
            }
        } catch (IOException e) {
            log.error("[jsonl] 批量写入失败: {}", e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    /**
     * 关闭当前 writer（刷入后关闭）
     */
    private void closeWriter() {
        lock.lock();
        try {
            if (writer != null) {
                try {
                    writer.flush();
                } catch (IOException e) {
                    log.warn("[jsonl] flush 关闭前失败: {}", e.getMessage());
                }
                try {
                    writer.close();
                } catch (IOException e) {
                    log.warn("[jsonl] close 关闭前失败: {}", e.getMessage());
                }
                writer = null;
            }
        } finally {
            lock.unlock();
        }
    }

    // ---- 关闭/清理 ----

    /**
     * 启动定时刷入线程（daemon）
     */
    private void startPeriodicFlush() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "jsonl-flusher");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::flush,
                FLUSH_INTERVAL_SEC, FLUSH_INTERVAL_SEC, TimeUnit.SECONDS);
    }

    // ---- SessionStore 接口实现 ----

    /**
     * 停止定时刷入
     */
    private void stopPeriodicFlush() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 关闭 store，释放所有资源（消费者线程 + 定时器 + writer）。
     * 调用后不能再使用此 store 实例。
     */
    public void shutdown() {
        consumerRunning = false;
        if (consumerThread != null) {
            try {
                consumerThread.interrupt();
                consumerThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        stopPeriodicFlush();
        closeWriter();
    }

    @Override
    public String currentName() {
        return currentName;
    }

    @Override
    public String newSessionName() {
        String ts = new SimpleDateFormat("yyyyMMddHHmm").format(new Date());
        return "agent4j-" + ts;
    }

    @Override
    public boolean switchTo(String name) {
        if (name == null || name.isEmpty()) return false;
        // 切换前先排空缓冲区
        drainBuffer();
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
    public void append(ChatMessage message) throws IOException {
        // 非阻塞入队，关键路径不执行 IO
        if (!buffer.offer(message)) {
            // 缓冲区满（MAX_BUFFER_SIZE），降级为同步写入
            lock.lock();
            try {
                if (currentName == null) {
                    currentName = newSessionName();
                }
                String json = serializeMessage(message);
                ensureWriter();
                writer.write(json);
                writer.newLine();
            } finally {
                lock.unlock();
            }
        }
    }

    @Override
    public void flush() {
        // 先将缓冲区中的消息全部排空到文件
        List<ChatMessage> pending = new ArrayList<>();
        buffer.drainTo(pending);
        if (!pending.isEmpty()) {
            writeBatch(pending);
        }
        // 再刷入文件系统
        lock.lock();
        try {
            if (writer != null) {
                writer.flush();
            }
        } catch (IOException e) {
            log.error("[jsonl] flush 失败: {}", e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<ChatMessage> load() throws IOException {
        if (currentName == null) return new ArrayList<>();
        return load(currentName);
    }

    @Override
    public List<ChatMessage> load(String name) throws IOException {
        Path file = sessionPath(name);
        if (!Files.exists(file)) return new ArrayList<>();
        List<ChatMessage> messages = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("//")) continue;
            try {
                org.noear.snack4.ONode node = org.noear.snack4.ONode.ofJson(line);
                messages.add(ChatMessage.fromMap(ONodeUtil.toMap(node)));
            } catch (Exception e) {
                log.warn("[jsonl] 解析消息行失败: {}", e.getMessage());
            }
        }
        return messages;
    }

    @Override
    public void rewrite(List<ChatMessage> messages) throws IOException {
        // 重写前排空缓冲区
        drainBuffer();
        lock.lock();
        try {
            // 关闭当前 writer
            closeWriter();
            // 写入临时文件，然后替换
            Path file = sessionPath(currentName);
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (BufferedWriter w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                for (ChatMessage m : messages) {
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
                        String metaJson = Files.readString(metaFile);
                        org.noear.snack4.ONode metaNode = org.noear.snack4.ONode.ofJson(metaJson);
                        title = metaNode.get("title").getString();
                    } catch (Exception e) {
                        log.warn("[jsonl] 读取会话元数据失败: {}", e.getMessage());
                    }
                }
                list.add(new SessionInfo(name, size, lines, attr.lastModifiedTime().toMillis(), title));
            }
        }
        list.sort((a, b) -> Long.compare(b.mtime(), a.mtime()));
        return list;
    }

    @Override
    public boolean delete(String name) throws IOException {
        flush();
        String safe = sanitize(name);
        Path jsonl = sessionsDir.resolve(safe + ".jsonl");
        Path usage = sessionsDir.resolve(safe + ".usage");
        Path meta = sessionsDir.resolve(safe + ".meta");
        boolean deleted = Files.deleteIfExists(jsonl);
        Files.deleteIfExists(usage);
        Files.deleteIfExists(meta);
        return deleted;
    }

    @SneakyThrows
    @Override
    public void clearAll(){
        flush();
        if (!Files.isDirectory(sessionsDir)) return;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(sessionsDir)) {
            for (Path p : ds) {
                String name = p.getFileName().toString();
                // 只删除会话相关文件：.jsonl / .usage / .meta / .model_usage
                if (name.endsWith(".jsonl") || name.endsWith(".usage")
                        || name.endsWith(".meta") || name.endsWith(".model_usage")) {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        log.warn("[jsonl] 删除文件失败 {}: {}", name, e.getMessage());
                    }
                }
            }
        }
        // 重置当前会话状态
        currentName = null;
        closeWriter();
    }

    @Override
    public void saveUsage(String name, long prompt, long completion, long cacheHit, long cacheMiss) throws IOException {
        saveUsage(name, prompt, completion, cacheHit, cacheMiss, 0);
    }

    @Override
    public void saveUsage(String name, long prompt, long completion,
                          long cacheHit, long cacheMiss, long lastPromptTokens) throws IOException {
        Path file = sessionsDir.resolve(sanitize(name) + ".usage");

        // 如果所有值都是 0，则删除文件（如果存在），避免创建空文件
        if (prompt == 0 && completion == 0 && cacheHit == 0 && cacheMiss == 0) {
            Files.deleteIfExists(file);
            return;
        }

        org.noear.snack4.ONode node = org.noear.snack4.ONode.ofJson("{}").asObject();
        node.set("prompt", prompt);
        node.set("completion", completion);
        node.set("cacheHit", cacheHit);
        node.set("cacheMiss", cacheMiss);
        node.set("lastPromptTokens", lastPromptTokens);
        Files.writeString(file, node.toJson());
    }

    @Override
    public void updateTitle(String name, String title) throws IOException {
        Path file = sessionsDir.resolve(sanitize(name) + ".meta");
        org.noear.snack4.ONode node = org.noear.snack4.ONode.ofJson("{}");
        node.set("title", title);
        Files.writeString(file, node.toJson());
    }

    @Override
    public String getTitle(String name) {
        Path file = sessionsDir.resolve(sanitize(name) + ".meta");
        if (!Files.exists(file)) return null;
        try {
            String metaJson = Files.readString(file);
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
            String json = Files.readString(file);
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

    // ---- 异步缓冲区排空 ----

    /**
     * 排空缓冲区中所有待写入消息（同步等待消费者完成）
     */
    private void drainBuffer() {
        List<ChatMessage> pending = new ArrayList<>();
        buffer.drainTo(pending);
        if (!pending.isEmpty()) {
            writeBatch(pending);
        }
    }

    // ---- 内部辅助 ----

    @Override
    public void saveModelUsage(String name, Map<String, long[]> modelUsage) throws IOException {
        if (modelUsage == null || modelUsage.isEmpty()) {
            // 删除文件（如果存在）
            Path file = sessionsDir.resolve(sanitize(name) + ".model_usage");
            Files.deleteIfExists(file);
            return;
        }
        org.noear.snack4.ONode root = org.noear.snack4.ONode.ofJson("{}").asObject();
        for (Map.Entry<String, long[]> entry : modelUsage.entrySet()) {
            long[] mu = entry.getValue();
            org.noear.snack4.ONode arr = root.getOrNew(entry.getKey()).asArray();
            arr.clear();
            for (long v : mu) {
                arr.add(v);
            }
        }
        Path file = sessionsDir.resolve(sanitize(name) + ".model_usage");
        Files.writeString(file, root.toJson());
    }

    @Override
    public Map<String, long[]> loadModelUsage(String name) {
        Map<String, long[]> result = new LinkedHashMap<>();
        Path file = sessionsDir.resolve(sanitize(name) + ".model_usage");
        if (!Files.exists(file)) return result;
        try {
            String json = Files.readString(file);
            org.noear.snack4.ONode root = org.noear.snack4.ONode.ofJson(json);
            for (Map.Entry<String, org.noear.snack4.ONode> entry : root.getObject().entrySet()) {
                org.noear.snack4.ONode arr = entry.getValue();
                if (arr.isArray() && !arr.getArray().isEmpty()) {
                    long[] mu = new long[Math.min(arr.getArray().size(), 4)];
                    for (int i = 0; i < mu.length; i++) {
                        mu[i] = arr.getArray().get(i).getLong();
                    }
                    result.put(entry.getKey(), mu);
                }
            }
        } catch (Exception e) {
            return result;
        }
        return result;
    }

    private Path sessionPath(String name) {
        return sessionsDir.resolve(sanitize(name) + ".jsonl");
    }

    // ---- 每日用量日志（全局共享） ----

    private static final Path DAILY_USAGE_FILE = Paths.get(
            System.getProperty("user.home"), ".agent4j", "usage_daily.jsonl");

    private static final java.util.concurrent.locks.ReentrantLock DAILY_LOCK =
            new java.util.concurrent.locks.ReentrantLock();

    @Override
    public void appendDailyUsage(String model, int prompt, int completion,
                                 int cacheHit, int cacheMiss) {
        if (prompt == 0 && completion == 0) return;
        DAILY_LOCK.lock();
        try {
            Files.createDirectories(DAILY_USAGE_FILE.getParent());
            String modelName = model != null ? model.replace("\\", "\\\\").replace("\"", "\\\"") : "unknown";
            String line = "{\"ts\":" + System.currentTimeMillis()
                    + ",\"model\":\"" + modelName + "\""
                    + ",\"prompt\":" + prompt
                    + ",\"completion\":" + completion
                    + ",\"cacheHit\":" + cacheHit
                    + ",\"cacheMiss\":" + cacheMiss + "}\n";
            Files.writeString(DAILY_USAGE_FILE, line,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.debug("[usage] 写入每日用量日志失败: {}", e.getMessage());
        } finally {
            DAILY_LOCK.unlock();
        }
    }
}
