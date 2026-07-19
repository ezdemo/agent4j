# Loopra 项目记忆

本文件由 Loopra 自动维护，记录跨会话沉淀的项目关键事实（架构决策、约定、踩坑、用户偏好等）。
会话启动时自动注入 AI 上下文。可手动删除过期条目，请勿手动修改条目格式。

---

## [2026-07-17 20:45] 会话折叠沉淀

技能文件放在 ~/.loopra/skills/ 下（不是 ~/.codex/skills/）。安装 browser-harness 后需用 `browser-harness skill > ~/.loopra/skills/browser-harness/SKILL.md` 注册技能。

## [2026-07-19 22:44] 会话折叠沉淀

模型渠道配置已演进为 modelChannels[].models 对象条目：name、contextTokens、imageInput、可选 price；旧字符串列表与根级 price 保持读取兼容。图片输入和上下文大小运行时仅读取当前渠道的模型配置，不再依赖模型元数据。

## [2026-07-19 22:57] 会话折叠沉淀

子代理与父代理共用会话文件变更 tracker scope。子循环必须设置 drainFileChanges=false，避免提前 drain 记录；父 AgentLoop 在包含 sub_agent 的工具批次完成后统一 drain，才能把子代理编辑持久化为主消息“已编辑 X 个文件”并支持撤销。
