# 共享工作区（黑板架构）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 Agent4j 的共享工作区（SharedWorkspace），使多个 Agent 能通过共享数据空间协作，支持 KV 存储、文档存储和事件通知。

**Architecture:** 在 `agent4j-bin` 模块新增 `workspace` 包，包含 SharedWorkspace（核心存储）、WorkspaceEventBus（事件总线），以及 4 个 workspace 工具。SubAgent 和 MultiTaskTool 增加 workspace 注入，工具注册表自动注册。

**Tech Stack:** Java 17, Solon, Snack4, JUnit 5

---

## 文件结构

```
创建:
  agent4j-bin/src/main/java/.../workspace/SharedWorkspace.java
  agent4j-bin/src/main/java/.../workspace/WorkspaceEventBus.java
  agent4j-bin/src/main/java/.../workspace/KVBucket.java
  agent4j-bin/src/main/java/.../workspace/DocumentBucket.java
  agent4j-bin/src/main/java/.../workspace/WatchHandler.java
  agent4j-bin/src/main/java/.../workspace/EventType.java
  
  agent4j-bin/src/main/java/.../builtin/WorkspaceWriteTool.java
  agent4j-bin/src/main/java/.../builtin/WorkspaceReadTool.java
  agent4j-bin/src/main/java/.../builtin/WorkspaceListTool.java
  agent4j-bin/src/main/java/.../builtin/WorkspaceWatchTool.java

修改:
  agent4j-bin/src/main/java/.../agent/SubAgent.java            # 增加 workspace 注入
  agent4j-bin/src/main/java/.../builtin/MultiTaskTool.java     # 传递 workspace 引用
  agent4j-bin/src/main/java/.../builtin/TaskTool.java          # 传递 workspace 引用
  agent4j-bin/src/main/java/.../tool/ToolSystemInitializer.java # 注册 workspace 工具
  
测试:
  agent4j-bin/src/test/java/.../workspace/SharedWorkspaceTest.java
  agent4j-bin/src/test/java/.../workspace/WorkspaceEventBusTest.java
```

---

### Task 1: 创建数据模型类 (KVBucket, DocumentBucket, EventType, WatchHandler)

**Files:**
- Create: `agent4j-bin/src/main/java/site/sorghum/agent4j/bin/workspace/KVBucket.java`
- Create: `agent4j-bin/src/main/java/site/sorghum/agent4j/bin/workspace/DocumentBucket.java`
- Create: `agent4j-bin/src/main/java/site/sorghum/agent4j/bin/workspace/EventType.java`
- Create: `agent4j-bin/src/main/java/site/sorghum/agent4j/bin/workspace/WatchHandler.java`

- [ ] **Step 1: Create KVBucket.java**

```java
package site.sorghum.agent4j.bin.workspace;

import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * KV 存储桶 —— 存储键值对及其元数据。
 */
@Data
@Builder
public class KVBucket {
    private String value;
    private String creator;
    private long createdAt;
    private long updatedAt;
    private int version;
    /** TTL 毫秒，-1 表示永不超时 */
    private long ttlMs;

    /** 附加元数据（KV 模式一般不需要，预留扩展） */
    @Builder.Default
    private Map<String, String> metadata = new ConcurrentHashMap<>();

    /** 是否已过期 */
    public boolean isExpired() {
        if (ttlMs < 0) return false;
        return System.currentTimeMillis() - createdAt > ttlMs;
    }
}
```

- [ ] **Step 2: Create DocumentBucket.java**

```java
package site.sorghum.agent4j.bin.workspace;

import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文档存储桶 —— 存储文档内容及其元数据。
 */
@Data
@Builder
public class DocumentBucket {
    private String content;
    /** MIME 类型：text/markdown, text/java, application/json 等 */
    private String mimeType;
    private String creator;
    private long createdAt;
    private long updatedAt;
    private int version;
    /** TTL 毫秒，-1 表示永不超时 */
    private long ttlMs;

    @Builder.Default
    private Map<String, String> metadata = new ConcurrentHashMap<>();

    public boolean isExpired() {
        if (ttlMs < 0) return false;
        return System.currentTimeMillis() - createdAt > ttlMs;
    }
}
```

- [ ] **Step 3: Create EventType.java**

```java
package site.sorghum.agent4j.bin.workspace;

/**
 * 工作区事件类型。
 */
public enum EventType {
    /** 新建条目 */
    WRITE,
    /** 更新已有条目 */
    UPDATE,
    /** 删除条目 */
    DELETE
}
```

- [ ] **Step 4: Create WatchHandler.java**

```java
package site.sorghum.agent4j.bin.workspace;

/**
 * 工作区变更通知回调接口。
 */
@FunctionalInterface
public interface WatchHandler {
    /**
     * 当匹配的键发生变更时调用。
     *
     * @param key   变更的键
     * @param type  事件类型
     * @param value 变更后的值（KV 为 String，文档为 DocumentBucket，删除时为 null）
     */
    void onEvent(String key, EventType type, Object value);
}
```

- [ ] **Step 5: Commit**

```bash
git add agent4j-bin/src/main/java/site/sorghum/agent4j/bin/workspace/
git commit -m "feat: add workspace data models (KVBucket, DocumentBucket, EventType, WatchHandler)"
```

---

### Task 2: 实现 WorkspaceEventBus（事件总线）

**Files:**
- Create: `agent4j-bin/src/main/java/site/sorghum/agent4j/bin/workspace/WorkspaceEventBus.java`

- [ ] **Step 1: Create WorkspaceEventBus.java**

```java
package site.sorghum.agent4j.bin.workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * 工作区事件总线 —— 支持通配符路径订阅。
 * <p>
 * 通配符规则：
 * <ul>
 *   <li>{@code *} 匹配单级路径（不含 /）</li>
 *   <li>{@code **} 匹配多级路径（含 /）</li>
 * </ul>
 * </p>
 */
public class WorkspaceEventBus {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceEventBus.class);

    /** 订阅者注册表：subscriptionId → Watcher */
    private final ConcurrentHashMap<String, Watcher> watchers = new ConcurrentHashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger(0);

    /** 通配符 → 正则 */
    private static Pattern wildcardToRegex(String pattern) {
        String regex = pattern
                .replace("**", "#TEMP_MULTI#")
                .replace("*", "[^/]+")
                .replace("#TEMP_MULTI#", ".*");
        return Pattern.compile("^" + regex + "$");
    }

    /**
     * 订阅工作区变更。
     *
     * @param keyPattern 键路径通配符，如 "task/*/status", "shared/**"
     * @param handler    事件处理回调
     * @return 订阅 ID（可用于取消订阅）
     */
    public String subscribe(String keyPattern, WatchHandler handler) {
        String id = "ws_watch_" + idCounter.incrementAndGet();
        Watcher watcher = new Watcher(keyPattern, wildcardToRegex(keyPattern), handler, System.currentTimeMillis());
        watchers.put(id, watcher);
        log.debug("Workspace watch subscribed: {} -> {}", id, keyPattern);
        return id;
    }

    /**
     * 取消订阅。
     *
     * @param subscriptionId 订阅 ID
     */
    public void unsubscribe(String subscriptionId) {
        watchers.remove(subscriptionId);
        log.debug("Workspace watch unsubscribed: {}", subscriptionId);
    }

    /**
     * 发布事件（由 SharedWorkspace 内部调用）。
     */
    public void publish(String key, EventType type, Object value) {
        for (Map.Entry<String, Watcher> entry : watchers.entrySet()) {
            Watcher w = entry.getValue();
            if (w.pattern.matcher(key).matches()) {
                try {
                    w.handler.onEvent(key, type, value);
                } catch (Exception e) {
                    log.warn("Workspace watch handler error for key={}, subId={}: {}", key, entry.getKey(), e.getMessage());
                }
            }
        }
    }

    /**
     * 获取当前活跃订阅数。
     */
    public int watcherCount() {
        return watchers.size();
    }

    /** 内部 Watcher 记录 */
    private record Watcher(String keyPattern, Pattern pattern, WatchHandler handler, long createdAt) {}
}
```

- [ ] **Step 2: Commit**

```bash
git add agent4j-bin/src/main/java/site/sorghum/agent4j/bin/workspace/WorkspaceEventBus.java
git commit -m "feat: implement WorkspaceEventBus with wildcard pattern matching"
```

---

### Task 3: 实现 SharedWorkspace（核心存储）

**Files:**
- Create: `agent4j-bin/src/main/java/site/sorghum/agent4j/bin/workspace/SharedWorkspace.java`

- [ ] **Step 1: Create SharedWorkspace.java**

```java
package site.sorghum.agent4j.bin.workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.noear.solon.annotation.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * 共享工作区（黑板架构核心）。
 * <p>
 * 线程安全的共享数据空间，支持 KV 和文档两种存储模式，
 * 并提供事件通知机制。
 * </p>
 *
 * <h3>作用域</h3>
 * <ul>
 *   <li>{@code global} — 所有 Agent 可见（默认）</li>
 *   <li>{@code task:{id}} — 仅特定任务内的 Agent 可见</li>
 * </ul>
 */
@Component
public class SharedWorkspace {

    private static final Logger log = LoggerFactory.getLogger(SharedWorkspace.class);

    /** 默认最大条目数 */
    private static final int DEFAULT_MAX_ENTRIES = 1000;

    /** KV 存储 */
    private final ConcurrentHashMap<String, KVBucket> kvStore = new ConcurrentHashMap<>();
    /** 文档存储 */
    private final ConcurrentHashMap<String, DocumentBucket> docStore = new ConcurrentHashMap<>();
    /** 事件总线 */
    private final WorkspaceEventBus eventBus = new WorkspaceEventBus();
    /** 读写锁（用于批量操作一致性） */
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    private final int maxEntries;

    public SharedWorkspace() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public SharedWorkspace(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    // ==================== 事件总线访问 ====================

    public WorkspaceEventBus getEventBus() {
        return eventBus;
    }

    // ==================== KV 操作 ====================

    /**
     * 写入 KV 条目。已存在则更新（版本递增）。
     */
    public void writeKV(String key, String value, String creator) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");

        rwLock.readLock().lock();
        try {
            KVBucket existing = kvStore.get(key);
            if (existing != null) {
                // 更新
                existing.setValue(value);
                existing.setUpdatedAt(System.currentTimeMillis());
                existing.setVersion(existing.getVersion() + 1);
                eventBus.publish(key, EventType.UPDATE, value);
                log.debug("KV updated: {} = {} (by {})", key, truncate(value), creator);
            } else {
                // 新增（检查容量）
                if (kvStore.size() >= maxEntries) {
                    evictOne();
                }
                KVBucket bucket = KVBucket.builder()
                        .value(value)
                        .creator(creator != null ? creator : "unknown")
                        .createdAt(System.currentTimeMillis())
                        .updatedAt(System.currentTimeMillis())
                        .version(1)
                        .ttlMs(-1)
                        .build();
                kvStore.put(key, bucket);
                eventBus.publish(key, EventType.WRITE, value);
                log.debug("KV written: {} = {} (by {})", key, truncate(value), creator);
            }
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * 读取 KV 条目。
     */
    public Optional<String> readKV(String key) {
        KVBucket bucket = kvStore.get(key);
        if (bucket == null) return Optional.empty();
        if (bucket.isExpired()) {
            kvStore.remove(key);
            return Optional.empty();
        }
        return Optional.of(bucket.getValue());
    }

    /**
     * 读取 KV 完整桶信息（含元数据）。
     */
    public Optional<KVBucket> getKVBucket(String key) {
        KVBucket bucket = kvStore.get(key);
        if (bucket == null) return Optional.empty();
        if (bucket.isExpired()) {
            kvStore.remove(key);
            return Optional.empty();
        }
        return Optional.of(bucket);
    }

    // ==================== 文档操作 ====================

    /**
     * 写入文档条目。已存在则更新。
     */
    public void writeDoc(String key, String content, String mimeType, String creator) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(content, "content must not be null");

        rwLock.readLock().lock();
        try {
            DocumentBucket existing = docStore.get(key);
            if (existing != null) {
                existing.setContent(content);
                existing.setMimeType(mimeType != null ? mimeType : "text/plain");
                existing.setUpdatedAt(System.currentTimeMillis());
                existing.setVersion(existing.getVersion() + 1);
                eventBus.publish(key, EventType.UPDATE, existing);
                log.debug("Doc updated: {} ({} bytes, by {})", key, content.length(), creator);
            } else {
                if (docStore.size() >= maxEntries) {
                    evictOneDoc();
                }
                DocumentBucket bucket = DocumentBucket.builder()
                        .content(content)
                        .mimeType(mimeType != null ? mimeType : "text/plain")
                        .creator(creator != null ? creator : "unknown")
                        .createdAt(System.currentTimeMillis())
                        .updatedAt(System.currentTimeMillis())
                        .version(1)
                        .ttlMs(-1)
                        .build();
                docStore.put(key, bucket);
                eventBus.publish(key, EventType.WRITE, bucket);
                log.debug("Doc written: {} ({} bytes, by {})", key, content.length(), creator);
            }
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * 读取文档完整桶信息。
     */
    public Optional<DocumentBucket> readDoc(String key) {
        DocumentBucket bucket = docStore.get(key);
        if (bucket == null) return Optional.empty();
        if (bucket.isExpired()) {
            docStore.remove(key);
            return Optional.empty();
        }
        return Optional.of(bucket);
    }

    // ==================== 通用操作 ====================

    /**
     * 删除条目（同时检查 KV 和文档存储）。
     */
    public void delete(String key) {
        boolean removed = false;
        if (kvStore.remove(key) != null) {
            eventBus.publish(key, EventType.DELETE, null);
            removed = true;
        }
        if (docStore.remove(key) != null) {
            eventBus.publish(key, EventType.DELETE, null);
            removed = true;
        }
        if (removed) {
            log.debug("Deleted: {}", key);
        }
    }

    /**
     * 列出匹配前缀的所有键。
     */
    public Set<String> listKeys(String prefix) {
        Set<String> keys = new LinkedHashSet<>();
        String p = prefix != null ? prefix : "";
        for (String key : kvStore.keySet()) {
            if (key.startsWith(p)) keys.add(key);
        }
        for (String key : docStore.keySet()) {
            if (key.startsWith(p)) keys.add(key);
        }
        return keys;
    }

    /**
     * 清空工作区。
     */
    public void clear() {
        rwLock.writeLock().lock();
        try {
            kvStore.clear();
            docStore.clear();
            log.debug("Workspace cleared");
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 获取当前条目数。
     */
    public int size() {
        return kvStore.size() + docStore.size();
    }

    // ==================== 内部方法 ====================

    private void evictOne() {
        // 删除最旧的 KV 条目
        kvStore.entrySet().stream()
                .min(Map.Entry.comparingByValue(Comparator.comparingLong(KVBucket::getCreatedAt)))
                .ifPresent(entry -> {
                    kvStore.remove(entry.getKey());
                    log.debug("Evicted oldest KV: {}", entry.getKey());
                });
    }

    private void evictOneDoc() {
        docStore.entrySet().stream()
                .min(Map.Entry.comparingByValue(Comparator.comparingLong(DocumentBucket::getCreatedAt)))
                .ifPresent(entry -> {
                    docStore.remove(entry.getKey());
                    log.debug("Evicted oldest doc: {}", entry.getKey());
                });
    }

    private static String truncate(String s) {
        if (s == null) return "null";
        return s.length() > 50 ? s.substring(0, 47) + "..." : s;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add agent4j-bin/src/main/java/site/sorghum/agent4j/bin/workspace/SharedWorkspace.java
git commit -m "feat: implement SharedWorkspace with KV and document storage"
```

---

### Task 4: 实现 WorkspaceWriteTool

**Files:**
- Create: `agent4j-bin/src/main/java/site/sorghum/agent4j/bin/builtin/WorkspaceWriteTool.java`

- [ ] **Step 1: Create WorkspaceWriteTool.java**

```java
package site.sorghum.agent4j.bin.builtin;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.bin.workspace.SharedWorkspace;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.util.Arrays;
import java.util.List;

/**
 * workspace_write —— 写入 KV 或文档到共享工作区。
 */
@Component
public class WorkspaceWriteTool extends AgentTool {

    @Inject
    private SharedWorkspace workspace;

    @Override
    public String getName() {
        return "workspace_write";
    }

    @Override
    public String getDescription() {
        return "Write a KV pair or document to the shared workspace. "
                + "All agents see global-scope entries. "
                + "Use 'value' for KV mode (simple string), or 'content'+'type' for document mode.";
    }

    @Override
    public String toToolSpec() {
        return """
                ### workspace_write
                
                写入 KV 或文档到共享工作区。所有 Agent 可见。
                使用 value 参数为 KV 模式（简单字符串），使用 content+type 为文档模式。
                参数: key(必填), value(可选), content(可选), type(可选, 如 text/markdown), creator(自动).
                """;
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Arrays.asList(
                new ToolParameter("key", "string", true, "条目路径，如 'task/module-a/status' 或 'shared/context'"),
                new ToolParameter("value", "string", false, "KV 模式的值（简单字符串）"),
                new ToolParameter("content", "string", false, "文档模式的内容"),
                new ToolParameter("type", "string", false, "文档 MIME 类型: text/markdown, text/java, application/json 等"),
                new ToolParameter("scope", "string", false, "作用域: global(默认)")
        );
    }

    @Override
    public ToolResult execute(ToolContext ctx) {
        String key = ctx.getString("key");
        if (key == null || key.isEmpty()) {
            return ToolResult.fail("PARAM_MISSING", "Missing required parameter 'key'");
        }

        String value = ctx.getString("value");
        String content = ctx.getString("content");
        String agentName = ctx.getAgentName() != null ? ctx.getAgentName() : "unknown";

        if (value != null) {
            // KV 模式
            workspace.writeKV(key, value, agentName);
            return ToolResult.ok("KV written: " + key + " = " + value);
        } else if (content != null) {
            // 文档模式
            String type = ctx.getString("type");
            if (type == null) type = "text/plain";
            workspace.writeDoc(key, content, type, agentName);
            return ToolResult.ok("Document written: " + key + " (" + content.length() + " bytes, type: " + type + ")");
        } else {
            return ToolResult.fail("PARAM_MISSING", "Either 'value' (KV mode) or 'content' (document mode) is required");
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add agent4j-bin/src/main/java/site/sorghum/agent4j/bin/builtin/WorkspaceWriteTool.java
git commit -m "feat: add WorkspaceWriteTool for KV and document write"
```

---

### Task 5: 实现 WorkspaceReadTool

**Files:**
- Create: `agent4j-bin/src/main/java/site/sorghum/agent4j/bin/builtin/WorkspaceReadTool.java`

- [ ] **Step 1: Create WorkspaceReadTool.java**

```java
package site.sorghum.agent4j.bin.builtin;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.bin.workspace.DocumentBucket;
import site.sorghum.agent4j.bin.workspace.KVBucket;
import site.sorghum.agent4j.bin.workspace.SharedWorkspace;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * workspace_read —— 从共享工作区读取条目。
 */
@Component
public class WorkspaceReadTool extends AgentTool {

    @Inject
    private SharedWorkspace workspace;

    @Override
    public String getName() {
        return "workspace_read";
    }

    @Override
    public String getDescription() {
        return "Read an entry from the shared workspace. "
                + "Returns the value (KV mode) or content (document mode). "
                + "Use 'list' parameter with 'prefix' to list matching keys.";
    }

    @Override
    public String toToolSpec() {
        return """
                ### workspace_read
                
                从共享工作区读取条目。自动探测 KV 或文档模式。
                参数: key(必填, 条目路径).
                """;
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Arrays.asList(
                new ToolParameter("key", "string", true, "要读取的条目路径，如 'shared/context' 或 'artifacts/user-api/spec'"),
                new ToolParameter("scope", "string", false, "作用域: global(默认)")
        );
    }

    @Override
    public ToolResult execute(ToolContext ctx) {
        String key = ctx.getString("key");
        if (key == null || key.isEmpty()) {
            return ToolResult.fail("PARAM_MISSING", "Missing required parameter 'key'");
        }

        // 先尝试 KV
        Optional<String> kvResult = workspace.readKV(key);
        if (kvResult.isPresent()) {
            return ToolResult.ok(kvResult.get());
        }

        // 再尝试文档
        Optional<DocumentBucket> docResult = workspace.readDoc(key);
        if (docResult.isPresent()) {
            DocumentBucket doc = docResult.get();
            StringBuilder sb = new StringBuilder();
            sb.append("--- ").append(doc.getMimeType()).append(" (v").append(doc.getVersion()).append(") ---\n");
            sb.append(doc.getContent());
            return ToolResult.ok(sb.toString());
        }

        // 都找不到
        String message = "Key not found: " + key;
        // 找相似 key 做提示
        var similar = workspace.listKeys("").stream()
                .filter(k -> k.contains(key) || key.contains(k))
                .limit(3)
                .toList();
        if (!similar.isEmpty()) {
            message += ". Similar keys: " + similar;
        }
        return ToolResult.fail("NOT_FOUND", message);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add agent4j-bin/src/main/java/site/sorghum/agent4j/bin/builtin/WorkspaceReadTool.java
git commit -m "feat: add WorkspaceReadTool for reading workspace entries"
```

---

### Task 6: 实现 WorkspaceListTool

**Files:**
- Create: `agent4j-bin/src/main/java/site/sorghum/agent4j/bin/builtin/WorkspaceListTool.java`

- [ ] **Step 1: Create WorkspaceListTool.java**

```java
package site.sorghum.agent4j.bin.builtin;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.bin.workspace.SharedWorkspace;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * workspace_list —— 列出共享工作区中的条目。
 */
@Component
public class WorkspaceListTool extends AgentTool {

    @Inject
    private SharedWorkspace workspace;

    @Override
    public String getName() {
        return "workspace_list";
    }

    @Override
    public String getDescription() {
        return "List entries in the shared workspace, optionally filtered by prefix.";
    }

    @Override
    public String toToolSpec() {
        return """
                ### workspace_list
                
                列出共享工作区中的条目，支持按前缀过滤。
                """;
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Arrays.asList(
                new ToolParameter("prefix", "string", false, "可选前缀过滤，如 'task/' 只显示任务相关条目"),
                new ToolParameter("scope", "string", false, "作用域: global(默认)")
        );
    }

    @Override
    public ToolResult execute(ToolContext ctx) {
        String prefix = ctx.getString("prefix");
        Set<String> keys = workspace.listKeys(prefix);

        if (keys.isEmpty()) {
            String msg = prefix != null ? "No entries found with prefix: " + prefix : "Workspace is empty";
            return ToolResult.ok(msg);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📋 Workspace Entries");
        if (prefix != null) sb.append(" (prefix: ").append(prefix).append(")");
        sb.append(":\n");
        int i = 1;
        for (String key : keys) {
            sb.append("  ").append(i++).append(". ").append(key).append("\n");
        }
        sb.append("Total: ").append(keys.size()).append(" entries");

        return ToolResult.ok(sb.toString());
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add agent4j-bin/src/main/java/site/sorghum/agent4j/bin/builtin/WorkspaceListTool.java
git commit -m "feat: add WorkspaceListTool for listing workspace entries"
```

---

### Task 7: 实现 WorkspaceWatchTool

**Files:**
- Create: `agent4j-bin/src/main/java/site/sorghum/agent4j/bin/builtin/WorkspaceWatchTool.java`

- [ ] **Step 1: Create WorkspaceWatchTool.java**

```java
package site.sorghum.agent4j.bin.builtin;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.bin.workspace.EventType;
import site.sorghum.agent4j.bin.workspace.SharedWorkspace;
import site.sorghum.agent4j.bin.workspace.WatchHandler;
import site.sorghum.agent4j.bin.workspace.WorkspaceEventBus;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * workspace_watch —— 订阅共享工作区变更。
 * <p>
 * 阻塞等待匹配的键发生变更，返回变更事件信息。
 * 默认超时 30 秒，可通过 timeout 参数调整。
 * </p>
 */
@Component
public class WorkspaceWatchTool extends AgentTool {

    @Inject
    private SharedWorkspace workspace;

    @Override
    public String getName() {
        return "workspace_watch";
    }

    @Override
    public String getDescription() {
        return "Watch for changes on workspace keys matching a pattern. "
                + "Blocks until a matching change occurs or timeout. "
                + "Useful for coordinating between agents (e.g., wait for task completion). "
                + "Returns the key, event type, and new value.";
    }

    @Override
    public String toToolSpec() {
        return """
                ### workspace_watch
                
                订阅共享工作区变更通知。阻塞等待匹配的键发生变更后返回。
                通配符: * 匹配单级, ** 匹配多级。
                参数: keyPattern(必填), timeout(可选, 默认30秒).
                返回: key + event type + value.
                """;
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Arrays.asList(
                new ToolParameter("keyPattern", "string", true,
                        "键路径通配符。* 匹配单级（如 task/*/status），** 匹配多级（如 task/**）"),
                new ToolParameter("timeout", "integer", false, "超时秒数（默认 30，最大 300）"),
                new ToolParameter("scope", "string", false, "作用域: global(默认)")
        );
    }

    @Override
    public ToolResult execute(ToolContext ctx) {
        String keyPattern = ctx.getString("keyPattern");
        if (keyPattern == null || keyPattern.isEmpty()) {
            return ToolResult.fail("PARAM_MISSING", "Missing required parameter 'keyPattern'");
        }

        int timeout = ctx.getInt("timeout", 30);
        if (timeout <= 0) timeout = 30;
        if (timeout > 300) timeout = 300; // 最大 5 分钟

        WorkspaceEventBus eventBus = workspace.getEventBus();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> resultKey = new AtomicReference<>();
        AtomicReference<String> resultEvent = new AtomicReference<>();
        AtomicReference<String> resultValue = new AtomicReference<>();
        AtomicReference<String> subIdRef = new AtomicReference<>();

        WatchHandler handler = (key, type, value) -> {
            resultKey.set(key);
            resultEvent.set(type.name());
            resultValue.set(value != null ? value.toString() : "(deleted)");
            // 取消订阅
            String sid = subIdRef.get();
            if (sid != null) {
                eventBus.unsubscribe(sid);
            }
            latch.countDown();
        };

        String subId = eventBus.subscribe(keyPattern, handler);
        subIdRef.set(subId);

        try {
            boolean triggered = latch.await(timeout, TimeUnit.SECONDS);
            if (!triggered) {
                eventBus.unsubscribe(subId);
                return ToolResult.fail("TIMEOUT", "Watch timed out after " + timeout + "s for pattern: " + keyPattern);
            }
            return ToolResult.ok("🔔 Event: key=" + resultKey.get()
                    + ", type=" + resultEvent.get()
                    + ", value=" + resultValue.get());
        } catch (InterruptedException e) {
            eventBus.unsubscribe(subId);
            Thread.currentThread().interrupt();
            return ToolResult.fail("INTERRUPTED", "Watch was interrupted");
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add agent4j-bin/src/main/java/site/sorghum/agent4j/bin/builtin/WorkspaceWatchTool.java
git commit -m "feat: add WorkspaceWatchTool for subscribing to workspace changes"
```

---

### Task 8: 修改 SubAgent 支持 workspace 注入

**Files:**
- Modify: `agent4j-bin/src/main/java/site/sorghum/agent4j/bin/agent/SubAgent.java`

- [ ] **Step 1: 编辑 SubAgent.java，增加 workspace 支持**

在 `SubAgent.java` 的 `SUB_AGENT_DENY` 集合中追加 `"workspace_watch"`：

```java
// 第34行附近，追加 workspace_watch 到禁止列表
public static final Set<String> SUB_AGENT_DENY = new HashSet<>(Arrays.asList(
        "task",                // 防止递归子代理 spawn
        "multi_task",          // 防止递归多子代理 spawn
        "submit_plan",         // 计划管理（主代理专用）
        "mark_step_complete",  // 计划管理（主代理专用）
        "revise_plan",         // 计划管理（主代理专用）
        "ask_choice",          // 用户交互（主代理专用）
        "todo_write",          // 会话任务跟踪（主代理专用）
        "workspace_watch"      // 子代理不允许阻塞式 watch
));
```

新增构造函数重载（接受 SharedWorkspace 参数）：

```java
// 在第82行构造函数后追加
/**
 * 构造函数（带 SharedWorkspace 支持）
 */
public SubAgent(ModelClient client, ToolRegistry parentRegistry, 
                String systemPrompt, SharedWorkspace workspace) {
    this(client, parentRegistry, systemPrompt);
    // 注册 workspace 工具到子代理的注册表
    this.registry.register(new site.sorghum.agent4j.bin.builtin.WorkspaceWriteTool(workspace));
    this.registry.register(new site.sorghum.agent4j.bin.builtin.WorkspaceReadTool(workspace));
    this.registry.register(new site.sorghum.agent4j.bin.builtin.WorkspaceListTool(workspace));
}
```

- [ ] **Step 2: Commit**

```bash
git add agent4j-bin/src/main/java/site/sorghum/agent4j/bin/agent/SubAgent.java
git commit -m "feat: add SharedWorkspace support to SubAgent"
```

---

### Task 9: 修改 TaskTool 和 MultiTaskTool 传递 workspace

**Files:**
- Modify: `agent4j-bin/src/main/java/site/sorghum/agent4j/bin/builtin/TaskTool.java`
- Modify: `agent4j-bin/src/main/java/site/sorghum/agent4j/bin/builtin/MultiTaskTool.java`

- [ ] **Step 1: 编辑 TaskTool.java，注入 SharedWorkspace 并传递给 SubAgent**

在第 82 行 `@Inject private ModelClient modelClient;` 后追加：

```java
@Inject
private SharedWorkspace sharedWorkspace;
```

修改 `execute` 方法中创建 SubAgent 的部分（第 173 行附近）：

```java
// 原代码：
// SubAgent sub = new SubAgent(modelClient, registry, systemPrompt);
// 修改为：
SubAgent sub = new SubAgent(modelClient, registry, systemPrompt, sharedWorkspace);
```

- [ ] **Step 2: 编辑 MultiTaskTool.java，同样注入 SharedWorkspace**

在第 38-39 行 `@Inject private ModelClient modelClient;` 后追加：

```java
@Inject
private SharedWorkspace sharedWorkspace;
```

修改 `executeSingleSubAgent` 方法中创建 SubAgent 的部分（第164行附近）：

```java
// 原代码：
// SubAgent sub = new SubAgent(modelClient, registry, systemPrompt);
// 修改为：
SubAgent sub = new SubAgent(modelClient, registry, systemPrompt, sharedWorkspace);
```

同时更新 `toToolSpec()` 文档，在 multi_task 描述中提示 workspace 可用：

```java
// 在 toToolSpec() 的文档中追加提示
参数: tasks(必填, JSON数组)，每个任务包含 name(必填), arguments(可选), systemPrompt(可选)。可写。
注意：子代理不可再创建子代理（task/multi_task 工具对子代理不可用）。
提示：子代理自动获得 workspace_write/workspace_read/workspace_list 工具，可通过共享工作区协作。
```

- [ ] **Step 3: 为 WorkspaceWriteTool 和 WorkspaceReadTool 增加可直接注入 workspace 的构造函数**

在 `WorkspaceWriteTool.java` 中添加：

```java
// 无参构造函数（Solon DI 用）
public WorkspaceWriteTool() {}

// 直接注入 workspace 的构造函数（SubAgent 创建工具实例用）
public WorkspaceWriteTool(SharedWorkspace workspace) {
    this.workspace = workspace;
}
```

同理在 `WorkspaceReadTool.java` 和 `WorkspaceListTool.java` 中添加类似构造函数。

```java
// WorkspaceReadTool.java
public WorkspaceReadTool() {}
public WorkspaceReadTool(SharedWorkspace workspace) {
    this.workspace = workspace;
}

// WorkspaceListTool.java
public WorkspaceListTool() {}
public WorkspaceListTool(SharedWorkspace workspace) {
    this.workspace = workspace;
}
```

- [ ] **Step 4: Commit**

```bash
git add agent4j-bin/src/main/java/site/sorghum/agent4j/bin/builtin/TaskTool.java
git add agent4j-bin/src/main/java/site/sorghum/agent4j/bin/builtin/MultiTaskTool.java
git add agent4j-bin/src/main/java/site/sorghum/agent4j/bin/builtin/WorkspaceWriteTool.java
git add agent4j-bin/src/main/java/site/sorghum/agent4j/bin/builtin/WorkspaceReadTool.java
git add agent4j-bin/src/main/java/site/sorghum/agent4j/bin/builtin/WorkspaceListTool.java
git commit -m "feat: integrate SharedWorkspace into TaskTool and MultiTaskTool"
```

---

### Task 10: 修改 ToolSystemInitializer 注册 workspace 工具

**Files:**
- Modify: `agent4j-bin/src/main/java/site/sorghum/agent4j/bin/tool/ToolSystemInitializer.java`

- [ ] **Step 1: 查看 ToolSystemInitializer.java 现有代码**

```bash
cat agent4j-bin/src/main/java/site/sorghum/agent4j/bin/tool/ToolSystemInitializer.java
```

确认是否已有自动扫描机制（如 `ToolScanUtil`）。如果有 `@Component` 扫描，则工具会自动注册。如果没有，手动添加注册代码。

- [ ] **Step 2: 按需修改**

如果使用自动扫描（`@Component`），则 workspace 工具已自动注册，无需额外操作。
如果手动注册，在注册列表中添加：

```java
registry.register(new WorkspaceWriteTool());
registry.register(new WorkspaceReadTool());
registry.register(new WorkspaceListTool());
registry.register(new WorkspaceWatchTool());
```

- [ ] **Step 3: Commit**

```bash
git add agent4j-bin/src/main/java/site/sorghum/agent4j/bin/tool/ToolSystemInitializer.java
git commit -m "feat: register workspace tools in ToolSystemInitializer"
```

---

### Task 11: 编写 SharedWorkspace 单元测试

**Files:**
- Create: `agent4j-bin/src/test/java/site/sorghum/agent4j/bin/workspace/SharedWorkspaceTest.java`

- [ ] **Step 1: Create SharedWorkspaceTest.java**

```java
package site.sorghum.agent4j.bin.workspace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SharedWorkspaceTest {

    private SharedWorkspace workspace;

    @BeforeEach
    void setUp() {
        workspace = new SharedWorkspace(100);
    }

    @Test
    void testWriteAndReadKV() {
        workspace.writeKV("test/key1", "value1", "tester");
        Optional<String> result = workspace.readKV("test/key1");
        assertTrue(result.isPresent());
        assertEquals("value1", result.get());
    }

    @Test
    void testWriteAndReadDoc() {
        workspace.writeDoc("test/doc1", "# Hello\nWorld", "text/markdown", "tester");
        Optional<DocumentBucket> result = workspace.readDoc("test/doc1");
        assertTrue(result.isPresent());
        assertEquals("# Hello\nWorld", result.get().getContent());
        assertEquals("text/markdown", result.get().getMimeType());
    }

    @Test
    void testUpdateKV() {
        workspace.writeKV("test/key1", "v1", "tester");
        workspace.writeKV("test/key1", "v2", "tester");
        Optional<String> result = workspace.readKV("test/key1");
        assertTrue(result.isPresent());
        assertEquals("v2", result.get());
        Optional<KVBucket> bucket = workspace.getKVBucket("test/key1");
        assertTrue(bucket.isPresent());
        assertEquals(2, bucket.get().getVersion());
    }

    @Test
    void testDelete() {
        workspace.writeKV("test/to-delete", "value", "tester");
        workspace.delete("test/to-delete");
        assertFalse(workspace.readKV("test/to-delete").isPresent());
    }

    @Test
    void testListKeys() {
        workspace.writeKV("task/a/status", "pending", "tester");
        workspace.writeKV("task/b/status", "running", "tester");
        workspace.writeDoc("artifacts/report.md", "content", "text/markdown", "tester");

        Set<String> allKeys = workspace.listKeys(null);
        assertEquals(3, allKeys.size());

        Set<String> taskKeys = workspace.listKeys("task/");
        assertEquals(2, taskKeys.size());

        Set<String> artKeys = workspace.listKeys("artifacts/");
        assertEquals(1, artKeys.size());
    }

    @Test
    void testClear() {
        workspace.writeKV("test/k1", "v1", "tester");
        workspace.writeDoc("test/d1", "content", "text/plain", "tester");
        workspace.clear();
        assertEquals(0, workspace.size());
    }

    @Test
    void testMaxEntriesEviction() {
        SharedWorkspace small = new SharedWorkspace(5);
        for (int i = 0; i < 10; i++) {
            small.writeKV("key" + i, "value" + i, "tester");
        }
        assertTrue(small.size() <= 5, "Should evict to stay under max entries");
    }

    @Test
    void testEventBusNotification() {
        final boolean[] notified = {false};
        workspace.getEventBus().subscribe("test/*", (key, type, value) -> {
            notified[0] = true;
        });
        workspace.writeKV("test/event-test", "hello", "tester");
        assertTrue(notified[0], "Write should trigger event");
    }

    @Test
    void testReadNonExistent() {
        assertFalse(workspace.readKV("non/existent").isPresent());
        assertFalse(workspace.readDoc("non/existent").isPresent());
    }
}
```

- [ ] **Step 2: 运行测试**

```bash
mvn test -pl agent4j-bin -Dtest="SharedWorkspaceTest" -q
```

预期输出：`Tests run: 9, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 3: Commit**

```bash
git add agent4j-bin/src/test/java/site/sorghum/agent4j/bin/workspace/SharedWorkspaceTest.java
git commit -m "test: add SharedWorkspace unit tests"
```

---

### Task 12: 编写 WorkspaceEventBus 单元测试

**Files:**
- Create: `agent4j-bin/src/test/java/site/sorghum/agent4j/bin/workspace/WorkspaceEventBusTest.java`

- [ ] **Step 1: Create WorkspaceEventBusTest.java**

```java
package site.sorghum.agent4j.bin.workspace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceEventBusTest {

    private WorkspaceEventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = new WorkspaceEventBus();
    }

    @Test
    void testExactMatch() {
        List<String> events = new ArrayList<>();
        eventBus.subscribe("task/status", (key, type, value) -> events.add(key));
        eventBus.publish("task/status", EventType.WRITE, "value");
        assertEquals(1, events.size());
    }

    @Test
    void testSingleLevelWildcard() {
        List<String> events = new ArrayList<>();
        eventBus.subscribe("task/*/status", (key, type, value) -> events.add(key));
        eventBus.publish("task/module-a/status", EventType.UPDATE, "done");
        eventBus.publish("task/module-b/status", EventType.UPDATE, "pending");
        assertEquals(2, events.size());
    }

    @Test
    void testMultiLevelWildcard() {
        List<String> events = new ArrayList<>();
        eventBus.subscribe("task/**", (key, type, value) -> events.add(key));
        eventBus.publish("task/a/status", EventType.WRITE, "x");
        eventBus.publish("task/a/b/c", EventType.WRITE, "y");
        assertEquals(2, events.size());
    }

    @Test
    void testNonMatching() {
        List<String> events = new ArrayList<>();
        eventBus.subscribe("task/*", (key, type, value) -> events.add(key));
        eventBus.publish("shared/context", EventType.WRITE, "x");
        assertEquals(0, events.size(), "Non-matching key should not trigger");
    }

    @Test
    void testUnsubscribe() {
        List<String> events = new ArrayList<>();
        String id = eventBus.subscribe("test/*", (key, type, value) -> events.add(key));
        eventBus.unsubscribe(id);
        eventBus.publish("test/key", EventType.WRITE, "x");
        assertEquals(0, events.size());
    }

    @Test
    void testMultipleSubscribers() {
        List<String> events1 = new ArrayList<>();
        List<String> events2 = new ArrayList<>();
        eventBus.subscribe("shared/*", (key, type, value) -> events1.add(key));
        eventBus.subscribe("shared/*", (key, type, value) -> events2.add(key));
        eventBus.publish("shared/context", EventType.WRITE, "ctx");
        assertEquals(1, events1.size());
        assertEquals(1, events2.size());
    }

    @Test
    void testWatcherCount() {
        assertEquals(0, eventBus.watcherCount());
        eventBus.subscribe("a/*", (k, t, v) -> {});
        eventBus.subscribe("b/*", (k, t, v) -> {});
        assertEquals(2, eventBus.watcherCount());
    }

    @Test
    void testEventTypePropagation() {
        final EventType[] capturedType = {null};
        eventBus.subscribe("test/*", (key, type, value) -> capturedType[0] = type);
        eventBus.publish("test/key", EventType.DELETE, null);
        assertEquals(EventType.DELETE, capturedType[0]);
    }
}
```

- [ ] **Step 2: 运行测试**

```bash
mvn test -pl agent4j-bin -Dtest="WorkspaceEventBusTest" -q
```

预期输出：`Tests run: 8, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 3: Commit**

```bash
git add agent4j-bin/src/test/java/site/sorghum/agent4j/bin/workspace/WorkspaceEventBusTest.java
git commit -m "test: add WorkspaceEventBus unit tests"
```

---

### Task 13: 运行所有测试验证

- [ ] **Step 1: 运行全部测试**

```bash
mvn test -q
```

预期输出：`BUILD SUCCESS`，全部测试通过。

- [ ] **Step 2: 编译验证**

```bash
mvn compile -pl agent4j-bin -am -q
```

预期输出：`BUILD SUCCESS`

- [ ] **Step 3: Commit 最终状态**

```bash
git add -A
git commit -m "feat: complete SharedWorkspace implementation with 4 workspace tools"
```
