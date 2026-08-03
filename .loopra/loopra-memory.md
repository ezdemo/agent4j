# Loopra 项目记忆

本文件由 Loopra 自动维护，记录跨会话沉淀的项目关键事实（架构决策、约定、踩坑、用户偏好等）。
会话启动时自动注入 AI 上下文。可手动删除过期条目，请勿手动修改条目格式。

---
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

## [2026-08-02 22:37] 会话折叠沉淀

GoalCommand 的 /goal create 成功后需返回 CommandResult.LOOP 并修改 MessageWrapper 为执行提示，否则 Agent 不会自动开始执行 Goal（已修复 2026-08-02）。UserMessageSanitizerTest 中 visionService 引用已清理。

## [2026-08-02 23:19] 会话折叠沉淀

ImageReadTool.read_image 在模型不支持图片输入时不可用：通过 ToolContext.getLoopController() → getModelClient() 获取当前模型，经静态注入的 ModelModalityProvider.getModalitySupport(channelId, modelName).imageInput() 判断，不支持时返回 "MODEL_NOT_SUPPORTED: ..." 错误；无控制器/无 provider（单元测试、CLI）时跳过拦截保持兼容。

## [2026-08-03 12:10] 会话折叠沉淀

ToolCallValidator 校验模型调用失败（超时等异常）时返回 Decision.failed（新增状态），AgentLoop 在 HITL 拦截处回退到普通人工审批（interceptForHITL 弹 /agree /deny），而非直接拒绝终止；AI 明确判定危险（allow=false）仍直接拒绝。Decision record 字段为 (allowed, requiresHuman, failed, reason)。

## [2026-08-03 15:02] 会话折叠沉淀

Gitee Electron 流水线 `.workflow/electron-pipeline.yml` 监听 main；脚本 `.release/ci-package-electron.sh` 自举 Node 22.14.0 + pnpm 10.24.0，在 Linux runner 安装 Wine 后执行 `electron-builder --mac --win --linux`，要求并上传 `.zip`、`.exe`、`.deb` 三端产物。macOS 产物为未签名 zip，签名/公证需 Apple 机器或后续阶段。

## [2026-08-03 18:34] 会话折叠沉淀

PowerShell 脚本（.ps1）必须保存为 UTF-8 with BOM：PS 5.1 对无 BOM 文件按系统 ANSI(GBK) 读取，UTF-8 中文会被误读成双字节乱码，可能吞掉 { } 括号导致解析失败（典型报错"表达式或语句中包含意外的标记 }"）；CRLF 版可能侥幸通过，但发布打包转成 LF 后必现。installDist 的 install.ps1/uninstall.ps1 与 .release 脚本均已加 BOM（2026-08-03）。新脚本或修改后检查：文件头为 EF BB BF 且 Parser::ParseFile 无错误。


## [2026-08-03 19:26] 会话折叠沉淀

桌面首页（DesktopHome.vue）左下角菜单 2026-08-03 精简：只保留「技能/工具」两个文字入口；「子代理/数据面板」入口移到设置页左侧导航底部「功能」区（仅 ?desktopShell=1 时显示，通过 emit open-sub-agents/open-dashboard 由 DesktopShell 切换视图）；「设置」改为齿轮图标（desktop-settings-button），与主题按钮、服务进程管理并排一行。

## [2026-08-03 19:30] 会话折叠沉淀

桌面首页（DesktopHome.vue）左下角菜单最终布局（2026-08-03 二次调整）：第一行「技能」整行文字按钮；第二行「设置」文字按钮（flex:1 占满左侧）+ 右侧图标区（服务进程管理、工具图标 desktop-tools-button、主题切换 desktop-theme-button）。「子代理/数据面板」入口在设置页左侧导航底部「功能」区（仅 ?desktopShell=1 时显示），通过 emit open-sub-agents/open-dashboard 由 DesktopShell 切换视图。
