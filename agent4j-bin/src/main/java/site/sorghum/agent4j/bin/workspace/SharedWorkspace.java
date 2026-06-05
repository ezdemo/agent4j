package site.sorghum.agent4j.bin.workspace;

import org.noear.solon.annotation.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 共享工作区核心存储 —— 线程安全，支持 KV 和文档两种存储模式。
 * <p>
 * 使用 {@link ConcurrentHashMap} 保证单操作线程安全，{@link ReadWriteLock} 保障批量操作一致性。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class SharedWorkspace {

    private static final Logger log = LoggerFactory.getLogger(SharedWorkspace.class);

    /** KV 存储 */
    private final ConcurrentHashMap<String, KVBucket> kvStore = new ConcurrentHashMap<>();

    /** 文档存储 */
    private final ConcurrentHashMap<String, DocumentBucket> docStore = new ConcurrentHashMap<>();

    /** 事件总线 */
    private final WorkspaceEventBus eventBus = new WorkspaceEventBus();

    /** 读写锁 —— 批量操作一致性 */
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    /** 最大条目数（单个存储类型），默认 1000 */
    private int maxEntries;

    public SharedWorkspace() {
        this(1000);
    }

    /**
     * @param maxEntries 单个存储（KV / 文档）的最大条目数
     */
    public SharedWorkspace(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    // ==================== KV 操作 ====================

    /**
     * 写入 KV 条目。
     * <ul>
     *   <li>如果 key 已存在：更新 value、updatedAt、version++</li>
     *   <li>如果 key 不存在：新建 KVBucket，版本从 1 开始</li>
     *   <li>写入前检查容量，超限则淘汰最旧条目</li>
     *   <li>发布事件到 eventBus（WRITE 或 UPDATE）</li>
     * </ul>
     *
     * @param key     键
     * @param value   值
     * @param creator 创建者
     */
    public void writeKV(String key, String value, String creator) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(creator, "creator must not be null");

        rwLock.readLock().lock();
        try {
            // 容量检查：超限则淘汰最旧条目
            if (kvStore.size() >= maxEntries) {
                evictOne();
            }

            KVBucket existing = kvStore.get(key);
            if (existing != null) {
                // 更新已有条目
                existing.setValue(value);
                existing.setUpdatedAt(System.currentTimeMillis());
                existing.setVersion(existing.getVersion() + 1);
                eventBus.publish(key, EventType.UPDATE, existing);
                log.debug("Updated KV entry: key={}, version={}", key, existing.getVersion());
            } else {
                // 新建条目
                long now = System.currentTimeMillis();
                KVBucket bucket = KVBucket.builder()
                        .value(value)
                        .creator(creator)
                        .createdAt(now)
                        .updatedAt(now)
                        .version(1)
                        .build();
                kvStore.put(key, bucket);
                eventBus.publish(key, EventType.WRITE, bucket);
                log.debug("Created KV entry: key={}, version=1", key);
            }
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * 读取 KV 条目的值。
     * 如果条目已过期则自动删除并返回 {@link Optional#empty()}。
     *
     * @param key 键
     * @return 值（可能为空）
     */
    public Optional<String> readKV(String key) {
        Objects.requireNonNull(key, "key must not be null");

        KVBucket bucket = kvStore.get(key);
        if (bucket == null) {
            return Optional.empty();
        }
        if (bucket.isExpired()) {
            kvStore.remove(key);
            eventBus.publish(key, EventType.DELETE, null);
            log.debug("Removed expired KV entry: key={}", key);
            return Optional.empty();
        }
        return Optional.of(bucket.getValue());
    }

    /**
     * 读取完整的 KV 桶信息。
     * 如果条目已过期则自动删除并返回 {@link Optional#empty()}。
     *
     * @param key 键
     * @return KVBucket（可能为空）
     */
    public Optional<KVBucket> getKVBucket(String key) {
        Objects.requireNonNull(key, "key must not be null");

        KVBucket bucket = kvStore.get(key);
        if (bucket == null) {
            return Optional.empty();
        }
        if (bucket.isExpired()) {
            kvStore.remove(key);
            eventBus.publish(key, EventType.DELETE, null);
            log.debug("Removed expired KV entry: key={}", key);
            return Optional.empty();
        }
        return Optional.of(bucket);
    }

    // ==================== 文档操作 ====================

    /**
     * 写入文档条目。
     * <ul>
     *   <li>如果 key 已存在：更新 content、mimeType、updatedAt、version++</li>
     *   <li>如果 key 不存在：新建 DocumentBucket，版本从 1 开始</li>
     *   <li>写入前检查容量，超限则淘汰最旧文档</li>
     *   <li>发布事件到 eventBus（WRITE 或 UPDATE）</li>
     * </ul>
     *
     * @param key      键
     * @param content  文档内容
     * @param mimeType MIME 类型
     * @param creator  创建者
     */
    public void writeDoc(String key, String content, String mimeType, String creator) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(mimeType, "mimeType must not be null");
        Objects.requireNonNull(creator, "creator must not be null");

        rwLock.readLock().lock();
        try {
            // 容量检查：超限则淘汰最旧文档
            if (docStore.size() >= maxEntries) {
                evictOneDoc();
            }

            DocumentBucket existing = docStore.get(key);
            if (existing != null) {
                // 更新已有文档
                existing.setContent(content);
                existing.setMimeType(mimeType);
                existing.setUpdatedAt(System.currentTimeMillis());
                existing.setVersion(existing.getVersion() + 1);
                eventBus.publish(key, EventType.UPDATE, existing);
                log.debug("Updated document entry: key={}, version={}", key, existing.getVersion());
            } else {
                // 新建文档
                long now = System.currentTimeMillis();
                DocumentBucket bucket = DocumentBucket.builder()
                        .content(content)
                        .mimeType(mimeType)
                        .creator(creator)
                        .createdAt(now)
                        .updatedAt(now)
                        .version(1)
                        .build();
                docStore.put(key, bucket);
                eventBus.publish(key, EventType.WRITE, bucket);
                log.debug("Created document entry: key={}, version=1", key);
            }
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * 读取文档条目。
     * 如果文档已过期则自动删除并返回 {@link Optional#empty()}。
     *
     * @param key 键
     * @return DocumentBucket（可能为空）
     */
    public Optional<DocumentBucket> readDoc(String key) {
        Objects.requireNonNull(key, "key must not be null");

        DocumentBucket bucket = docStore.get(key);
        if (bucket == null) {
            return Optional.empty();
        }
        if (bucket.isExpired()) {
            docStore.remove(key);
            eventBus.publish(key, EventType.DELETE, null);
            log.debug("Removed expired document entry: key={}", key);
            return Optional.empty();
        }
        return Optional.of(bucket);
    }

    // ==================== 通用操作 ====================

    /**
     * 删除指定 key 的条目（同时检查 KV 和文档存储）。
     *
     * @param key 键
     */
    public void delete(String key) {
        Objects.requireNonNull(key, "key must not be null");

        boolean removed = false;
        KVBucket kvRemoved = kvStore.remove(key);
        if (kvRemoved != null) {
            removed = true;
            log.debug("Deleted KV entry: key={}", key);
        }
        DocumentBucket docRemoved = docStore.remove(key);
        if (docRemoved != null) {
            removed = true;
            log.debug("Deleted document entry: key={}", key);
        }
        if (removed) {
            eventBus.publish(key, EventType.DELETE, null);
        }
    }

    /**
     * 返回匹配指定前缀的所有 key（KV + 文档合并去重）。
     * 使用 {@link LinkedHashSet} 保持顺序。
     *
     * @param prefix key 前缀
     * @return 匹配的 key 集合
     */
    public Set<String> listKeys(String prefix) {
        Objects.requireNonNull(prefix, "prefix must not be null");

        Set<String> keys = new LinkedHashSet<>();
        for (String key : kvStore.keySet()) {
            if (key.startsWith(prefix)) {
                keys.add(key);
            }
        }
        for (String key : docStore.keySet()) {
            if (key.startsWith(prefix)) {
                keys.add(key);
            }
        }
        return keys;
    }

    /**
     * 清空所有数据（使用 writeLock 保证排他）。
     */
    public void clear() {
        rwLock.writeLock().lock();
        try {
            kvStore.clear();
            docStore.clear();
            log.debug("Cleared all workspace data");
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 返回总条目数（KV + 文档）。
     *
     * @return 总条目数
     */
    public int size() {
        return kvStore.size() + docStore.size();
    }

    /**
     * 获取事件总线引用（供 WorkspaceWatchTool 使用）。
     *
     * @return WorkspaceEventBus 实例
     */
    public WorkspaceEventBus getEventBus() {
        return eventBus;
    }

    // ==================== 淘汰策略 ====================

    /**
     * 淘汰 KV 存储中最旧的条目（按 createdAt 升序）。
     */
    private void evictOne() {
        kvStore.entrySet().stream()
                .min(Map.Entry.comparingByValue(Comparator.comparingLong(KVBucket::getCreatedAt)))
                .ifPresent(entry -> {
                    kvStore.remove(entry.getKey());
                    log.info("Evicted oldest KV entry: key={}, createdAt={}",
                            entry.getKey(), entry.getValue().getCreatedAt());
                });
    }

    /**
     * 淘汰文档存储中最旧的条目（按 createdAt 升序）。
     */
    private void evictOneDoc() {
        docStore.entrySet().stream()
                .min(Map.Entry.comparingByValue(Comparator.comparingLong(DocumentBucket::getCreatedAt)))
                .ifPresent(entry -> {
                    docStore.remove(entry.getKey());
                    log.info("Evicted oldest document entry: key={}, createdAt={}",
                            entry.getKey(), entry.getValue().getCreatedAt());
                });
    }
}
