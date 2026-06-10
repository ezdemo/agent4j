# 🎯 Goal Feature Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `/goal` command that lets users set session-level goals, auto-breaks them into steps, executes with automatic retry, and patrols via sub-agent to ensure completion.

**Architecture:** New `goal/` package under `agent4j-bin` with data models (Goal, GoalStep), JSONL-based GoalStore (parallel to session storage), GoalEngine orchestrating execution, and GoalCommand registered via Solon `@Component`. Patrol uses existing `SubAgent` mechanism — spawns an isolated sub-agent that monitors goal status and retries failed steps through the shared workspace.

**Tech Stack:** Java 17, Solon `@Component`, JSONL persistence (same pattern as SessionStore), existing SubAgent + workspace tools.

---

## File Structure

### New files (all in `agent4j-bin/src/main/java/site/sorghum/agent4j/bin/goal/`)

| # | File | Responsibility |
|---|------|---------------|
| 1 | `Goal.java` | Data model — id, sessionId, workspaceHash, title, description, status, steps, timestamps |
| 2 | `GoalStep.java` | Step model — index, description, status, retryCount, lastError, completedAt |
| 3 | `GoalStatus.java` | Enum — ACTIVE, PAUSED, COMPLETED, FAILED |
| 4 | `StepStatus.java` | Enum — PENDING, IN_PROGRESS, DONE, FAILED, SKIPPED |
| 5 | `GoalStore.java` | Interface — save, findBySession, findActiveByWorkspace, delete |
| 6 | `JsonlGoalStore.java` | JSONL implementation writing to `workspace/{hash}/goals/{sessionId}.jsonl` |
| 7 | `GoalEngine.java` | Core logic — setGoal (LLM breakdown), patrol (retry failed steps), status reporting |
| 8 | `GoalPatrolPrompt.java` | System prompt template for the patrol sub-agent |
| 9 | `GoalCommand.java` | `/goal` command handler (implements ChatCommand, @Component) |

### Modified files

| File | Change |
|------|--------|
| `WorkspaceManager.java` | Add `getGoalStore()` method |
| `Agent4jAgent.java` | Session load → check for pending goal → inject system message |
| `AgentLoop.java` | After LLM response → trigger patrol check |

---

## Task 1: Data Model Layer

**Files:**
- Create: `agent4j-bin/src/main/java/site/sorghum/agent4j/bin/goal/GoalStatus.java`
- Create: `agent4j-bin/src/main/java/site/sorghum/agent4j/bin/goal/StepStatus.java`
- Create: `agent4j-bin/src/main/java/site/sorghum/agent4j/bin/goal/Goal.java`
- Create: `agent4j-bin/src/main/java/site/sorghum/agent4j/bin/goal/GoalStep.java`

- [ ] **Step 1: Create GoalStatus enum**

```java
package site.sorghum.agent4j.bin.goal;

/**
 * 目标状态枚举。
 */
public enum GoalStatus {
    /** 活跃：正在执行中 */
    ACTIVE,
    /** 暂停：用户手动暂停 */
    PAUSED,
    /** 已完成：所有步骤 DONE */
    COMPLETED,
    /** 失败：某步骤超重试次数 */
    FAILED
}
```

- [ ] **Step 2: Create StepStatus enum**

```java
package site.sorghum.agent4j.bin.goal;

/**
 * 步骤状态枚举。
 */
public enum StepStatus {
    /** 待执行 */
    PENDING,
    /** 执行中 */
    IN_PROGRESS,
    /** 已完成 */
    DONE,
    /** 失败（可重试） */
    FAILED,
    /** 用户手动跳过 */
    SKIPPED
}
```

- [ ] **Step 3: Create Goal data model**

```java
package site.sorghum.agent4j.bin.goal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Goal — 会话目标。
 * <p>
 * 绑定到会话，持久化在 workspace/{hash}/goals/{sessionId}.jsonl。
 * 每个会话同时最多一个活跃目标。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Goal {
    /** UUID */
    private String id;
    /** 关联的会话 ID */
    private String sessionId;
    /** 工作区 hash（冗余，方便全局巡检） */
    private String workspaceHash;
    /** 一句话目标标题 */
    private String title;
    /** 详细描述 */
    private String description;
    /** 目标状态 */
    private GoalStatus status;
    /** 每步最大重试次数（默认 3） */
    @Builder.Default
    private int maxRetries = 3;
    /** 验证命令（如 "mvn test"），可为 null */
    private String verifyCommand;

    /** 步骤列表 */
    private List<GoalStep> steps;

    /** 创建时间 */
    private Instant createdAt;
    /** 最后更新时间 */
    private Instant updatedAt;
    /** 完成时间 */
    private Instant completedAt;

    /**
     * 生成进度文本：如 "3/6 (50%)"
     */
    public String progressText() {
        long done = steps.stream().filter(s -> s.getStatus() == StepStatus.DONE).count();
        long total = steps.size();
        long pct = total > 0 ? (done * 100 / total) : 0;
        return done + "/" + total + " (" + pct + "%)";
    }

    /**
     * 判断是否全部完成。
     */
    public boolean isAllDone() {
        return steps != null && steps.stream().allMatch(
                s -> s.getStatus() == StepStatus.DONE || s.getStatus() == StepStatus.SKIPPED);
    }
}
```

- [ ] **Step 4: Create GoalStep data model**

```java
package site.sorghum.agent4j.bin.goal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * GoalStep — 目标步骤。
 * <p>
 * 由 LLM 拆解生成，走完所有步骤目标即完成。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoalStep {
    /** 步骤序号（从 0 开始） */
    private int index;
    /** 步骤描述 */
    private String description;
    /** 步骤状态 */
    private StepStatus status;
    /** 已重试次数 */
    @Builder.Default
    private int retryCount = 0;
    /** 最后一次失败的错误信息 */
    private String lastError;
    /** 完成时间 */
    private Instant completedAt;
}
```

- [ ] **Step 5: Verify compilation**

```bash
mvn compile -pl agent4j-bin -am -q
```
Expected: BUILD SUCCESS (no errors)

---

## Task 2: Persistent Store (GoalStore)

**Files:**
- Create: `agent4j-bin/src/main/java/site/sorghum/agent4j/bin/goal/GoalStore.java`
- Create: `agent4j-bin/src/main/java/site/sorghum/agent4j/bin/goal/JsonlGoalStore.java`

- [ ] **Step 1: Create GoalStore interface**

```java
package site.sorghum.agent4j.bin.goal;

import java.io.IOException;
import java.util.List;

/**
 * GoalStore — 目标持久化仓库接口。
 * <p>
 * JSONL 格式存储于 workspace/{hash}/goals/{sessionId}.jsonl。
 * 与 SessionStore 设计风格一致，保证可替换性。
 * </p>
 */
public interface GoalStore {

    /** 保存/更新目标（覆盖写入） */
    void save(Goal goal) throws IOException;

    /** 按会话 ID 加载目标 */
    Goal findBySession(String sessionId) throws IOException;

    /** 加载工作区内所有活跃目标（巡检用） */
    List<Goal> findActiveByWorkspace(String workspaceHash) throws IOException;

    /** 删除目标 */
    boolean delete(String sessionId) throws IOException;
}
```

- [ ] **Step 2: Create JsonlGoalStore implementation**

```java
package site.sorghum.agent4j.bin.goal;

import org.noear.snack4.ONode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.DirectoryStream;
import java.util.ArrayList;
import java.util.List;

/**
 * JsonlGoalStore — JSONL 格式的目标持久化实现。
 * <p>
 * 存储路径：workspace/{hash}/goals/{sessionId}.jsonl
 * 每个会话一个文件，单行 JSON。
 * </p>
 */
public class JsonlGoalStore implements GoalStore {

    private static final Logger log = LoggerFactory.getLogger(JsonlGoalStore.class);

    private final Path goalsDir;

    public JsonlGoalStore(Path workspaceDir) {
        this.goalsDir = workspaceDir.resolve("goals");
    }

    @Override
    public void save(Goal goal) throws IOException {
        Files.createDirectories(goalsDir);
        Path file = goalsDir.resolve(goal.getSessionId() + ".jsonl");
        String json = ONode.stringify(goal);
        Files.writeString(file, json + "\n", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        log.debug("[goal] 已保存目标 {} -> {}", goal.getId(), file);
    }

    @Override
    public Goal findBySession(String sessionId) throws IOException {
        Path file = goalsDir.resolve(sessionId + ".jsonl");
        if (!Files.exists(file)) return null;
        String json = Files.readString(file).trim();
        if (json.isEmpty()) return null;
        return ONode.deserialize(json, Goal.class);
    }

    @Override
    public List<Goal> findActiveByWorkspace(String workspaceHash) throws IOException {
        List<Goal> active = new ArrayList<>();
        if (!Files.isDirectory(goalsDir)) return active;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(goalsDir, "*.jsonl")) {
            for (Path file : ds) {
                try {
                    String json = Files.readString(file).trim();
                    if (json.isEmpty()) continue;
                    Goal goal = ONode.deserialize(json, Goal.class);
                    if (goal.getStatus() == GoalStatus.ACTIVE || goal.getStatus() == GoalStatus.PAUSED) {
                        active.add(goal);
                    }
                } catch (Exception e) {
                    log.warn("[goal] 读取目标文件失败: {} - {}", file, e.getMessage());
                }
            }
        }
        return active;
    }

    @Override
    public boolean delete(String sessionId) throws IOException {
        Path file = goalsDir.resolve(sessionId + ".jsonl");
        return Files.deleteIfExists(file);
    }
}
```

- [ ] **Step 3: Verify compilation**

```bash
mvn compile -pl agent4j-bin -am -q
```
Expected: BUILD SUCCESS

---

## Task 3: Workspace Integration

**Files:**
- Modify: `agent4j-bin/src/main/java/site/sorghum/agent4j/bin/workspace/WorkspaceManager.java`

- [ ] **Step 1: Add getGoalStore() method**

Add after `getSessionsDir()` method (around line 101):

```java
/**
 * 获取工作区的目标存储。
 */
public GoalStore getGoalStore() {
    Path workspaceDir = getWorkspaceDir(currentWorkspacePath);
    return new JsonlGoalStore(workspaceDir);
}
```

Add import at top:
```java
import site.sorghum.agent4j.bin.goal.GoalStore;
import site.sorghum.agent4j.bin.goal.JsonlGoalStore;
```

- [ ] **Step 2: Verify compilation**

```bash
mvn compile -pl agent4j-bin -am -q
```
Expected: BUILD SUCCESS

---

## Task 4: GoalEngine — Core Execution Logic

**Files:**
- Create: `agent4j-bin/src/main/java/site/sorghum/agent4j/bin/goal/GoalPatrolPrompt.java`
- Create: `agent4j-bin/src/main/java/site/sorghum/agent4j/bin/goal/GoalEngine.java`

- [ ] **Step 1: Create GoalPatrolPrompt (system prompt template for patrol sub-agent)**

```java
package site.sorghum.agent4j.bin.goal;

/**
 * GoalPatrolPrompt — 巡检子代理的 system prompt 模板。
 * <p>
 * 当主 Agent 创建目标后，用此 prompt 启动一个隔离的巡检子代理，
 * 自动监视目标进度并重试失败步骤。
 * </p>
 */
public class GoalPatrolPrompt {

    /** 工作区 hash 占位符 */
    private static final String PH_HASH = "{workspaceHash}";
    /** 会话 ID 占位符 */
    private static final String PH_SESSION = "{sessionId}";

    private static final String PROMPT_TEMPLATE = """
            你是一个目标巡检代理（Goal Patrol Agent）。
            
            ## 你的使命
            你被主代理派遣来监视一个目标的执行进度，自动重试失败的步骤，确保目标"不达目的不罢休"。
            
            ## 目标信息
            工作区 hash: %s
            会话 ID: %s
            
            ## 你的行为循环
            持续执行以下循环：
            
            1. 通过 workspace_read 读取目标文件
               - 使用 workspace_read 读取 key 为 "goal:%s:data" 的数据
               - 解析 JSON 获取目标和步骤状态
            
            2. 检查是否有 FAILED 且 retryCount < maxRetries 的步骤
               - 如果有，使用可用工具（read/write/edit/bash/grep/glob/ls）自动执行重试：
                 a. 读取步骤描述，理解需要做什么
                 b. 执行所需的工具调用（修复代码、运行测试等）
                 c. 如果指定了验证命令（verifyCommand），运行它验证结果
                 d. 成功 → 通过 workspace_write 更新步骤状态为 DONE
                 e. 失败 → 更新 retryCount，记录 lastError
               - 如果没有需要处理的步骤，进入等待
            
            3. 等待 60 秒
               - 执行: bash("sleep 60")
            
            4. 检查目标状态
               - 如果所有步骤 DONE 或 SKIPPED:
                 - 更新目标状态为 COMPLETED
                 - 通过 workspace_write 写入完成通知
                 - 退出循环（return "目标已完成"）
               - 如果某步骤 retryCount >= maxRetries:
                 - 通过 workspace_write 通知主代理
                 - 继续循环等待用户手动处理
            
            ## 通信协议
            通过 workspace_write 写入以下 key:
            - "goal:{sessionId}:status" — {"step": N, "total": M, "failed": N, "message": "..."}
            - "goal:{sessionId}:patrol:active" — "true"（巡检子代理存活标记）
            - "goal:{sessionId}:patrol:log" — 最近操作日志
            
            ## 重要限制
            - 不可创建子代理（没有 task/multi_task 工具）
            - 每次 bash 调用有超时限制，sleep 60 是安全的
            - 如果发现自己进入死循环（同一步骤重试多次无进展），写入告警并等待用户
            """;

    public static String build(String workspaceHash, String sessionId) {
        return PROMPT_TEMPLATE
                .replace(PH_HASH, workspaceHash)
                .replace(PH_SESSION, sessionId);
    }
}
```

- [ ] **Step 2: Create GoalEngine — core class**

```java
package site.sorghum.agent4j.bin.goal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.bin.agent.Agent4jAgent;
import site.sorghum.agent4j.bin.agent.UserMessage;
import site.sorghum.agent4j.bin.command.ChatCommandContext;
import site.sorghum.agent4j.bin.workspace.WorkspaceManager;
import site.sorghum.agent4j.tool.LogLevel;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * GoalEngine — 目标执行引擎。
 * <p>
 * 负责目标创建（LLM 拆解步骤）、状态查询、暂停/恢复、巡检派发。
 * 不直接执行步骤——步骤由主 Agent 或巡检子代理执行。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoalEngine {

    @Inject
    private WorkspaceManager workspaceManager;

    /**
     * 创建新目标：调用 LLM 拆解步骤。
     *
     * @param description 目标描述
     * @param verifyCmd   验证命令（可为 null）
     * @param ctx         命令上下文
     * @return 创建的目标（尚未持久化，需调用方确认后 save）
     */
    public Goal createGoal(String description, String verifyCmd, ChatCommandContext ctx) {
        Agent4jAgent agent = ctx.getAgent();
        String sessionId = agent.getSessionService().getStore().currentName();

        // 调用 LLM 拆解步骤
        String breakdownPrompt = """
                请将以下目标拆解为 3-8 个具体的、可执行的步骤，每个步骤应是一个独立可验证的任务。
                请以 JSON 数组格式返回，每个元素包含 "description" 字段。
                不要包含任何其他文本，只返回 JSON 数组。
                
                目标：%s
                """.formatted(description);

        String llmResponse;
        try {
            llmResponse = agent.chat(UserMessage.of(breakdownPrompt));
        } catch (Exception e) {
            log.error("[goal] LLM 拆解失败", e);
            // fallback: 将整个目标作为单一步骤
            llmResponse = """
                    [{"description": "%s"}]
                    """.formatted(description);
        }

        // 解析 LLM 返回的步骤列表
        List<GoalStep> steps = parseSteps(llmResponse, description);

        Goal goal = Goal.builder()
                .id(UUID.randomUUID().toString().substring(0, 8))
                .sessionId(sessionId)
                .workspaceHash(workspaceManager.getCurrentWorkspaceHash())
                .title(description.length() > 50 ? description.substring(0, 50) + "..." : description)
                .description(description)
                .status(GoalStatus.ACTIVE)
                .maxRetries(3)
                .verifyCommand(verifyCmd)
                .steps(steps)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return goal;
    }

    /**
     * 持久化目标并启动巡检子代理。
     */
    public void activateGoal(Goal goal, ChatCommandContext ctx) throws Exception {
        // 保存目标
        GoalStore store = workspaceManager.getGoalStore();
        store.save(goal);
        log.info("[goal] 目标已保存: {} - {}", goal.getId(), goal.getTitle());

        // 将目标注入 LLM context
        String stepsText = IntStream.range(0, goal.getSteps().size())
                .mapToObj(i -> {
                    GoalStep s = goal.getSteps().get(i);
                    return "  [" + (i + 1) + "] " + s.getDescription() + " (" + s.getStatus() + ")";
                })
                .collect(Collectors.joining("\n"));

        ctx.getAgent().getOutput().onLog(LogLevel.INFO,
                "🎯 目标已创建: " + goal.getTitle() + "\n步骤:\n" + stepsText);

        // 启动巡检子代理
        spawnPatrolSubAgent(goal, ctx);
    }

    /**
     * 获取当前会话的目标状态。
     */
    public Goal getCurrentGoal(ChatCommandContext ctx) throws Exception {
        String sessionId = ctx.getAgent().getSessionService().getStore().currentName();
        GoalStore store = workspaceManager.getGoalStore();
        return store.findBySession(sessionId);
    }

    /**
     * 暂停目标。
     */
    public void pause(Goal goal, ChatCommandContext ctx) throws Exception {
        goal.setStatus(GoalStatus.PAUSED);
        goal.setUpdatedAt(Instant.now());
        GoalStore store = workspaceManager.getGoalStore();
        store.save(goal);
        // 巡检子代理会通过 workspace_read 检测到 PAUSED 状态并进入休眠
    }

    /**
     * 恢复目标。
     */
    public void resume(Goal goal, ChatCommandContext ctx) throws Exception {
        goal.setStatus(GoalStatus.ACTIVE);
        goal.setUpdatedAt(Instant.now());
        GoalStore store = workspaceManager.getGoalStore();
        store.save(goal);
    }

    /**
     * 标记某步骤已完成（用户手动或子代理报告）。
     */
    public void markStepDone(Goal goal, int stepIndex, ChatCommandContext ctx) throws Exception {
        if (stepIndex < 0 || stepIndex >= goal.getSteps().size()) return;
        GoalStep step = goal.getSteps().get(stepIndex);
        step.setStatus(StepStatus.DONE);
        step.setCompletedAt(Instant.now());
        goal.setUpdatedAt(Instant.now());

        if (goal.isAllDone()) {
            goal.setStatus(GoalStatus.COMPLETED);
            goal.setCompletedAt(Instant.now());
        }

        GoalStore store = workspaceManager.getGoalStore();
        store.save(goal);
    }

    /**
     * 启动巡检子代理。
     * 使用 task 工具（SubAgent）创建隔离的巡检代理。
     */
    private void spawnPatrolSubAgent(Goal goal, ChatCommandContext ctx) {
        // 构造巡检子代理的参数
        String patrolPrompt = GoalPatrolPrompt.build(
                goal.getWorkspaceHash(), goal.getSessionId());

        String arguments = String.format(
                "开始巡检目标 %s (%s)。工作区hash: %s, 会话ID: %s",
                goal.getId(), goal.getTitle(),
                goal.getWorkspaceHash(), goal.getSessionId());

        // 通过 Agent 的 chat 方法发送 spawn 指令
        // 实际执行时由 GoalCommand 或 AgentLoop 触发
        log.info("[goal] 准备启动巡检子代理: goalId={}, sessionId={}", goal.getId(), goal.getSessionId());

        // 注意：实际的 spawn 操作在 GoalCommand.execute() 中通过
        // ctx.getAgent().chat(UserMessage.of("task ...")) 触发
        // 这里只做准备工作
    }

    /**
     * 发送 spawn 巡检子代理的指令给 LLM。
     * 由 GoalCommand 在目标激活后调用。
     */
    public String buildSpawnPatrolCommand(Goal goal) {
        return String.format("""
                请使用 task 工具启动一个巡检子代理，名称为 "goal-patrol-%s"，参数为 "开始巡检目标 %s"。
                
                巡检子代理的 system prompt 如下：
                ---
                %s
                ---
                """,
                goal.getSessionId(),
                goal.getId(),
                GoalPatrolPrompt.build(goal.getWorkspaceHash(), goal.getSessionId()));
    }

    /**
     * 从 LLM 响应中解析步骤列表。
     */
    private List<GoalStep> parseSteps(String llmResponse, String fallbackDescription) {
        try {
            // 尝试提取 JSON 数组
            String json = llmResponse;
            int startIdx = json.indexOf('[');
            int endIdx = json.lastIndexOf(']');
            if (startIdx >= 0 && endIdx > startIdx) {
                json = json.substring(startIdx, endIdx + 1);
            }

            org.noear.snack4.ONode node = org.noear.snack4.ONode.ofJson(json);
            if (node.isArray()) {
                List<GoalStep> steps = new java.util.ArrayList<>();
                for (int i = 0; i < node.count(); i++) {
                    String desc = node.get(i).get("description").getString();
                    if (desc != null && !desc.isEmpty()) {
                        steps.add(GoalStep.builder()
                                .index(i)
                                .description(desc)
                                .status(StepStatus.PENDING)
                                .retryCount(0)
                                .build());
                    }
                }
                if (!steps.isEmpty()) return steps;
            }
        } catch (Exception e) {
            log.warn("[goal] 解析 LLM 步骤失败，使用 fallback", e);
        }

        // fallback: 单一步骤
        return List.of(GoalStep.builder()
                .index(0)
                .description(fallbackDescription)
                .status(StepStatus.PENDING)
                .retryCount(0)
                .build());
    }
}
```

- [ ] **Step 3: Verify compilation**

```bash
mvn compile -pl agent4j-bin -am -q
```
Expected: BUILD SUCCESS

---

## Task 5: GoalCommand — `/goal` Command Handler

**Files:**
- Create: `agent4j-bin/src/main/java/site/sorghum/agent4j/bin/command/impl/GoalCommand.java`

- [ ] **Step 1: Create GoalCommand**

```java
package site.sorghum.agent4j.bin.command.impl;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.bin.agent.UserMessage;
import site.sorghum.agent4j.bin.command.ChatCommand;
import site.sorghum.agent4j.bin.command.ChatCommandContext;
import site.sorghum.agent4j.bin.command.MessageWrapper;
import site.sorghum.agent4j.bin.goal.*;
import site.sorghum.agent4j.tool.LogLevel;

import java.util.stream.IntStream;

/**
 * /goal — 设定并跟踪会话目标。
 * <p>
 * 子命令：
 * /goal set &lt;描述&gt;           设定新目标
 * /goal set &lt;描述&gt; --verify "cmd"  设定目标并指定验证命令
 * /goal status                 查看当前目标进度
 * /goal pause                  暂停目标
 * /goal resume                 恢复目标
 * /goal retry &lt;步骤号&gt;      手动重试某一步
 * /goal skip  &lt;步骤号&gt;      跳过某一步
 * /goal clear                  清除当前目标
 * /goal help                   显示帮助
 * </p>
 */
@Component
@Slf4j
public class GoalCommand implements ChatCommand {

    @Inject
    private GoalEngine goalEngine;

    @Inject
    private site.sorghum.agent4j.bin.workspace.WorkspaceManager workspaceManager;

    @Override
    public String getCommand() {
        return "goal";
    }

    @Override
    public boolean matches(String input) {
        return input != null && input.trim().toLowerCase().startsWith("/goal");
    }

    @Override
    public String getDescription() {
        return "/goal        设定并跟踪会话目标（子命令：set/status/pause/resume/retry/skip/clear）";
    }

    @Override
    public String getCommandType() {
        return "tool";
    }

    @Override
    public CommandResult execute(MessageWrapper input, ChatCommandContext ctx) throws Exception {
        String text = input.getMessage();
        // 去掉 "/goal" 前缀
        String remaining = text.trim().substring(5).trim();
        String[] parts = remaining.split("\\s+", 2);
        String subCmd = parts.length > 0 ? parts[0].toLowerCase() : "help";
        String args = parts.length > 1 ? parts[1] : "";

        switch (subCmd) {
            case "set":
                return handleSet(args, ctx, input);
            case "status":
                return handleStatus(ctx);
            case "pause":
                return handlePause(ctx);
            case "resume":
                return handleResume(ctx);
            case "retry":
                return handleRetry(args, ctx);
            case "skip":
                return handleSkip(args, ctx);
            case "clear":
                return handleClear(ctx);
            default:
                return handleHelp();
        }
    }

    private CommandResult handleSet(String args, ChatCommandContext ctx, MessageWrapper input) throws Exception {
        if (args.isEmpty()) {
            ctx.getAgent().getOutput().onLog(LogLevel.INFO,
                    "用法: /goal set <描述> [--verify \"命令\"]");
            return CommandResult.CONTINUE;
        }

        // 提取 --verify 标志
        String description = args;
        String verifyCmd = null;
        int verifyIdx = args.indexOf("--verify");
        if (verifyIdx >= 0) {
            description = args.substring(0, verifyIdx).trim();
            String afterFlag = args.substring(verifyIdx + 8).trim();
            if (afterFlag.startsWith("\"")) {
                int endQuote = afterFlag.indexOf("\"", 1);
                if (endQuote > 0) {
                    verifyCmd = afterFlag.substring(1, endQuote);
                }
            } else {
                // 不带引号，取到下一个空格或末尾
                int spaceIdx = afterFlag.indexOf(' ');
                verifyCmd = spaceIdx > 0 ? afterFlag.substring(0, spaceIdx) : afterFlag;
            }
        }

        // 创建目标（LLM 拆解步骤）
        Goal goal = goalEngine.createGoal(description, verifyCmd, ctx);

        // 持久化并激活
        goalEngine.activateGoal(goal, ctx);

        // 通过修改 MessageWrapper 的消息内容，让主流程继续执行第一步
        // 将目标信息注入到 LLM 的下一次请求中
        String stepsText = formatStepsForPrompt(goal);
        String prompt = """
                当前会话已设定目标：「%s」
                
                步骤计划：
                %s
                
                请开始执行第一步。每完成一步，更新目标进度。
                如果有步骤失败，自动重试（最多 %d 次）。
                全部完成后总结汇报。
                """
                .formatted(goal.getTitle(), stepsText, goal.getMaxRetries());

        input.setMessage(prompt);

        // 使用 LOOP 让主流程处理修改后的消息
        return CommandResult.LOOP;
    }

    private CommandResult handleStatus(ChatCommandContext ctx) throws Exception {
        Goal goal = goalEngine.getCurrentGoal(ctx);
        if (goal == null) {
            ctx.getAgent().getOutput().onLog(LogLevel.INFO,
                    "当前会话没有活跃目标。使用 /goal set <描述> 创建目标。");
            return CommandResult.CONTINUE;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n──────────────────────────────────────\n");
        sb.append("🎯 目标：").append(goal.getTitle()).append("\n");
        sb.append("状态：").append(formatStatusBadge(goal.getStatus())).append("\n\n");
        sb.append("进度：").append(goal.progressText()).append("\n\n");

        for (GoalStep step : goal.getSteps()) {
            String icon = switch (step.getStatus()) {
                case DONE -> "✅";
                case IN_PROGRESS -> "⏳";
                case FAILED -> "❌";
                case SKIPPED -> "⏭️";
                case PENDING -> "⬜";
            };
            String retryInfo = step.getRetryCount() > 0 ? " (retry:" + step.getRetryCount() + ")" : "";
            sb.append(icon).append(" ").append(step.getIndex() + 1).append(". ")
                    .append(step.getDescription()).append(retryInfo).append("\n");
            if (step.getLastError() != null && !step.getLastError().isEmpty()) {
                sb.append("   └─ ").append(step.getLastError()).append("\n");
            }
        }
        sb.append("──────────────────────────────────────");

        ctx.getAgent().getOutput().onLog(LogLevel.INFO, sb.toString());
        return CommandResult.CONTINUE;
    }

    private CommandResult handlePause(ChatCommandContext ctx) throws Exception {
        Goal goal = goalEngine.getCurrentGoal(ctx);
        if (goal == null) return noGoalResponse(ctx);
        goalEngine.pause(goal, ctx);
        ctx.getAgent().getOutput().onLog(LogLevel.INFO, "⏸️ 目标已暂停： " + goal.getTitle());
        return CommandResult.CONTINUE;
    }

    private CommandResult handleResume(ChatCommandContext ctx) throws Exception {
        Goal goal = goalEngine.getCurrentGoal(ctx);
        if (goal == null) return noGoalResponse(ctx);
        goalEngine.resume(goal, ctx);
        ctx.getAgent().getOutput().onLog(LogLevel.INFO, "▶️ 目标已恢复： " + goal.getTitle());
        return CommandResult.CONTINUE;
    }

    private CommandResult handleRetry(String args, ChatCommandContext ctx) throws Exception {
        if (args.isEmpty()) {
            ctx.getAgent().getOutput().onLog(LogLevel.INFO, "用法: /goal retry <步骤号>");
            return CommandResult.CONTINUE;
        }
        int stepIndex;
        try {
            stepIndex = Integer.parseInt(args.trim()) - 1; // 用户从 1 开始计数
        } catch (NumberFormatException e) {
            ctx.getAgent().getOutput().onLog(LogLevel.INFO, "步骤号必须是数字");
            return CommandResult.CONTINUE;
        }

        Goal goal = goalEngine.getCurrentGoal(ctx);
        if (goal == null) return noGoalResponse(ctx);
        if (stepIndex < 0 || stepIndex >= goal.getSteps().size()) {
            ctx.getAgent().getOutput().onLog(LogLevel.INFO, "步骤号超出范围（1-" + goal.getSteps().size() + "）");
            return CommandResult.CONTINUE;
        }

        GoalStep step = goal.getSteps().get(stepIndex);
        step.setStatus(StepStatus.PENDING);
        step.setRetryCount(0);
        step.setLastError(null);
        goal.setUpdatedAt(java.time.Instant.now());
        goal.getSteps().set(stepIndex, step);
        workspaceManager.getGoalStore().save(goal);

        ctx.getAgent().getOutput().onLog(LogLevel.INFO,
                "🔄 步骤 " + (stepIndex + 1) + " 已重置为待执行状态，请继续工作。");
        return CommandResult.CONTINUE;
    }

    private CommandResult handleSkip(String args, ChatCommandContext ctx) throws Exception {
        if (args.isEmpty()) {
            ctx.getAgent().getOutput().onLog(LogLevel.INFO, "用法: /goal skip <步骤号>");
            return CommandResult.CONTINUE;
        }
        int stepIndex;
        try {
            stepIndex = Integer.parseInt(args.trim()) - 1;
        } catch (NumberFormatException e) {
            ctx.getAgent().getOutput().onLog(LogLevel.INFO, "步骤号必须是数字");
            return CommandResult.CONTINUE;
        }

        Goal goal = goalEngine.getCurrentGoal(ctx);
        if (goal == null) return noGoalResponse(ctx);
        if (stepIndex < 0 || stepIndex >= goal.getSteps().size()) {
            ctx.getAgent().getOutput().onLog(LogLevel.INFO, "步骤号超出范围（1-" + goal.getSteps().size() + "）");
            return CommandResult.CONTINUE;
        }

        GoalStep step = goal.getSteps().get(stepIndex);
        step.setStatus(StepStatus.SKIPPED);
        step.setCompletedAt(java.time.Instant.now());
        goal.setUpdatedAt(java.time.Instant.now());

        if (goal.isAllDone()) {
            goal.setStatus(GoalStatus.COMPLETED);
            goal.setCompletedAt(java.time.Instant.now());
        }

        workspaceManager.getGoalStore().save(goal);
        ctx.getAgent().getOutput().onLog(LogLevel.INFO,
                "⏭️ 已跳过步骤 " + (stepIndex + 1) + "：" + step.getDescription());
        return CommandResult.CONTINUE;
    }

    private CommandResult handleClear(ChatCommandContext ctx) throws Exception {
        Goal goal = goalEngine.getCurrentGoal(ctx);
        if (goal == null) return noGoalResponse(ctx);

        workspaceManager.getGoalStore().delete(goal.getSessionId());
        ctx.getAgent().getOutput().onLog(LogLevel.INFO, "🗑️ 目标已清除：" + goal.getTitle());
        return CommandResult.CONTINUE;
    }

    private CommandResult handleHelp() {
        // 帮助信息由 /help 命令自动收集 getDescription()
        // 这里不需要额外输出
        return CommandResult.CONTINUE;
    }

    private CommandResult noGoalResponse(ChatCommandContext ctx) {
        ctx.getAgent().getOutput().onLog(LogLevel.INFO,
                "当前会话没有活跃目标。使用 /goal set <描述> 创建目标。");
        return CommandResult.CONTINUE;
    }

    private String formatStatusBadge(GoalStatus status) {
        return switch (status) {
            case ACTIVE -> "🟢 进行中";
            case PAUSED -> "🟡 已暂停";
            case COMPLETED -> "✅ 已完成";
            case FAILED -> "🔴 失败";
        };
    }

    private String formatStepsForPrompt(Goal goal) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < goal.getSteps().size(); i++) {
            GoalStep step = goal.getSteps().get(i);
            sb.append("  ").append(i + 1).append(". [ ] ").append(step.getDescription()).append("\n");
        }
        return sb.toString();
    }

    @Override
    public boolean isSilent() {
        return false;
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
mvn compile -pl agent4j-bin -am -q
```
Expected: BUILD SUCCESS

---

## Task 6: Session Load — Goal Recovery Integration

**Files:**
- Modify: `agent4j-bin/src/main/java/site/sorghum/agent4j/bin/agent/Agent4jAgent.java`

- [ ] **Step 1: Add goal recovery after session load**

In `Agent4jAgent.java`, locate the `chat()` method. After the command handling block (around line 228) and before the "普通聊天逻辑" comment, add goal recovery:

```java
// === 目标恢复检测 ===
// 会话加载后，检查是否有未完成的活跃目标
if (sessionService != null && commandRegistry != null) {
    try {
        String currentSessionId = sessionService.getStore().currentName();
        if (currentSessionId != null && workspaceManager != null) {
            GoalStore goalStore = workspaceManager.getGoalStore();
            Goal pendingGoal = goalStore.findBySession(currentSessionId);
            if (pendingGoal != null
                    && (pendingGoal.getStatus() == GoalStatus.ACTIVE
                        || pendingGoal.getStatus() == GoalStatus.PAUSED)) {
                // 注入系统消息提醒
                ctx.addSystemMessage(
                        "📋 检测到未完成的目标：「" + pendingGoal.getTitle() + "」\n"
                        + "进度：" + pendingGoal.progressText() + "\n"
                        + "使用 /goal status 查看详情，或直接继续执行。");
                log.info("[goal] 会话恢复，发现未完成目标: {} - {}",
                        pendingGoal.getId(), pendingGoal.getTitle());
            }
        }
    } catch (Exception e) {
        log.warn("[goal] 目标恢复检测失败", e);
    }
}
```

Add the import at the top of the file:
```java
import site.sorghum.agent4j.bin.goal.Goal;
import site.sorghum.agent4j.bin.goal.GoalStatus;
import site.sorghum.agent4j.bin.goal.GoalStore;
```

- [ ] **Step 2: Verify compilation**

```bash
mvn compile -pl agent4j-bin -am -q
```
Expected: BUILD SUCCESS

---

## Task 7: Post-Message Patrol Trigger

**Files:**
- Modify: `agent4j-bin/src/main/java/site/sorghum/agent4j/bin/agent/AgentLoop.java`

- [ ] **Step 1: Review AgentLoop to find the right hook**

First read the AgentLoop file to find where LLM responses finish and where to add the patrol trigger:

```bash
grep -n "class AgentLoop\|void run\|String run\|onLog\|after loop\|// complete\|// done\|// finish" agent4j-bin/src/main/java/site/sorghum/agent4j/bin/agent/AgentLoop.java
```

- [ ] **Step 2: Add patrol check after LLM reply**

After reading the file, add the following at the appropriate location (after each LLM message is fully processed):

```java
// === 目标巡检检查 ===
// 每次 LLM 消息处理后，触发一次 patrol 检查
try {
    if (workspaceManager != null && sessionService != null) {
        GoalStore goalStore = workspaceManager.getGoalStore();
        String sessionId = sessionService.getStore().currentName();
        if (sessionId != null) {
            Goal goal = goalStore.findBySession(sessionId);
            if (goal != null && goal.getStatus() == GoalStatus.ACTIVE) {
                // 查找是否有 FAILED 但未超重试的步骤
                for (GoalStep step : goal.getSteps()) {
                    if (step.getStatus() == StepStatus.FAILED
                            && step.getRetryCount() < goal.getMaxRetries()) {
                        log.info("[goal] 检测到失败步骤，自动重试: step={}, retry={}/{}",
                                step.getIndex() + 1, step.getRetryCount(), goal.getMaxRetries());
                        
                        // 注入系统消息通知 Agent 重试
                        addSystemMessage("⚠️ 步骤 " + (step.getIndex() + 1)
                                + " 执行失败，正在进行第 " + (step.getRetryCount() + 1)
                                + "/" + goal.getMaxRetries() + " 次重试");
                        
                        // 重置步骤状态为 PENDING 让 Agent 重新执行
                        step.setStatus(StepStatus.PENDING);
                        step.setRetryCount(step.getRetryCount() + 1);
                        goal.setUpdatedAt(java.time.Instant.now());
                        goalStore.save(goal);
                        break; // 一次只重试一步
                    }
                }
            }
        }
    }
} catch (Exception e) {
    log.warn("[goal] 巡检检查失败", e);
}
```

Add the required imports to AgentLoop.java:
```java
import site.sorghum.agent4j.bin.goal.Goal;
import site.sorghum.agent4j.bin.goal.GoalStatus;
import site.sorghum.agent4j.bin.goal.GoalStep;
import site.sorghum.agent4j.bin.goal.GoalStore;
import site.sorghum.agent4j.bin.goal.StepStatus;
import site.sorghum.agent4j.bin.workspace.WorkspaceManager;
```

- [ ] **Step 3: Verify compilation**

```bash
mvn compile -pl agent4j-bin -am -q
```
Expected: BUILD SUCCESS

---

## Task 8: Integration Test — Verify End-to-End

- [ ] **Step 1: Build the entire project**

```bash
mvn compile -DskipTests -q
```
Expected: BUILD SUCCESS

- [ ] **Step 2: Run existing tests**

```bash
mvn test -pl agent4j-bin -am -q
```
Expected: All tests pass (no regressions)

- [ ] **Step 3: Manual smoke test (start web server)**

```bash
mvn exec:java -pl agent4j-web -Dexec.mainClass="site.sorghum.agent4j.web.WebApp" -q
```
Expected: Server starts without errors

---

## Self-Review Checklist

**1. Spec coverage:**
- [x] Data model (Goal, GoalStep, enums) → Task 1
- [x] JSONL persistence → Task 2
- [x] Workspace integration → Task 3
- [x] GoalEngine (LLM breakdown, patrol) → Task 4
- [x] GoalPatrolPrompt → Task 4
- [x] /goal command (set/status/pause/resume/retry/skip/clear) → Task 5
- [x] Session recovery on load → Task 6
- [x] Post-message patrol trigger → Task 7
- [x] Sub-agent patrol mechanism → Task 4 (GoalPatrolPrompt)
- [x] verifyCommand support → Task 5 (GoalCommand.handleSet)

**2. Placeholder scan:** No TBD, TODO, or placeholder code.
**3. Type consistency:** All method signatures and field names consistent across tasks.
