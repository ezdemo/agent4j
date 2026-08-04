# LoopraAgent 定制接口改造记录

> 提交：`6f1c367b`（dev 分支）
> 日期：2026-08-04
> 范围：loopra-harness（LoopraAgent / SessionService），零行为变化的向后兼容改造

---

## 1. 背景

多模块化重构后，`LoopraAgent` 已从 loopra-bin 下沉到 loopra-harness（`site.sorghum.loopra.bin.agent.core.LoopraAgent`）。
它仍是私有构造 + Builder 的具体门面类，构造时**硬编码**了多个协作者的具体实现：

| 协作者 | 硬编码实现 | 所在模块 |
|--------|-----------|----------|
| Goal 守卫 | `GoalGuardImpl`（bin.goal） | harness |
| 工具策略 | `ConfigServiceToolPolicyProvider`（bin.config） | harness |
| 会话持久化 | `SessionService` 内部 `new JsonlSessionStore(sessionsDir)`（bin.session） | harness |

对应的 SPI 接口其实早已存在于 loopra-model（`AgentLoop.setGoalGuard(GoalGuard)`、
`ToolRegistry.setToolPolicyProvider(ToolPolicyProvider)` 均面向接口），只是 LoopraAgent 没有把注入口放出来，
导致 loopra-web 等上层模块无法替换这些行为。

## 2. 本次改动

原则：**开放注入点，默认值保持原行为，上层不注入则零变化。**

### 2.1 LoopraAgent.Builder 新增三个可选注入点

文件：`loopra-harness/src/main/java/site/sorghum/loopra/bin/agent/core/LoopraAgent.java`

```java
LoopraAgent agent = LoopraAgent.builder()
        .config(cfg)
        .modelClient(client)
        // ↓ 三个新注入点，均可选
        .goalGuard(myGoalGuard)                 // 默认 GoalGuardImpl
        .toolPolicyProvider(myPolicyProvider)   // 默认 ConfigServiceToolPolicyProvider
        .sessionStore(mySessionStore)           // 默认 工作区会话目录的 JsonlSessionStore
        .buildLightweight();
```

| Builder 方法 | 接口类型（loopra-model） | 未注入时的默认回退 |
|--------------|--------------------------|--------------------|
| `goalGuard(GoalGuard)` | `bin.agent.spi.GoalGuard` | `new GoalGuardImpl()` |
| `toolPolicyProvider(ToolPolicyProvider)` | `bin.agent.spi.ToolPolicyProvider` | `new ConfigServiceToolPolicyProvider()` |
| `sessionStore(SessionStore)` | `bin.session.SessionStore` | `new JsonlSessionStore(workspaceManager.getSessionsDir(...))` |

装配逻辑在 `initSessionAndLoop`（签名改为接收 Builder）：

```java
this.sessionService = (b.sessionStore != null)
        ? new SessionService(ctx, b.sessionStore)
        : new SessionService(ctx, sessionsDir);
...
agentLoop.setGoalGuard(b.goalGuard != null ? b.goalGuard : new GoalGuardImpl());
registry.setToolPolicyProvider(b.toolPolicyProvider != null
        ? b.toolPolicyProvider : new ConfigServiceToolPolicyProvider());
```

### 2.2 SessionService 新增注入构造

文件：`loopra-harness/src/main/java/site/sorghum/loopra/bin/session/SessionService.java`

```java
// 原构造保留，委托为默认 JSONL 存储
public SessionService(ConversationContext ctx, Path sessionsDir) throws IOException {
    this(ctx, new JsonlSessionStore(sessionsDir));
}

// 新构造：上层注入自定义 SessionStore
public SessionService(ConversationContext ctx, SessionStore store) {
    this.ctx = ctx;
    this.store = store;
    ctx.setSessionStore(store);
}
```

### 2.3 测试

- 新增 `SessionServiceTest.usesInjectedSessionStore`：验证注入的 store 同时装配到
  `SessionService.getStore()` 与 `ctx.getSessionStore()`（同一性断言）。
- 全量验证：`mvn clean package` BUILD SUCCESS（loopra-model 145 / loopra-harness 123 / loopra-web 31，共 299 测试 0 失败）。
- fat jar 冒烟：`/api/system/health` 200、`/api/tools` 42 个、`/index.html` 200。

### 2.4 模块边界

- 三个 SPI 接口全部在 **loopra-model**，loopra-model 未新增任何对上层模块的依赖；
- 默认实现的装配全部在 **loopra-harness**；
- loopra-web / loopra-acp / CLI 无需任何改动。

## 3. 遗留问题与后续计划（按影响排序）

> 以下问题本次**未修复**，留待后续。推荐处理顺序：1 → 2，3/4 等 web 有真实需求再做。

### 3.1 ToolRegistry 仍硬编码，且 web 存在冗余初始化【建议优先做】

- LoopraAgent 构造时**每个 Agent 都完整重跑** `ToolSystemInitializer.initialize(...)`（扫技能、建工具）；
- web 的 `AgentService.buildSharedComponents` 算出的 `sharedToolRegistry` **只用于 `isReady()`，从未传给 Agent**
  （loopra-web `AgentService.java` 约 :318 / :473）；
- Builder 缺 `toolRegistry(...)` 注入点，web 无法定制工具装配，多会话时重复初始化浪费。

**建议**：加 `Builder.toolRegistry(ToolRegistry)`，注入时跳过 `ToolSystemInitializer.initialize`；
web 的 `createAgent` 改为传入共享 registry，删除冗余字段或让其真正生效。

### 3.2 SessionStore 生命周期归属未定义【建议接着做】

- `JsonlSessionStore.shutdown()` 能释放消费者线程 + 定时刷入 scheduler + writer（约 :394），
  但该方法**不在 `SessionStore` 接口上**，`LoopraAgent.dispose()` 也不调用；
- web 的 LRU 每淘汰一个 Agent 就泄漏一组线程/scheduler（daemon 不阻塞 JVM 退出，但长期累积）；
- store 可注入后归属更需明确：注入**共享** store 时 Agent 不应关，**专属** store 时应该关。

**建议**：`SessionStore` 加 `default void shutdown() {}`；LoopraAgent 记录 store 是否自建
（`b.sessionStore == null` 即自建），dispose 时仅关闭自建 store。

### 3.3 LoopraAgent 本身仍是具体类，"抽接口"目标只完成一半

- web 直接依赖具体类，无法替换/mock；
- 700 行门面含大量透传方法（`addUsage` / `retryLast` / `rewind` …），泄漏内部结构。

**建议**（有真实需求时）：抽 `Agent` 接口（chat / bindSession / setListener / setOutput / abort /
flushSession / saveUsage / dispose 等公共 API），LoopraAgent 作为默认实现。

### 3.4 SessionService 是具体类，会话编排逻辑不可换

注入点只解决了"存哪"，没解决"怎么管"：标题生成（截首条消息的启发式）、用量统计等仍钉死。
web 若想要 LLM 生成标题等定制，需进一步抽接口。

### 3.5 新注入点的测试证据偏薄

- `goalGuard` / `toolPolicyProvider` 注入 → 装配进 AgentLoop/ToolRegistry 这条路径目前只有
  code-review 级证据（`loop` 字段私有无 getter，外部难断言）；
- 兜底来自"默认路径等价 + 全量测试 + 冒烟"。后续可给 LoopraAgent 补最小装配测试（stub ModelClient）。

### 3.6 其他小问题

- 构造器硬读 `System.getenv("LOOPRA_SESSION")`（CLI 习惯泄漏，web 场景无害，bindSession 会覆盖）；
- 每个 Agent 自注册 `config.changed` Dami 监听，靠 dispose 注销，漏调 dispose 会泄漏监听器。

## 4. 相关文件索引

| 文件 | 说明 |
|------|------|
| `loopra-harness/.../bin/agent/core/LoopraAgent.java` | Builder 注入点 + initSessionAndLoop 装配 |
| `loopra-harness/.../bin/session/SessionService.java` | (ctx, SessionStore) 注入构造 |
| `loopra-harness/.../test/.../session/SessionServiceTest.java` | 注入装配测试 |
| `loopra-model/.../bin/agent/spi/GoalGuard.java` | Goal 守卫 SPI |
| `loopra-model/.../bin/agent/spi/ToolPolicyProvider.java` | 工具策略 SPI |
| `loopra-model/.../bin/session/SessionStore.java` | 会话存储 SPI |
| `loopra-harness/.../bin/session/JsonlSessionStore.java` | 默认 JSONL 实现（含 shutdown） |
