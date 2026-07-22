# Loopra 项目记忆

本文件由 Loopra 自动维护，记录跨会话沉淀的项目关键事实（架构决策、约定、踩坑、用户偏好等）。
会话启动时自动注入 AI 上下文。可手动删除过期条目，请勿手动修改条目格式。

---

## [2026-07-17 20:45] 会话折叠沉淀

技能文件放在 ~/.loopra/skills/ 下（不是 ~/.codex/skills/）。安装 browser-harness 后需用 `browser-harness skill > ~/.loopra/skills/browser-harness/SKILL.md` 注册技能。

## [2026-07-19 22:44] 会话折叠沉淀

模型渠道配置已演进为 modelChannels[].models 对象条目：name、contextTokens、imageInput、可选 price；旧字符串列表与根级 price 保持读取兼容。图片输入和上下文大小运行时仅读取当前渠道的模型配置，不再依赖模型元数据。

## [2026-07-19 22:57] 会话折叠沉淀

## [2026-07-21 17:28] 会话折叠沉淀

模型渠道通过 apiProtocol 选择协议：chat_completions（默认，兼容旧配置）或 responses；生产模型请求统一使用 LoopraConfig/ModelChannel.apiUrl()，HttpModelClient 将 Responses API 的 input、函数工具/结果、SSE、usage、refusal 和推理 item 映射到现有 ModelClient/Agent 流程。

## [2026-07-21 17:29] 会话折叠沉淀

- 项目同时支持 Chat Completions 与 Responses API；`apiProtocol` 的 `response`、`responses` 均归一化为 `responses`，其他值默认使用 `chat_completions`。
- `ToolCallEntry` 使用可选 `responseReasoning` 保存 Responses API reasoning item 的原始 JSON，并保留三参数构造器兼容既有调用。
- Responses API 的 reasoning item 需要随助手工具调用持久化，并在下一次请求中放在对应 `function_call` 和 `function_call_output` 之前回放。
- Responses API 启用 reasoning 时，请求包含 `reasoning.encrypted_content`，以便无状态工具调用链能够回放推理 item。
- 流式请求一旦已经向上层输出内容或推理增量，后续连接错误不能整体重试，否则会产生重复前缀。
- Responses API 的 `response.failed` 和 `response.incomplete` 属于终止错误，应触发 `onError`；用户主动中断必须触发 `onDone`，否则 AgentLoop 的流等待锁可能无法释放。
- 旧配置迁移时渠道协议固定为 `chat_completions`，以维持历史行为。

## [2026-07-21 18:35] 会话折叠沉淀

模型 HTTP 客户端采用传输层与协议策略分离：HttpModelClient 只负责 HTTP、重试、中断和日志；ModelApiProtocol/ModelApiProtocols 负责协议选择；ChatCompletionsApiProtocol 与 ResponsesApiProtocol 分别负责请求、非流式响应和 SSE 映射；共享 usage、think 标签与 tool_calls 逻辑在 AbstractModelApiProtocol。新增 API 规范应新增协议实现并在 ModelApiProtocols 注册，避免向 HttpModelClient 添加协议分支。

## [2026-07-21 20:09] 会话折叠沉淀

Responses API 流只有收到 `response.completed` 才可视为成功并发出累积工具调用；EOF、`[DONE]` 或连接关闭但缺少该终态必须 `onError`，且成功终态后的事件应忽略。

## [2026-07-22 15:10] 会话折叠沉淀

命令校验模型配置使用 validationModel + validationModelChannelId 跨渠道定位；AgentLoop 在 FunctionTool.call 前对非只读、非显式纯查询/控制工具调用独立 ToolCallValidator，校验失败或响应非严格 JSON 布尔 allow 时 fail-closed。

## [2026-07-22 15:26] 会话折叠沉淀

子代理模型缓存约定：每个 SubAgent 使用固定且唯一的 `父会话ID:sub-agent:UUID` session affinity，同时驱动 prompt_cache_key 与全部会话亲和请求头；子代理首轮前冻结 system 附加指令和结构化 tools，生命周期内不得改变前缀。
