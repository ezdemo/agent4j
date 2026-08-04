# Loopra 项目记忆

本文件由 Loopra 自动维护，记录跨会话沉淀的项目关键事实（架构决策、约定、踩坑、用户偏好等）。
会话启动时自动注入 AI 上下文。可手动删除过期条目，请勿手动修改条目格式。

---
## [2026-08-04 00:51] 会话折叠沉淀

模块化重构全量验证通过（2026-08-04）：mvn clean compile/test/package 全绿（loopra-model 217、loopra-harness 50、loopra-web 31 个测试，0 失败），loopra-web fat jar 冒烟启动正常（42 个内置工具、/api/system/health=200）；loopra-model 对 harness/acp 包零 import。前端静态资产由 loopra-front 构建后同步到 loopra-web/src/main/resources/static（CI 脚本 .release/ci-sync-web-assets.sh；本地等价操作：pnpm build 后复制 dist/renderer 内容，保留 static/config.json）。loopra-bin 包名（site.sorghum.loopra.bin.*）保留未改，仅物理拆分为多 Maven 模块，故无 JPMS/包名迁移风险。

## [2026-08-04 01:06] 会话折叠沉淀

- loopra 项目多模块化重构已于 2026-08-04 全量验证通过：mvn clean compile/test/package 全绿（loopra-model 217、loopra-harness 50、loopra-web 31 个测试，0 失败），loopra-web.jar 冒烟启动正常（42 个内置工具、/api/system/health=200），loopra-model 经正则扫描确认对 harness/acp 包零 import。
- loopra 三条解耦红线（后续修改内核时须保持）：工具来源只走 ToolScanProvider SPI、图片结果只走 ImageToolResult 文本协议、父输出只走 ParentOutputHolder；新增上层能力应在 loopra-harness 实现、由 loopra-web 聚合。
- 踩坑教训：loopra-web 资源过滤（filtering=true）遇到二进制字体（ttf/woff/woff2 等）会报 MalformedInputException，必须在 maven-resources-plugin 配置 nonFilteredFileExtensions 排除；前端重建引入新二进制资产后需同步检查此处。
- 前端静态资产流程：loopra-front 构建后同步到 loopra-web/src/main/resources/static（CI 脚本 .release/ci-sync-web-assets.sh；本地为 pnpm build 后复制 dist/renderer 内容并保留 static/config.json）。
- loopra-bin 包名（site.sorghum.loopra.bin.*）保留未改，仅物理拆分为多 Maven 模块，无 JPMS/包名迁移风险。
- 项目事实：项目根路径 C:\Users\sorghum\IdeaProjects\agent4j，当前版本 26.8.3.2，基于 Solon 4.0.4 框架；maven-resources-plugin 使用 @...@ 定界符（避免与 Solon ${...} 冲突）。</摘要>

## [2026-08-04 01:10] 会话折叠沉淀

loopra-web 打包踩坑：根 pom 对 loopra-web 资源启用了 filtering，前端重建新增 KaTeX 二进制字体（ttf/woff/woff2）会触发 MalformedInputException 构建失败。修复：loopra-web/pom.xml 的 maven-resources-plugin 配置 nonFilteredFileExtensions 加入 ttf/otf/woff/woff2。新增任何二进制静态资产时注意同样问题。

## [2026-08-04 04:38] 会话折叠沉淀

loopra 模块边界（2026-08-04 压薄后定稿）：loopra-model 只含 model(ChatModel)/agent 推理内核(AgentLoop/SubAgent/context/hitl/listener/memory/output/prompt/resilient)/tool 抽象/session 的 SessionStore 接口与 SessionFileChangeTracker/util，另新增 SPI 包 agent/spi（AgentConfig/GoalGuard/SessionUsageSink/ToolPolicyProvider）。LoopraAgent、goal/checklist/workspace/command、SessionService/JsonlSessionStore、LoopraConfig/ConfigService/ConfigChangedEvent、AppConfig 全部下沉 loopra-harness（FQCN 不变）。装配：LoopraConfig implements AgentConfig、SessionService implements SessionUsageSink、GoalGuardImpl 与 ConfigServiceToolPolicyProvider 由 LoopraAgent 构造时注入。loopra-acp 依赖 loopra-harness（非 model）。内核换型方法名：getSessionUsageSink/setSessionUsageSink（原 getSessionService/setSessionService）。

## [2026-08-04 09:01] 会话折叠沉淀

- LoopraAgent（loopra-harness .../bin/agent/core/LoopraAgent.java）是私有构造+Builder 的具体门面类，内部硬编码：ToolSystemInitializer、文件型 SessionService、WorkspaceManager、GoalGuardImpl、ConfigServiceToolPolicyProvider，并用 Dami.bus() 监听 "config.changed" 实现热更新（dispose 时需注销监听）。
- loopra-web 的 AgentService 按 sessionKey（workspacePath::sessionName）用 LRU 缓存 LoopraAgent，经 buildLightweight() 创建后依次 bindSession、setListener(WebUsageListener)、setOutput(SseAgentOutput 或 AgentOutput.NOOP)。
- Loopra 现存可扩展点仅三个：AgentLoopListener、AgentOutput、ChatCommand/ChatCommandRegistry；其余行为（会话持久化、工作区、工具策略、目标守卫、系统提示词 DEFAULT_PROMPT）均不可替换，这是本次接口抽取的动机。
- 用户偏好中文回复。

## [2026-08-04 09:13] 会话折叠沉淀

LoopraAgent 定制接口已开放（2026-08-04）：Builder 新增三个可选注入点 goalGuard(GoalGuard)、toolPolicyProvider(ToolPolicyProvider)、sessionStore(SessionStore)，未设置时默认 GoalGuardImpl/ConfigServiceToolPolicyProvider/JsonlSessionStore（工作区会话目录），行为与改造前完全一致；SessionService 新增 (ConversationContext, SessionStore) 构造，原 (ctx, Path) 构造委托为默认 JsonlSessionStore。initSessionAndLoop 签名改为接收 Builder。web 等上层可通过 builder 替换 Goal 守卫、工具策略与会话持久化；测试 SessionServiceTest.usesInjectedSessionStore 验证注入装配。

## [2026-08-04 10:14] 会话折叠沉淀

loopra 遗留项 5.1/5.2 已解决（2026-08-04，阶段四，待提交）：LoopraAgent.Builder 新增 toolSystem(ToolSystemInitializer.Result) 注入点，注入后跳过 ToolSystemInitializer.initialize（注入单位是完整 Result 而非单个 registry，因构造器还需 promptPrefix 构建 ConversationContext）；web AgentService 按工作区绝对路径缓存 Result（sharedToolSystems），createAgent 注入复用，reinitialize 清空，非默认工作区懒构建；副作用：~/.loopra/loopra.md 首次真正进入 Agent system prompt（原先只进从未使用的共享 registry）。SessionStore 接口新增 default shutdown()；LoopraAgent 以 ownsSessionStore（b.sessionStore==null 即自建）标记，dispose 仅关自建 store，修复 web LRU 淘汰泄漏。ToolRegistry.refresh() 已加 synchronized 防共享注册表并发刷新损坏；子代理仍走 copy()。测试 LoopraAgentInjectionTest（harness，3 例）。全量 302 测试 0 失败。

## [2026-08-04 10:14] 会话折叠沉淀

loopra 测试坑（2026-08-04）：① 单测中隔离 user.home 的目录不要用 JUnit @TempDir——chat 路径的 DJL 分词器会向 user.home 释放原生 DLL（.djl.ai/tokenizers/*.dll），DLL 被 JVM 加载后 Windows 无法删除，@TempDir 清理直接报 "Failed to close extension context"，须用 Files.createTempDirectory 手动创建且不强制删除；② 无 Solon 容器的单测里构建 LoopraAgent 时，默认 ConfigServiceToolPolicyProvider 会因 ConfigService.config 为 null 而 NPE，需注入空策略 ToolPolicyProvider 替身。

## [2026-08-04 11:15] 会话折叠沉淀

- 项目偏好中文回复。
- 计划模式的动态约束不应写入稳定的 system prompt 前缀；应由 AgentLoop 在当前工具指令尾部按需注入，以维护前缀缓存命中。
- 计划模式必须双层防护：向模型暴露的工具集合只保留 ToolMetadata 标记为只读的工具，实际 dispatch 时仍须硬拒绝任何非只读调用。
- 会话元数据需采用读改写方式保存 title、planMode 等字段，避免单字段更新覆盖其他持久化状态。
- 父代理处于计划模式时，子代理必须继承该状态，并在冻结工具/生成工具规范前将可用工具收敛为只读集合。
- 计划模式闭环约定：/plan 进入只读探索，submit_plan 提交待审查计划并发送 plan_submitted，/execute 消费计划、退出模式、发送 mode_changed 并将计划注入后续执行消息。

## [2026-08-04 12:16] 会话折叠沉淀

- 项目计划模式由 `LoopraAgent` 持有真实状态，包含 `NORMAL/PLAN` 模式和待审查计划，不能只依赖系统提示词约束。
- 计划模式采用工具白名单，并在工具执行层进行二次拦截；计划生成后暂停，必须显式批准后才能进入执行阶段。
- 会话持久化需要保存计划模式和待审查计划；历史截断、清空等会使旧计划失效的操作必须同步清理待审查计划。
- CLI 可继续兼容 `/plan`、`/execute` 命令，但 Web 前端应通过专用 API 和显式界面控件操作，不应向聊天输入框注入斜杠命令。
- Web 流式聊天入口是 `POST /api/chat/stream`，由 `ChatController` 转交 `AgentService.chatStream()`，再调用 `LoopraAgent.chat()`。
- 前端主聊天视图位于 `loopra-front/src/views/Chat.vue`，输入组件位于 `loopra-front/src/components/ChatInput.vue`。
- 用户偏好中文回复。

## [2026-08-04 13:15] 会话折叠沉淀

- 计划模式状态和待审计划通过会话 `.meta` 文件中的 `planMode`、`pendingPlan` 字段持久化，元数据更新必须保留其他字段。
- Web 批准计划使用聊天动作 `execute_plan`，执行指令以 `web_hidden` 用户消息送入模型，不应显示在普通聊天界面。
- 批准计划采用 prepare/complete/restore 生命周期：准备时退出计划模式但保留 pending plan，模型成功启动后才清除，启动失败或用户中断时截断执行消息并恢复计划模式与待审计划。
- `ChatMessage.webHidden` 必须同时支持会话持久化和上下文折叠，折叠序列化不得丢失该标记。
- 首条消息前开启计划模式可能只创建 `.meta` 而没有 `.jsonl`；会话列表和删除逻辑必须支持这种纯元数据会话。
- 前端计划批准需要独立忙状态阻止快速双击，并在 SSE 完成、错误或同步异常后释放该状态。
- 修改计划模式相关后端逻辑后应运行 `mvn -pl loopra-web -am test`；修改前端交互后应运行 `pnpm exec vitest run` 和 `pnpm build`。

## [2026-08-04 13:16] 会话折叠沉淀

Web 计划模式契约：`/plan` 只切换模式；模型通过 `submit_plan` 产生待审计划；Web 批准使用 `action=execute_plan`，后端 prepare/complete/restore 事务处理并用 `web_hidden` 隐藏内部执行指令；执行启动失败/空输出时恢复计划，已产生实质 assistant/tool 输出后中断则保留历史并消费计划；前端终态后重新同步 `/api/agent/mode`。

