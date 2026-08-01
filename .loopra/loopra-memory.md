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
