# Loopra 项目记忆

本文件由 Loopra 自动维护，记录跨会话沉淀的项目关键事实（架构决策、约定、踩坑、用户偏好等）。
会话启动时自动注入 AI 上下文。可手动删除过期条目，请勿手动修改条目格式。

---

## [2026-07-17 20:45] 会话折叠沉淀

技能文件放在 ~/.loopra/skills/ 下（不是 ~/.codex/skills/）。安装 browser-harness 后需用 `browser-harness skill > ~/.loopra/skills/browser-harness/SKILL.md` 注册技能。

## [2026-07-20 17:03] 会话折叠沉淀

## [2026-07-19 22:57] 会话折叠沉淀

子代理与父代理共用会话文件变更 tracker scope。子循环必须设置 drainFileChanges=false，避免提前 drain 记录；父 AgentLoop 在包含 sub_agent 的工具批次完成后统一 drain，才能把子代理编辑持久化为主消息“已编辑 X 个文件”并支持撤销。

## [2026-07-24 10:08] 会话折叠沉淀

OpenAI 兼容模型的上下文超限标准错误码是 `context_length_exceeded`（通常 HTTP 400，类型为 `invalid_request_error`）；Loopra 已统一识别该码、`input_too_long`/常见超限文本，并在流式或 HTTP 错误首次命中时自动折叠历史后重试一次。

## [2026-07-24 10:49] 会话折叠沉淀

- `context_length_exceeded` 不应进入通用重试流程；AgentLoop 应先折叠历史上下文，再重新发起一次请求。
- Responses API 的 SSE 错误需要只向上层回调一次，避免 `response.failed` 或 `error` 事件造成重复错误回调。
- 在未初始化全局 `ConfigService` 的 AgentLoop 单元测试中，应调用 `freezePromptPrefix()`，避免运行时 `ToolRegistry.refresh` 访问空配置。

## [2026-07-24 13:20] 会话折叠沉淀

- Loopra 桌面端的运行时文件安装目录为 `~/.loopra-gui`。
- Loopra 的用户配置目录仍为 `~/.loopra`，不能随安装目录迁移。
- 桌面端进程管理只应识别命令行包含 `~/.loopra-gui` 的 Java 运行时，不应接管命令行服务使用的 `loopra-web.jar`。
- 修改 Electron 主进程后应至少运行 `node --check loopra-front/electron/main.cjs` 和 `pnpm --dir loopra-front build:main`。
- 安装与卸载脚本同时维护 Shell 和 PowerShell 版本，修改后需要分别进行语法解析检查。


## [2026-07-24 13:46] 会话折叠沉淀

安装模式不使用 LOOPRA_* 自定义环境变量：GUI 安装脚本独立下载并调用安装器的显式参数（PowerShell: -Gui -Setup；Shell: --gui --setup）。GUI 运行时固定在 ~/.loopra-gui，配置固定在 ~/.loopra，且不注册 CLI PATH；卸载脚本从自身 bin 目录推导安装路径。Shell 脚本变更后须处理 CRLF 并运行 bash -n；PowerShell 脚本须运行 Parser::ParseFile 语法校验。

## [2026-07-24 15:04] 会话折叠沉淀

桌面启动时通过 ~/.loopra-gui/bin/version.txt 与 Electron app.getVersion() 比较；版本不一致在 SplashScreen 提示更新，用户可选择暂不更新后继续启动。缺少 version.txt 亦视为版本不一致。

## [2026-07-28 17:56] 会话折叠沉淀

Loopra Web 停止生成需三层取消：前端流请求携带 requestId 并传给 /chat/abort；ChatController 从任务提交起登记 Future/SseEmitter 以覆盖 Agent 创建前竞态；AgentLoop 工具必须用 ExecutorService.submit 返回的 Future 才能由 cancel(true) 中断，并登记 bash/bash_start 等脱离工具 Future 的进程资源，停止时终止进程树。

## [2026-07-30 12:58] 会话折叠沉淀

- 项目使用 `ChatMessage.contentParts` 表示多模态消息，支持 `text` 与 `image_url`；JSONL 会话持久化和 `ContextTokenEstimator` 已能序列化/估算该字段。
- `AgentLoop` 在主循环、普通 HITL 恢复和沙箱 HITL 恢复三处将工具执行结果写入 `ConversationContext`，修改工具结果消息结构时必须同步处理这三条路径。
- OpenAI Responses API 的 `function_call_output.output` 可为包含文本、图像或文件内容的数组；工具错误应使用 `status: incomplete`，而非 `is_error`。
- 用户偏好中文回复。

## [2026-07-31 16:44] 会话折叠沉淀

桌面端 AI 回复完成提醒由 Chat.vue 通过 Electron IPC 触发；主进程检测独立桌面宠物窗口可见则向其发送预览气泡，否则用 Electron Notification 发送 macOS/Windows 原生系统通知。桌面聊天实际运行在 WebContentsView，相关 IPC sender 校验须同时允许 mainWindow 与已注册 desktopChatTabs。

## [2026-07-31 17:01] 会话折叠沉淀

- 用户偏好中文回复。
- 桌面宠物窗口的短左键用于恢复、显示并聚焦主窗口；拖动宠物只移动宠物窗口，不能触发主窗口激活。
- 桌面宠物主窗口激活通过受限 Electron IPC 实现，仅宠物窗口可调用。
- 宠物状态与位置持久化 API 前缀为 `/api/pets`，位置文件为 `~/.loopra/pet/position.json`。
- 当前宠物尺寸持久化字段为整数 `sizeIndex`，若实现连续缩放需新增浮点尺寸字段并兼容旧数据。

## [2026-07-31 17:13] 会话折叠沉淀

桌面宠物展示开关由 Electron 主进程持久化到 ~/.loopra/pet/desktop.json（visible 布尔值）；应用启动时会按该值自动恢复宠物独立窗口。

## [2026-07-31 17:17] 会话折叠沉淀

桌面宠物拖动后的 Electron 窗口绝对坐标也保存到 ~/.loopra/pet/desktop.json 的 x、y；下次启动优先恢复，首次展示才使用右下角默认坐标。

## [2026-07-31 17:28] 会话折叠沉淀

桌面宠物的宠物 API 请求使用 Axios silent 配置，服务端离线时不触发全局 Network Error toast；已加载精灵会保留并显示“服务端离线中…”，下一次成功轮询自动恢复。

## [2026-07-31 17:56] 会话折叠沉淀

- 快捷命令功能位于 `ChatInput.vue` 输入栏底部使用信息区域的左侧。
- 快捷命令本地存储键名为 `loopra.quick-commands`。
- 默认快捷命令为 `/new`、`/plan`、`/compact`、`/continue`。
- 复制成功反馈复用窗口事件 `copy-success`。
- 项目前端使用 Vue、Vite、Vitest，组件测试位于 `src/components/*.test.js`。
- 用户偏好使用中文交流。

## [2026-08-02 14:43] 会话折叠沉淀

- Thread goal 采用结构化步骤模型；步骤具有独立状态和证据，并通过 protocol、state、goal extension 与 app-server API 暴露。
- 未显式提供步骤时，创建目标会把 objective 转成单一步骤。
- `create_goal` 只响应用户或系统/开发者明确提出的目标创建要求，不从普通任务自动推断；未完成目标存在时不得直接替换。
- `update_goal` 用于目标状态和步骤进度更新，不承担 objective 修改。
- 目标只有在全部步骤为 done 或 skipped，且可选验证命令已满足后才能完成。
- 本项目在 stable rustfmt 下会提示 `imports_granularity = Item` 是 nightly-only；该警告本身不代表格式失败。

## [2026-08-02 16:56] 会话折叠沉淀

- Goal 的终态是 COMPLETED/CANCELLED；PAUSED 和 BLOCKED 属于未结束状态，但 requiresAgentWork() 为 false，不应触发 Agent 继续循环。
- Goal 的生产写入应通过 GoalStore.update 或 createIfNoOpenGoal 完成，避免 read-modify-save 导致并发更新丢失。
- JsonlGoalStore 使用 Base64URL 无填充编码的 v2-&lt;sessionId&gt;.jsonl 文件名，旧版清洗文件名只用于兼容读取，并在下一次写入时迁移。
- JsonlGoalStore 的一致性方案是同会话 JVM 锁、跨进程文件锁、临时文件写入和原子 move；不同会话可并行。
- Goal 持久化遇到未知状态值时必须失败关闭，不能静默回退为 ACTIVE/PENDING。
- Goal 内容注入系统提示词前必须进行 XML 转义，防止用户提供的目标文本突破标签边界。
- 主 Agent 持有 Goal 推进和退出守卫责任；子 Agent 必须关闭 Goal 守卫，不能因主会话未完成 Goal 而自行续跑。
- 普通用户首条非命令消息应自动确保当前会话存在 Goal；GoalCommand 负责显式 Goal 命令。
- goal_status 是只读工具，应加入 ToolMetadata 的只读工具集合。
- loopra-bin 当前完整测试编译存在与 Goal 无关的既有阻塞：UserMessageSanitizerTest 引用了已不存在的 visionService。

## [2026-08-02 18:55] 会话折叠沉淀

- 用户偏好使用中文回复。
- OpenCode 的 Console Go 错误“Upstream request failed”曾在上游 Issue #37231 中被报告：OpenCode 1.18.1 下多个或全部 Go 模型失败，替换 API key 无效，相关请求仍会出现在统计中。
- 相关排查涉及 OpenCode 的 OpenAI 兼容请求转换函数 `fromOpenaiRequest` 及 Go 模型的 OpenAI 路由支持。

## [2026-08-02 19:03] 会话折叠沉淀

- OpenCode agent 的 `top_p` 配置映射到内部 `topP`，配置 schema 允许有限数值，且使用空值合并解析时显式 `0` 会被保留，不会自动回退为默认值。
- OpenCode Go/Console Go 的“Upstream request failed”是泛化错误，既可能是请求参数或模型兼容性问题，也可能是 Console 或上游服务故障，不能仅凭客户端错误文本判断根因。
- 排查此类问题应同时检查实时 `/models` 目录、实际请求日志和下游 HTTP 状态；模型出现在目录中不等于推理请求一定可用。
- 用户偏好中文回复。

## [2026-08-02 19:15] 会话折叠沉淀

- Chat Completions 请求中的 assistant `tool_calls[]` 每个工具调用都必须包含非空 `id`。
- `ChatCompletionsApiProtocol.buildRequest` 当前直接使用 `ToolCallEntry.id()` 序列化工具调用 ID，没有在协议层校验或补全缺失值。
- 流式工具调用由 `ChatCompletionsApiProtocol.accumulateToolCalls` 按 `index` 拼接；该逻辑可能先创建工具调用条目，随后再等待流块提供 ID。
- `MessageHealer` 当前主要修复 tool 消息的 `tool_call_id`、重复 ID、无效工具名和非法参数，未覆盖 assistant `tool_calls[]` 缺失 ID 的情况。

## [2026-08-02 19:35] 会话折叠沉淀

- 项目通过 ResponsesApiProtocol 发送 Responses API 请求时，function_call input item 需要同时设置顶层 id 和 call_id，两者使用同一个 ToolCallEntry.id()，以兼容严格的 Responses 网关。
- 当前相关接口是 Responses API 的 /v1/responses 路径，不应按 Chat Completions 的 tool_calls 嵌套结构分析该错误。
- loopra-bin 的测试目前存在独立编译阻塞：UserMessageSanitizerTest.java 引用了 UserMessageSanitizer 中不存在的 visionService 字段。
- 用户偏好使用中文回复。

## [2026-08-02 19:48] 会话折叠沉淀

Responses API 发送到 DeepSeek/Console Go 时，不能只回放 reasoning item 的 summary/encrypted_content；其兼容层需要从 assistant 的 reasoningContent 构造 `reasoning.content` 数组，元素为 `{type:"reasoning_text",text:...}`，否则上游会报 thinking mode 的 `reasoning_content` 未传回。

## [2026-08-02 20:14] 会话折叠沉淀

- DeepSeek Responses API 是无状态接口，工具调用续轮需要由客户端完整回放上下文。
- DeepSeek Responses API 的 `reasoning` 输入项支持明文 `content`，并将其合并到相邻 assistant 消息；`summary` 和 `encrypted_content` 不受支持，不能依赖它们恢复上游 `reasoning_content`。
- 回放 Responses API 的 reasoning item 时必须移除输出专用字段 `status`。
- `ResponsesApiProtocol` 回放历史思考内容的约定是使用 `content: [{"type":"reasoning_text","text":"..."}]`，内容来源为 `ChatMessage.reasoningContent`，且不覆盖已有的非空 content。
- 修改 Responses API reasoning 回放逻辑后，应补充请求构造回归测试并运行相关模块的编译和测试。

## [2026-08-02 20:21] 会话折叠沉淀

- DeepSeek Responses API 的输入 `reasoning` 仅支持明文 `content`；`summary` 和 `encrypted_content` 不受支持。
- DeepSeek Responses API 会把明文 reasoning 和 `function_call` 合并到相邻 assistant 消息，`function_call_output` 可直接回传。
- 项目中的 Responses API 实现位于 `loopra-bin/src/main/java/site/sorghum/loopra/bin/model/ResponsesApiProtocol.java`，流式 reasoning 事件包括 `response.reasoning_text.delta`。
- `response_reasoning` 在当前消息模型中归属于 assistant 消息级，而不是单个 `ToolCallEntry`；旧工具调用级字段只用于兼容迁移。
- OpenAI Responses API 的加密 reasoning 回放与 DeepSeek 的明文 reasoning 回放需要按供应商能力区分，不能统一假设支持 `encrypted_content`。
- 用户偏好中文沟通。
