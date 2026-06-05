# Agent间通信：共享工作区（黑板架构）设计文档

**日期**：2026-06-05  
**状态**：设计已批准  
**作者**：Agent4j 自动生成  

---

## 1. 概述

### 1.1 问题描述

当前 Agent4j 支持通过 `task` / `multi_task` 工具创建隔离子代理，但子代理之间、子代理与主代理之间**缺乏通信机制**。每个 SubAgent 在孤立上下文中执行，无法：

- 共享项目上下文和中间结果
- 协作完成复杂任务（如一个Agent写代码，另一个审查）
- 向 Manager Agent 报告进度和状态

### 1.2 目标

引入 **共享工作区（Shared Workspace / Blackboard Architecture）**，使 Agent4j 的多个 Agent 能通过一个共享的数据空间协作，实现：

- Agent 间数据共享（读写共有信息）
- 事件驱动的协作模式（订阅变更通知）
- 任务进度追踪与状态管理
- 零侵入式集成到现有 SubAgent 架构

### 1.3 非目标

- 不实现 Agent-to-Agent 直接消息传递（通过共享空间间接实现）
- 不实现持久化存储（当前为内存方案，未来可扩展）
- 不实现分布式通信（当前为单进程方案）

---

## 2. 架构设计

### 2.1 总体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                     SharedWorkspace                              │
│                                                                  │
│  ┌──────────────────────┐  ┌─────────────────────────────────┐   │
│  │   KV Store           │  │   Document Store                 │   │
│  │                      │  │                                  │   │
│  │  task:status         │  │  architecture.md  (text/markdown)│   │
│  │  task:module-a/result│  │  module-a/code    (text/java)    │   │
│  │  shared/context      │  │  test/report      (text/markdown)│   │
│  │  config/model        │  │  design/spec      (text/markdown)│   │
│  └──────────┬───────────┘  └──────────┬──────────────────────┘   │
│             │                         │                          │
│  ┌──────────▼─────────────────────────▼──────────────────────┐   │
│  │                    EventBus                                │   │
│  │  watch("task/*/status") → callback when status changes    │   │
│  │  watch("shared/*") → callback when shared data changes    │   │
│  │  Events: WRITE | UPDATE | DELETE                          │   │
│  └───────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
          ▲                          ▲
          │                          │
    ┌─────┴──────┐           ┌──────┴──────┐
    │ Manager    │           │ Worker-1    │ ... Worker-N
    │ Agent      │           │ Agent       │
    │            │           │             │
    │ workspace_ │           │ workspace_  │
    │ write/read │           │ write/read  │
    │ workspace_ │           │ workspace_  │
    │ watch      │           │ watch       │
    └────────────┘           └─────────────┘
```

### 2.2 核心组件

#### 2.2.1 SharedWorkspace

- **包路径**：`site.sorghum.agent4j.bin.workspace`
- **线程安全**：`ConcurrentHashMap` + `ReadWriteLock`
- **数据模型**：

```java
// KV 条目
class KVBucket {
    String value;           // 值
    String creator;         // 创建者 Agent 名称
    long createdAt;         // 创建时间戳
    long updatedAt;         // 更新时间戳
    int version;            // 版本号（每次递增）
    long ttlMs;             // 可选 TTL（-1 表示永不超时）
}

// 文档条目
class DocumentBucket {
    String content;         // 文档内容
    String mimeType;        // text/markdown, text/java, application/json 等
    String creator;         // 创建者
    long createdAt;
    long updatedAt;
    int version;
    Map<String, String> metadata; // 自定义元数据
    long ttlMs;
}
```

- **作用域**：
  - `global` — 所有 Agent 可见（默认 scope）
  - `task:{taskId}` — 仅特定任务内的 Agent 可见

#### 2.2.2 WorkspaceEventBus

- **包路径**：`site.sorghum.agent4j.bin.workspace`
- **原理**：观察者模式，支持通配符路径匹配
- **API**：

```java
// 订阅变更
String watch(String keyPattern, WatchHandler handler);
// 取消订阅
void unwatch(String subscriptionId);
// 触发事件（内部由 write/delete 自动调用）
void emit(String key, EventType type);
```

- **通配符支持**：`*` 匹配单级，`**` 匹配多级
  - `task/*/status` → 匹配 `task/module-a/status`
  - `task/**` → 匹配所有 task 开头的键
  - `*` → 匹配所有一级键

#### 2.2.3 工具层

新增 4 个工具（注册到 ToolRegistry）：

| 工具名 | 只读 | 参数 | 说明 |
|--------|:----:|------|------|
| `workspace_write` | ❌ | `key`, `value` 或 `content` + `type`, `scope`(可选) | 写入 KV 或文档 |
| `workspace_read` | ✅ | `key`, `scope`(可选) | 读取条目内容 |
| `workspace_watch` | ❌ | `keyPattern`, `scope`(可选) | 订阅变更，返回 subscriptionId |
| `workspace_list` | ✅ | `prefix`(可选), `scope`(可选) | 列出匹配的键 |

---

## 3. 详细设计

### 3.1 数据模型

```java
// === SharedWorkspace.java ===

/**
 * 共享工作区核心类。
 * 线程安全，支持 KV 和文档两种存储模式。
 */
public class SharedWorkspace {
    
    /** KV 存储：key → KVBucket */
    private final ConcurrentHashMap<String, KVBucket> kvStore = new ConcurrentHashMap<>();
    
    /** 文档存储：key → DocumentBucket */
    private final ConcurrentHashMap<String, DocumentBucket> docStore = new ConcurrentHashMap<>();
    
    /** 事件总线 */
    private final WorkspaceEventBus eventBus = new WorkspaceEventBus();
    
    // ===== KV 操作 =====
    
    /** 写入 KV 条目 */
    public void writeKV(String key, String value, String creator) { ... }
    /** 读取 KV 条目 */
    public Optional<String> readKV(String key) { ... }
    
    // ===== 文档操作 =====
    
    /** 写入文档条目 */
    public void writeDoc(String key, String content, String mimeType, String creator) { ... }
    /** 读取文档条目（返回完整 bucket） */
    public Optional<DocumentBucket> readDoc(String key) { ... }
    
    // ===== 通用操作 =====
    
    /** 列出键（支持前缀匹配） */
    public Set<String> listKeys(String prefix) { ... }
    /** 删除条目 */
    public void delete(String key) { ... }
    /** 清空工作区 */
    public void clear() { ... }
    
    // ===== 事件订阅 =====
    
    public String watch(String keyPattern, WatchHandler handler) { ... }
    public void unwatch(String subscriptionId) { ... }
}
```

### 3.2 事件模型

```java
public enum EventType { WRITE, UPDATE, DELETE }

@FunctionalInterface
public interface WatchHandler {
    void onEvent(String key, EventType type, Object value);
}

public class WorkspaceEventBus {
    // subscriptionId → Watcher
    private final ConcurrentHashMap<String, Watcher> watchers = new ConcurrentHashMap<>();
    
    public String subscribe(String keyPattern, WatchHandler handler) { ... }
    public void unsubscribe(String subscriptionId) { ... }
    public void publish(String key, EventType type, Object value) { ... }
    
    // 内部 Watcher 记录
    private static class Watcher {
        final Pattern keyPattern;  // 编译后的通配符模式
        final WatchHandler handler;
        final long createdAt;
    }
}
```

通配符转正则规则：
- `*` → `[^/]+`（匹配单级路径）
- `**` → `.*`（匹配多级路径）

### 3.3 工具实现

#### workspace_write

```java
@Component
public class WorkspaceWriteTool extends AgentTool {
    
    @Override
    public String getName() { return "workspace_write"; }
    
    @Override
    public List<ToolParameter> getParameters() {
        return Arrays.asList(
            new ToolParameter("key", "string", true, "条目路径，如 'task/module-a/status'"),
            // KV 模式
            new ToolParameter("value", "string", false, "KV 模式的值"),
            // 文档模式
            new ToolParameter("content", "string", false, "文档模式的内容"),
            new ToolParameter("type", "string", false, "文档 MIME 类型: text/markdown, text/java, application/json"),
            new ToolParameter("scope", "string", false, "作用域: global(默认), task:{id}")
        );
    }
    
    @Override
    public ToolResult execute(ToolContext ctx) {
        String key = ctx.getString("key");
        String value = ctx.getString("value");
        String content = ctx.getString("content");
        
        if (value != null) {
            // KV 写入
            workspace.writeKV(key, value, getAgentName(ctx));
        } else if (content != null) {
            // 文档写入
            workspace.writeDoc(key, content, 
                ctx.getString("type", "text/plain"),
                getAgentName(ctx));
        }
        // ...
    }
}
```

#### workspace_read

```java
@Component
public class WorkspaceReadTool extends AgentTool {
    
    @Override
    public String getName() { return "workspace_read"; }
    
    @Override
    public List<ToolParameter> getParameters() {
        return Arrays.asList(
            new ToolParameter("key", "string", true, "要读取的条目路径"),
            new ToolParameter("scope", "string", false, "作用域")
        );
    }
    
    @Override
    public ToolResult execute(ToolContext ctx) {
        String key = ctx.getString("key");
        
        // 尝试 KV
        Optional<String> kv = workspace.readKV(key);
        if (kv.isPresent()) {
            return ToolResult.ok("Value: " + kv.get());
        }
        
        // 尝试文档
        Optional<DocumentBucket> doc = workspace.readDoc(key);
        if (doc.isPresent()) {
            return ToolResult.ok(doc.get().content);
        }
        
        return ToolResult.fail("NOT_FOUND", "Key not found: " + key);
    }
}
```

### 3.4 与 SubAgent 的集成

在 `SubAgent.java` 中增加 `SharedWorkspace` 注入：

```java
public class SubAgent {
    // 新增 DENY 列表追加 workspace_watch（避免子代理长时间阻塞）
    public static final Set<String> SUB_AGENT_DENY = new HashSet<>(Arrays.asList(
            "task", "multi_task", "submit_plan", 
            "mark_step_complete", "revise_plan",
            "ask_choice", "todo_write",
            "workspace_watch"   // 子代理不允许 watch（可能阻塞）
    ));
    
    private final SharedWorkspace workspace;
    
    public SubAgent(ModelClient client, ToolRegistry parentRegistry, 
                    String systemPrompt, SharedWorkspace workspace) {
        this.client = client;
        this.registry = parentRegistry.copy();
        this.registry.setForceDenyTools(SUB_AGENT_DENY);
        // 注入 workspace 工具
        this.registry.register(new WorkspaceWriteTool(workspace));
        this.registry.register(new WorkspaceReadTool(workspace));
        this.registry.register(new WorkspaceListTool(workspace));
        this.systemPrompt = systemPrompt;
        this.workspace = workspace;
    }
}
```

---

## 4. 典型使用场景

### 4.1 项目任务分解与协作

```
Manager Agent:
  workspace_write("shared/context", "Spring Boot项目，需要实现用户模块")
  workspace_write("task/user-api/status", "pending")
  workspace_write("task/user-service/status", "pending")
  workspace_write("task/user-test/status", "pending")
  
  multi_task([
    {name:"api-designer", arguments:"设计用户API，参考 workspace:shared/context"},
    {name:"service-impl", arguments:"实现用户服务，参考 workspace:shared/context"},
    {name:"test-writer", arguments:"编写用户测试，参考 workspace:shared/context"}
  ])

Worker-A (api-designer):
  workspace_read("shared/context") → 获取上下文
  workspace_write("task/user-api/status", "running")
  ...设计API...
  workspace_write("artifacts/user-api/spec", "POST /users ...", type=text/markdown)
  workspace_write("task/user-api/status", "completed")
  
Worker-B (service-impl):
  workspace_watch("task/user-api/status") → 等待API设计完成
  ...读取API设计...
  workspace_write("task/user-service/status", "running")
  ...实现...
  workspace_write("task/user-service/status", "completed")

Manager (监控):
  workspace_watch("task/*/status") → 所有 completed 后聚合
  workspace_read("artifacts/user-api/spec")
  workspace_read("artifacts/user-service/code")
  → 合成最终交付
```

### 4.2 代码审查流水线

```
Coder Agent:
  workspace_write("review/request/module-x/status", "pending_review")
  workspace_write("review/request/module-x/code", "...代码内容...")

Reviewer Agent (通过 workspace_watch 感知):
  workspace_watch("review/request/*/status")
  → 收到 "pending_review" 通知
  workspace_read("review/request/module-x/code")
  workspace_write("review/request/module-x/review", "审查意见...")
  workspace_write("review/request/module-x/status", "reviewed")

Coder Agent:
  workspace_watch("review/request/module-x/status")
  → 收到 "reviewed" 通知
  workspace_read("review/request/module-x/review")
  → 根据反馈修改代码
  workspace_write("review/request/module-x/status", "fixed")
```

---

## 5. 与 MultiTaskTool 的配合

`MultiTaskTool` 需要增强以支持 SharedWorkspace：

```java
public class MultiTaskTool extends AgentTool {
    
    @Inject
    private SharedWorkspace sharedWorkspace;
    
    @Override
    public ToolResult execute(ToolContext ctx) {
        // ... 原有逻辑 ...
        
        for (Map<String, Object> taskDef : taskList) {
            futures.add(CompletableFuture.supplyAsync(() ->
                executeSingleSubAgent(name, arguments, customSystemPrompt, 
                                    registry, parentOutput, sharedWorkspace)
            ));
        }
        
        // ... 等待并收集 ...
    }
    
    private SubAgentResult executeSingleSubAgent(..., SharedWorkspace workspace) {
        SubAgent sub = new SubAgent(modelClient, registry, systemPrompt, workspace);
        // ...
    }
}
```

同步增强 `toToolSpec()` 文档，让 LLM 知道 workspace 可用。

---

## 6. 安全考虑

| 风险 | 缓解措施 |
|------|---------|
| **键冲突** | 路径命名约定：`task/{id}/...`, `shared/...`, `artifacts/...` |
| **数据污染** | Agent 写入时自动附加 creator 身份 |
| **无限watch** | watch 强制超时（默认 5 分钟自动取消） |
| **内存溢出** | 设置最大条目数（默认 1000），超限自动淘汰最旧条目 |
| **并发写入** | ConcurrentHashMap + 版本号乐观锁 |

---

## 7. 实施计划

### Phase 1：核心实现（预计 1-2 天）

| 步骤 | 文件 | 说明 |
|------|------|------|
| 1 | `SharedWorkspace.java` | 核心存储类 + KV/文档操作 |
| 2 | `WorkspaceEventBus.java` | 事件总线 + 通配符匹配 |
| 3 | `WorkspaceWriteTool.java` | 写入工具 |
| 4 | `WorkspaceReadTool.java` | 读取工具 |
| 5 | `WorkspaceListTool.java` | 列表工具 |
| 6 | `WorkspaceWatchTool.java` | 订阅工具 |

### Phase 2：集成（预计 0.5 天）

| 步骤 | 文件 | 说明 |
|------|------|------|
| 7 | `SubAgent.java` | 增加 workspace 注入 |
| 8 | `MultiTaskTool.java` | 传递 workspace 引用 |
| 9 | `TaskTool.java` | 传递 workspace 引用 |
| 10 | `ToolSystemInitializer.java` | 注册 workspace 工具 |

### Phase 3：测试（预计 0.5 天）

| 步骤 | 说明 |
|------|------|
| 11 | SharedWorkspace 单元测试 |
| 12 | 工具类的单元测试 |
| 13 | 集成测试：Manager→Worker→结果聚合 |
| 14 | 并发安全测试 |

---

## 8. 未来扩展

- **持久化工作区**：将数据持久化到 JSONL 文件，支持会话间共享
- **远程工作区**：通过 REST API 访问，支持跨进程 Agent 通信
- **工作区快照**：支持保存/恢复工作区状态
- **访问控制**：基于 Agent 角色的读写权限
- **分布式工作区**：基于 Redis 等中间件实现
