# Prompt / Tool Cutin 插件化说明

原 `ToolSystemInitializer` 静态拼串已迁移为切面插件：

- `LoopraPromptPlugin (loopra-prompt, order=-1000)` 在 `BEFORE_MODEL` 聚合 `PromptRegistry` 中全部 `PromptSliceProvider` 并注入 `ModelCallRequest` + `LoopContext`。内置 4 切片：core-identity(100) / skill-contract(300) / env-info(500) / project-doc(900,尾部缓存友好)。外部 JAR 只需实现 `PromptSliceProvider` 并 `registry.register(...)` 即可热插拔，`LoopraPluginRuntime.setEnabled("loopra-prompt", false)` 一键下线。
- `LoopraToolGatewayPlugin (loopra-tool-gateway, order=-900)` 桥接 `ToolScanUtil/Solon/skill` 的 `FunctionTool` 到 `cutin Tool` via `LoopRegistrar.registerTool`。尊重 `ToolRegistry.isEnabled` 禁用列表，`AgentLoop.refreshTools()` 会重启网关以同步最新禁用状态。外部工具直接 `registrar.registerToolProvider(...)` 独立贡献，与网关并存。

注册方式：`AgentLoop` 构造时注入 `PromptRegistry` 与宿主 `LoopraPromptHost / LoopraToolHost` 到 `PluginBeanManager`，两插件在 `createCutinPlugins()` 最先注册，保证后续 `LoopraModelPolicyPlugin` 读到已注入的 system 与过滤后的 tool 列表。
