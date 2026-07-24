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
