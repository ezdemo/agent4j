# 工作流引擎简化重构方案

## 核心思路

**把 DAG 降级为有序 Step 列表，去掉图算法，让 LLM 在步骤内保持自然推理的自由度。**

---

## 1. 新模型：`WorkflowStep`

```java
public class WorkflowStep {
    String id;           // "step-1", "step-2"
    String description;  // "分析需求文档，输出 API 接口规范"
    StepKind kind;       // STEP | FORK | HITL
    StepStatus status;   // PENDING | RUNNING | DONE | SKIPPED | FAILED
    String result;       // 执行结果摘要
    List<String> notes;  // 执行过程中的备注
    Instant createdAt;
    Instant completedAt;
}

public enum StepKind {
    STEP,   // 普通步骤（LLM 自行决定如何完成）
    FORK,   // 分支步骤（LLM 从多个选项中选一个）
    HITL    // 人工审批步骤
}

public enum StepStatus {
    PENDING, RUNNING, DONE, SKIPPED, FAILED
}
```

**比起现在的 WorkflowNode + WorkflowEdge + NodeType + EdgeType 共 4 个类的图模型，减少到 1 个类 + 2 个枚举。**

---

## 2. 新引擎：`SimpleWorkflow`

```java
public class SimpleWorkflow {
    String id;
    String sessionId;
    String title;
    List<WorkflowStep> steps;    // 有序列表
    int currentStepIndex;        // 当前执行到第几步
    WorkflowStatus status;       // ACTIVE | PAUSED | COMPLETED | FAILED
    
    // ---- 核心 API ----
    WorkflowStep currentStep();              // 获取当前步骤
    boolean advance();                        // 推进到下一步
    void markCurrentDone(String result);      // 标记当前完成
    void markCurrentFailed(String error);     // 标记当前失败
    void pause(); / void resume();
    boolean isAllDone();
}
```

**比起现在的 WorkflowEngine（410 行），新引擎预计 ~150 行。**

---

## 3. 工具简化：1 个工具代替 3 个

### 当前：3 个工具
- `workflow_create_dag` — 687 行 ✅ **删除**
- `workflow_mark_node` — 160 行 ✅ **删除**
- `workflow_visualize` — 164 行 ✅ **保留并简化**

### 新：2 个精简工具

**a) `workflow_start`（~80 行）**
```
title: "工作流标题"
steps: [{description: "步骤1描述", kind: "step"}, ...]
```
→ 创建 SimpleWorkflow，返回工作流 ID 和当前步骤

**b) `workflow_step`（~80 行）**
```
action: "done" | "fail" | "skip"
result: "执行结果摘要"
```
→ 标记当前步骤完成/失败/跳过，自动推进到下一步

**c) `workflow_status`（~50 行，原 workflow_visualize 精简）**
→ 返回当前步骤索引、总步骤数、各步骤状态

---

## 4. 执行流程对比

### 当前 DAG 流程
```
LLM: workflow_create_dag(nodes=[n1,n2,n3], edges=[n1→n2, n2→n3])
系统: 解析JSON → 验证DAG → 持久化 → 返回workflowId
LLM: 读提示词 → "请执行n1节点"
LLM: read/write/bash → 完成n1
LLM: workflow_mark_node(nodeId=n1) → n1标记完成
系统: 计算前驱后继 → 发现n2就绪 → 自动推进
LLM: 读提示词 → "请执行n2节点"
...重复...
```

### 简化后 Step 流程
```
LLM: workflow_start(title="重构X", steps=[
        {description:"分析代码结构", kind:"step"},
        {description:"实现重构", kind:"step"},
        {description:"运行测试", kind:"step"},
        {description:"人工确认部署", kind:"hitl"}
      ])
系统: 创建SimpleWorkflow → 返回"当前: 步骤1/4 - 分析代码结构"

LLM: read → 分析代码结构 → 完成
LLM: workflow_step(action="done", result="分析完成，确定了重构范围")
系统: 自动推进到步骤2/4

LLM: write/edit → 实现重构 → 完成
LLM: workflow_step(action="done")
系统: 自动推进到步骤3/4

LLM: bash test → 测试通过
LLM: workflow_step(action="done")
系统: 自动推进到步骤4/4 [HITL]

用户: approve
系统: 标记完成 → 工作流结束
```

**LLM 在每一步内的推理是完全自由的**——它可以在步骤 2 "实现重构"中自主决定 read → edit → grep → bash 的顺序，不受任何 DAG 约束。

---

## 5. 关键差异总结

| 维度 | 当前 DAG 引擎（2100行） | 简化 Step 方案（~400行） |
|------|----------------------|----------------------|
| 模型 | Workflow + WorkflowNode + WorkflowEdge + NodeType + EdgeType + NodeStatus + WorkflowStatus | WorkflowStep + StepKind + StepStatus |
| 图算法 | 前驱/后继查找、拓扑排序、分支递归跳过、自动完成推断、环检测 | **无**。线性索引即可 |
| 节点类型 | 6种（ACTION/PARALLEL/CONDITION/SUBFLOW/HITL/LOOP） | 3种（STEP/FORK/HITL） |
| 工具数 | 3个工具（2100行） | 3个精简工具（~210行） |
| 持久化 | JSONL 完整状态 | 简单 KV（sessionId → JSON） |
| LLM 自由度 | 受 DAG 边约束 | 步骤内完全自由 |
| 条件分支 | 显式 CONDITION 节点 | FORK 步骤：LLM 自行判断，选一个分支 |
| 循环 | LOOP 节点 + LOOP_BACK 边 | **不需要**——LLM 在步骤内自然多轮推理 |
| 并行 | PARALLEL + join 同步 | FORK 步骤：LLM 发起子代理并行执行 |

---

## 6. 前端同步改造

### 6.1 当前前端工作流相关代码

| 文件 | 当前职责 | 改造方向 |
|------|---------|---------|
| `WorkflowDagRenderer.vue` (309行) | SVG DAG 图渲染（布局算法、节点/边绘制、箭头标记、条件分支着色） | 🗑️ **删除**，替换为新组件 |
| `Chat.vue` (workflow-dock) | 左侧工作流 dock 面板，hover 时加载 `WorkflowDagRenderer` | ✏️ 改用新组件 |
| `BlockRenderer.vue` | 聊天消息内识别 `workflow_create_dag` / `workflow_visualize` 工具调用，渲染 DAG 图 | ✏️ 识别新工具 `workflow_start`/`workflow_step` |
| `api.js` (getWorkflow) | `GET /api/sessions/{name}/workflow` | ✏️ 适配新 API |

### 6.2 新前端组件：`WorkflowSteps.vue`（~150行）

**替代 `WorkflowDagRenderer.vue`（309行 → 150行），去掉 SVG DAG 渲染，改用步骤进度条。**

```vue
<template>
  <div class="workflow-steps">
    <!-- 头部：标题 + 状态 + 进度 -->
    <div class="ws-header">
      <span class="ws-title">{{ data.title }}</span>
      <span class="ws-badge" :class="data.status">{{ statusText }}</span>
      <span class="ws-progress">{{ data.currentStepIndex }}/{{ data.steps.length }}</span>
    </div>

    <!-- 步骤时间线 -->
    <div class="ws-timeline">
      <div v-for="(step, i) in data.steps" :key="step.id"
           class="ws-step" :class="[
             step.status,
             { active: i === data.currentStepIndex - 1 }
           ]">
        <!-- 步骤圆点 + 连接线 -->
        <div class="ws-dot-row">
          <div class="ws-dot">
            <span v-if="step.status==='DONE'" class="dot-icon">✓</span>
            <span v-else-if="step.status==='RUNNING'" class="dot-icon pulse">●</span>
            <span v-else-if="step.status==='FAILED'" class="dot-icon">✕</span>
            <span v-else-if="step.status==='SKIPPED'" class="dot-icon">−</span>
            <span v-else class="dot-num">{{ i + 1 }}</span>
          </div>
          <div v-if="i < data.steps.length - 1" class="ws-line"
               :class="{ done: step.status==='DONE' }"></div>
        </div>
        <!-- 步骤内容 -->
        <div class="ws-content">
          <div class="ws-desc">{{ step.description }}</div>
          <div v-if="step.kind==='HITL'" class="ws-kind">🛑 需人工审批</div>
          <div v-if="step.kind==='FORK'" class="ws-kind">🔀 分支选择</div>
          <div v-if="step.result" class="ws-result">{{ step.result }}</div>
        </div>
      </div>
    </div>
  </div>
</template>
```

**样式变化要点：**
- 🗑️ 删除所有 SVG 相关代码（defs/marker/path/箭头渲染）
- 🗑️ 删除布局算法（BFS 层级计算、条件分支识别、坐标计算）
- 🗑️ 删除 5 种边类型（NORMAL/CONDITION_SELECT/CONDITION_TRUE/CONDITION_FALSE/LOOP_BACK）
- 🆕 纯 CSS 时间线渲染（圆点 + 连接线 + 状态着色）

### 6.3 Chat.vue 修改点

```diff
- <WorkflowDagRenderer :data="workflowData" />
+ <WorkflowSteps :data="workflowData" />
```

同时更新 import：
```diff
- import WorkflowDagRenderer from '../components/WorkflowDagRenderer.vue'
+ import WorkflowSteps from '../components/WorkflowSteps.vue'
```

### 6.4 BlockRenderer.vue 修改点

```diff
- const WORKFLOW_TOOLS = ['workflow_create_dag', 'workflow_visualize']
+ const WORKFLOW_TOOLS = ['workflow_start', 'workflow_step', 'workflow_status']
```

```diff
- <WorkflowDagRenderer :data="getWorkflowData(block)" />
+ <WorkflowSteps :data="getWorkflowData(block)" />
```

`isWorkflowTool` / `getWorkflowTitle` / `getWorkflowData` 等函数同步更新参数解析逻辑。

### 6.5 api.js 修改点

```diff
- getWorkflow: (name, workspaceHash) => {
-   const params = { workspaceHash }
-   return api.get(`/sessions/${name}/workflow`, { params })
- }
+ getWorkflow: (name, workspaceHash) => {
+   const params = { workspaceHash }
+   return api.get(`/sessions/${name}/workflow`, { params })
+ }
```

### 6.6 ⚠️ 遗漏：AgentLoop 系统提示词中的工具文档

`loopra-bin/.../agent/core/AgentLoop.java` 的 system prompt 中（约第393-419行）硬编码了旧工具的说明文档：

```
### workflow_create_dag — 创建工作流 DAG  ← ❌ 删除
### workflow_visualize — 查看工作流      ← 改名为 workflow_status
### workflow_mark_node — 标记节点完成    ← 改名为 workflow_step
```

需要替换为：
```
### workflow_start — 创建工作流
创建有序步骤工作流。
| 参数 | 必填 | 说明 |
|------|------|------|
| title | 是 | 工作流标题 |
| steps | 是 | 步骤数组 `[{description, kind?}]` |

### workflow_step — 标记步骤完成
标记当前步骤完成/失败，自动推进到下一步。
| 参数 | 必填 | 说明 |
|------|------|------|
| action | 是 | `done` / `fail` / `skip` |
| result | 否 | 执行结果摘要 |

### workflow_status — 查看工作流状态
返回当前步骤索引、总步骤数、各步骤状态。
```

### 6.7 ⚠️ 遗漏：SessionController REST 端点

`loopra-web/.../controller/SessionController.java` 中（约第136-156行）：
- 端点：`GET /sessions/{name}/workflow`
- 返回：`WorkflowVisualizationDTO`（含 NodeDTO、EdgeDTO、PathNodeDTO）
- 当前实现：从 `WorkflowStore` 加载旧 `Workflow` 模型，通过 `convertToVisualizationDTO()` 转换

改造：
```diff
- @Mapping("/{name}/workflow")
- public ApiResponse<WorkflowVisualizationDTO> getWorkflow(...)
+ @Mapping("/{name}/workflow")
+ public ApiResponse<WorkflowStatusDTO> getWorkflow(...)
```

### 6.8 ⚠️ 遗漏：WorkflowVisualizationDTO → WorkflowStatusDTO

`loopra-web/.../model/WorkflowVisualizationDTO.java`（77行，3个内部类）：

```
当前：                   改造后：
WorkflowVisualizationDTO  WorkflowStatusDTO
├── NodeDTO (11字段)      ├── title
│   id/desc/type/status/  ├── status
│   retryCount/lastError/  ├── currentStepIndex
│   result/condition/      └── steps: StepDTO[]
│   conditionResult/           └── id/desc/kind/status/result
│   parallelBranches/
├── EdgeDTO (6字段)
│   id/from/to/type/condition/label
└── PathNodeDTO (4字段, 递归)
    id/desc/status/next[]
```

**从 77 行 + 3 内部类 → 约 30 行 + 1 内部类。**

### 6.9 ⚠️ 遗漏：WorkspaceManager 存储适配

`loopra-bin/.../workspace/WorkspaceManager.java`（第125-130行）：
- `getWorkflowStore()` 只返回 `JsonlWorkflowStore`（旧 DAG 持久化）
- 需要新增 `SimpleWorkflow` 的存储方式（KV 模式即可，无需 JSONL）

### 6.10 ⚠️ 遗漏：测试文件 WorkflowTest.java

`loopra-bin/src/test/.../workflow/WorkflowTest.java`（775行）：
- 14 个测试方法覆盖了旧 DAG 模型（节点状态、前驱后继、序列化、分支条件等）
- **需要全部重写**为 Step 列表模型的测试（约 200 行即可）

---

## 7. 完整改造清单（最终版）

| # | 文件 | 变更类型 | 行数变化 |
|---|------|---------|---------|
| 1 | `WorkflowNode.java` | 🗑️ 删除 | -103 |
| 2 | `WorkflowEdge.java` | 🗑️ 删除 | -44 |
| 3 | `NodeType.java` | 🗑️ 删除 | -24 |
| 4 | `EdgeType.java` | 🗑️ 删除 | -20 |
| 5 | `NodeStatus.java` | 🗑️ 删除 | -24 |
| 6 | `WorkflowStatus.java` | 🗑️ 删除 | -18 |
| 7 | `Workflow.java` | 🗑️ 删除 | -343 |
| 8 | `WorkflowEngine.java` | 🗑️ 删除 | -410 |
| 9 | `JsonlWorkflowStore.java` | 🗑️ 删除 | -282 |
| 10 | `WorkflowCreateDagTool.java` | 🗑️ 删除 | -687 |
| 11 | `WorkflowMarkNodeTool.java` | 🗑️ 删除 | -160 |
| 12 | `WorkflowVisualizationTool.java` | 🗑️ 删除 | -164 |
| 13 | `WorkflowTest.java` | 🗑️ 删除 | -775 |
| 14 | `WorkflowVisualizationDTO.java` | 🗑️ 删除 | -77 |
| 15 | `WorkflowDagRenderer.vue` | 🗑️ 删除 | -309 |
| | **小计（清理）** | | **-3,440** |
| | | | |
| 16 | `WorkflowStep.java` | 🆕 新增 | +30 |
| 17 | `StepKind.java` | 🆕 新增 | +10 |
| 18 | `StepStatus.java` | 🆕 新增 | +10 |
| 19 | `SimpleWorkflow.java` | 🆕 新增 | +80 |
| 20 | `SimpleWorkflowEngine.java` | 🆕 新增 | +150 |
| 21 | `WorkflowStartTool.java` | 🆕 新增 | +80 |
| 22 | `WorkflowStepTool.java` | 🆕 新增 | +80 |
| 23 | `WorkflowStatusTool.java` | 🆕 新增 | +50 |
| 24 | `WorkflowStatusDTO.java` | 🆕 新增 | +30 |
| 25 | `WorkflowSteps.vue` | 🆕 新增 | +150 |
| 26 | `WorkflowTest.java`（新） | 🆕 新增 | +200 |
| | **小计（新增）** | | **+870** |
| | | | |
| 27 | `AgentLoop.java`（system prompt） | ✏️ 修改 | ~30行文档替换 |
| 28 | `SessionController.java` | ✏️ 修改 | ~30行替换 |
| 29 | `WorkspaceManager.java` | ✏️ 修改 | ~5行新增存储 |
| 30 | `Chat.vue` | ✏️ 修改 | 2行 |
| 31 | `BlockRenderer.vue` | ✏️ 修改 | ~10行 |
| 32 | `api.js` | ✏️ 修改 | 1行 |
| | **小计（修改）** | | **~80行** |

### 总量变化

```
清理：-3,440 行
新增：+870 行
修改：~80 行
────────────────
净减：-2,570 行
```

---

## 8. 保留的能力

| 能力 | 新方案如何实现 |
|------|--------------|
| 👁️ Visibility | `workflow_status` 返回 currentStepIndex / totalSteps / 各步骤状态 + 前端时间线渲染 |
| 🛑 HITL 结构性保证 | `kind: "hitl"` 步骤在时间线上显示🛑标记，等待用户 approve |
| 🔁 失败恢复 | `workflow_step(action="retry")` 重置当前步骤 |
| 📋 多步骤跟踪 | 有序列表 + 状态枚举 + 前端进度条 |
| 📱 内联渲染 | 聊天消息中 `workflow_step` 工具调用展开显示时间线微缩版 |
| 🎯 进度指标 | `currentStepIndex / totalSteps` 可视化 |

```
Phase 1: 后端新增 SimpleWorkflow 模型 + SimpleWorkflowEngine（与现有 DAG 并行）
         - 新增 WorkflowStep.java / StepKind.java / StepStatus.java / SimpleWorkflow.java
         - 新增 SimpleWorkflowEngine.java (~150行)
         - 新增 workflow_start / workflow_step / workflow_status 三个精简工具

Phase 2: 前端改造
         - 新建 WorkflowSteps.vue (~150行) 替代 WorkflowDagRenderer.vue (309行)
         - Chat.vue 替换组件引用 + 更新 API 调用
         - BlockRenderer.vue 替换工具名识别 + 组件引用
         - api.js 更新端点 URL

Phase 3: TUI 同步
         - loopra-tui 中找 workflow 相关卡片渲染，同步改为 Step 列表渲染

Phase 4: 旧代码清理
         - 删除 WorkflowNode.java / WorkflowEdge.java / NodeType.java / EdgeType.java / NodeStatus.java
         - 删除 WorkflowEngine.java（旧版）
         - 删除 JsonlWorkflowStore.java
         - 删除 WorkflowCreateDagTool.java / WorkflowMarkNodeTool.java
         - 删除 WorkflowDagRenderer.vue
```

---

## 8. 保留的能力

| 能力 | 新方案如何实现 |
|------|--------------|
| 👁️ Visibility | `workflow_status` 返回 currentStepIndex / totalSteps / 各步骤状态 + 前端时间线渲染 |
| 🛑 HITL 结构性保证 | `kind: "hitl"` 步骤在时间线上显示🛑标记，等待用户 approve |
| 🔁 失败恢复 | `workflow_step(action="retry")` 重置当前步骤 |
| 📋 多步骤跟踪 | 有序列表 + 状态枚举 + 前端进度条 |
| 📱 内联渲染 | 聊天消息中 `workflow_step` 工具调用展开显示时间线微缩版 |
