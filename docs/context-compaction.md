# 上下文压缩（Context Compaction）设计

## 参考 DeepSeek Harness 的模型

DeepSeek Harness 把上下文压缩拆成三层：

1. `@deepseek-ai/dsh-compaction`：服务定义，只定义 `compactIfNeeded` / `compactNow` / `compactRegion`、会话事件和 tool-call/result 配对边界。
2. `@deepseek-ai/dsh-compaction-basic`：服务提供方，负责压力测量、保留尾部预算、范围选择、摘要调用和收敛重试。
3. `@deepseek-ai/dsh-compaction-tool-result-pruner`：可选的无模型预处理，先裁剪超大的 tool result。

关键设计点：

- **压力以 token 预算为准**：`thresholdRatio=0.8` 表示 `有效 prompt tokens > floor(contextWindow * 0.8)` 时触发；`retainRatio=0.16` 表示折叠后保留最近 `floor(contextWindow * 0.16)` tokens 原样不折叠。
- **有效用量取离线估算与服务端 usage 的较大值**，避免离线 tokenizer 与模型实际封装差异导致误判。
- **范围选择从最旧消息开始，保留最近尾部，并保证切割点不会拆散 assistant tool_calls 与其 tool result 对**。
- **摘要是一次独立 LLM 调用**，回放被替换范围内的消息，输出结构化 checkpoint；只有摘要比被替换内容更小时才落地。
- **折叠后重新测量压力**，按 `compactionRetries` 继续折叠，直到回到阈值内或无可折叠范围。
- **服务端确认上下文溢出时不依赖压力估算**，直接做一次更激进的平衡折叠；只有表面替换真正推进时才允许重试原请求。
- **会话日志保留原始事件**，模型表面只看到替换后的 checkpoint，因此回放和审计仍然确定。

## Loopra 当前落地

本次在 `loopra-model` 中新增了四个核心类：

| 类 | 职责 |
|---|---|
| `ContextCompactionPolicy` | 阈值、保留比例、折叠重试次数的策略值，支持从 `AgentConfig` 读取 |
| `ContextPressureMeter` | 统一压力测量：`max(离线估算, lastPromptTokens)`，换算 threshold/retain 预算 |
| `CompactionRangeSelector` | 从尾部反向累计 token，选择最旧的可折叠范围，并回退到 tool-call/result 配对完整的边界 |
| `ContextFolding.foldRange` | 按选定范围折叠：调用摘要器、校验摘要确实更小、替换范围并保留尾部 |

`AgentLoop.prepareMessages()` 已经切换为：

```text
构建消息 -> MessageHealer -> 测量压力
  -> 超过 threshold 且还有重试次数
    -> CompactionRangeSelector 选范围
    -> ContextFolding.foldRange 折叠
    -> 重新测量，决定是否继续
```

配置入口在 `LoopraConfig` 的 `contextCompaction`：

```json
{
  "contextCompaction": {
    "thresholdRatio": 0.8,
    "retainRatio": 0.16,
    "compactionRetries": 1,
    "toolResultPrune": {
      "thresholdChars": 8192,
      "headChars": 4096,
      "tailChars": 1024
    }
  }
}
```

`maxContextChars` / `keepTailChars` 仍保留兼容，但自动路径优先使用 token 预算。

### 阶段 2：结构化摘要与合并旧 checkpoint（已完成）

`ContextFolding.summarize()` 现在要求输出固定的 Markdown checkpoint 结构（主要请求与意图、关键技术与约定、文件与代码、错误与修复、待办事项、当前进度、下一步、关键上下文），并显式要求合并去重上一次的 `<compacted-summary>`，而不是原样复制。

### 阶段 3：ToolResultPruner（已完成）

新增 `ToolResultPruner`：摘要前先按 `head + marker + tail` 裁剪超预算的 tool result，只改 `tool` 消息的 content，保留 tool_call_id 与时间戳等元数据。裁剪落地后重新测量压力，如果已经回到阈值内就完全跳过摘要调用。

### 阶段 5：统一手动和溢出路径（已完成）

`/compact`、服务端上下文溢出兜底和自动预检现在都走同一个 `AgentLoop.foldOldestRange()`：

- `/compact` 使用 `contextWindow * retainRatio` 作为保留 token 预算，不再按固定 20 条。
- 上下文溢出不依赖压力阈值，按有效用量（离线估算与服务端 prompt_tokens 的较大值）的 `1/4`、`1/8` 逐步收缩保留预算，第一次更温和、第二次更激进。
- 三条路径共用 `CompactionRangeSelector` 的配对边界和 `ContextFolding.foldRange` 的摘要收敛校验。

`ContextFolding.foldKeepLast` 保留给旧调用方兼容，但主流程不再使用。

### 阶段 4：raw/surface 分离（已完成）

主会话文件 `{name}.jsonl` 继续作为模型表面历史：压缩时 `rewrite()` 把它重写为 checkpoint + 保留尾部。新增 append-only 审计文件 `{name}.events`，所有原始消息和 tool result 在 `append()` 时同步写入，压缩重写不会触碰它。

- 模型表面：`load()` 读取 `{name}.jsonl`，恢复会话时只看到折叠后的 checkpoint。
- 审计/回放：`SessionStore.loadEvents(name)` 读取 `{name}.events`，`SessionService.loadRawEvents()` 暴露给上层；`ToolResultPruner` 裁剪前的完整 tool result 也保留在其中。
- 前端呈现：Web 历史继续读取模型表面（checkpoint + 尾部）。以 `[历史上下文折叠` 开头的 user 消息不再渲染成普通用户气泡，而是渲染为独立的“较早对话已压缩”提示条，默认只展示标题，可展开查看 checkpoint；Web 服务端新增只读接口 `GET /api/agent/history/events`，前端通过 `AgentService.getRawEvents()` 读取 `{name}.events`，并用与主消息列表相同的 `chatHistory` 组装逻辑 + `BlockRenderer` 在弹窗中展示压缩前的原始消息、思考与 tool result（工具和思考同样可折叠）。
- 会话删除/清空会同时清理 `.jsonl` 与 `.events`。
