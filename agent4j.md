# Agent4j — Java AI Agent 框架（AI Agent 完整知识库）

> **用途**：本文档是 AI 代理（coding agent）操作 Agent4j 项目时的完整参考知识库。
> 涵盖项目结构、架构设计、工具系统、配置参考、关键类和优化历史。

---

## 1. 项目概述

Agent4j 是一个纯 Java 17 实现的 AI 编码代理框架，将 LLM 与可扩展的工具系统深度整合，形成能自主工作的编程助手。灵感来源于 Claude Code / Devin，在 Java 生态中完整实现，提供 **CLI 控制台**、**Vue3 Web 界面** 和 **Tauri 桌面应用** 三种交互方式。

| 指标 | 数值 |
|------|------|
| Java 源文件 | 189 个（18,818 行） |
| Vue 组件 | 19 个（14,450 行） |
| 总代码量 | ~43K LOC |
| Git 提交 | 238+ commits |
| 测试用例 | 103 个（12 个测试类） |

### 核心能力

| 能力 | 说明 |
|------|------|
| 🤖 **推理循环** | 多轮对话上下文中自主思考 → 调用工具 → 观察结果 → 继续推理 |
| 🔧 **工具系统** | 30+ 内置工具（文件/代码/终端/网络/记忆/作业/计划/子代理/工作区），支持 Plugin/Skill/MCP/OpenAPI 扩展 |
| 🔌 **模型无关** | OpenAI 兼容 API，可对接 DeepSeek、Ollama、OpenAI、Claude、Gemini 等 |
| 📋 **计划模式** | 只读探索 → 提交计划 → 审批后执行，安全可控 |
| 🛡️ **安全机制** | 风暴断路器（防循环调用）、路径穿越防护、原子编辑回滚、HITL 人工审批、ReasonBreaker |
| 💾 **会话管理** | JSONL 持久化、异步缓冲写入、定时刷入、语义折叠、Token 用量追踪 |
| 🧩 **子代理** | 复杂任务委派（单任务/多任务并行），隔离执行上下文，AgentOutput 传播 |
| 🔍 **代码分析** | 源码定位、符号大纲、标识符查找、正则/glob 搜索 |
| 🌐 **联网能力** | 网页抓取、代码搜索 |
| 📝 **记忆服务** | 跨会话持久化键值记忆（global/project 双作用域） |
| 🔌 **MCP 协议** | 支持 Model Context Protocol 工具注册 |
| 📄 **OpenAPI** | 支持 OpenAPI 规范自动导入为工具 |
| 🎨 **多界面** | 控制台 CLI / Vue3 Web UI / Tauri 桌面应用 |
| 🧩 **Pluin 系统** | `~/.agent4j/plugin/` 下的技能插件自动发现和注册 |
| 🧠 **Skill 系统** | 基于 Solon AI Skill 的 Markdown+YAML 技能文件，支持 inline/subagent 两种运行模式 |
| 📦 **工作区共享** | 子代理间通过 SharedWorkspace 共享 KV/文档数据，事件总线通知变更 |

---

## 2. 目录结构

```
agent4j/
├── pom.xml                                # 父 POM（多模块 Maven 项目）
├── README.md / SKILL_README.md            # 项目说明 / Skill 系统说明
├── ANALYSIS.md / TEST_SUMMARY.md          # 架构分析 / 测试总结
├── UPGRADE_CHECKLIST.md                   # 升级检查清单
├── agent4j.md                             # 本文件（AI Agent 知识库）
├── compile.log / fix-app.cjs             # 构建日志 / 辅助脚本
├── icon.png / intro/                      # 项目图标 / 介绍页
├── logs/                                  # 运行时日志
│   └── agent4j_*.log
│
├── agent4j-tool/                          # [Maven 模块] 核心工具库（Solon-agnostic）
│   ├── pom.xml
│   └── src/main/java/site/sorghum/agent4j/tool/
│       ├── AgentTool.java                 # 工具抽象基类
│       ├── ToolContext.java               # 工具执行上下文（路径校验/会话注入）
│       ├── ToolParameter.java             # 工具参数定义
│       ├── ToolResult.java                # 工具执行结果
│       ├── HitlRequiredException.java     # HITL 审批异常
│       ├── interact/                      # 交互工具
│       │   ├── AskChoiceTool.java         # 用户选择菜单（2-6 选项）
│       │   ├── TodoWriteTool.java         # 任务跟踪列表
│       │   └── InteractionService.java
│       ├── job/                           # 后台作业工具
│       │   ├── JobRegistry.java           # 作业注册表
│       │   ├── JobService.java            # 作业服务
│       │   ├── RunBackgroundTool.java     # 启动后台进程
│       │   ├── JobOutputTool.java         # 读取作业输出
│       │   ├── WaitForJobTool.java        # 等待作业完成
│       │   ├── StopJobTool.java           # 停止作业
│       │   └── ListJobsTool.java          # 列出所有作业
│       ├── memory/                        # 记忆工具
│       │   ├── MemoryService.java
│       │   ├── RememberTool.java          # 保存记忆
│       │   ├── RecallMemoryTool.java      # 读取记忆
│       │   └── ForgetTool.java            # 删除记忆
│       ├── plan/                          # 计划工具
│       │   ├── PlanService.java
│       │   ├── SubmitPlanTool.java        # 提交计划
│       │   ├── RevisePlanTool.java        # 修订计划
│       │   └── MarkStepCompleteTool.java  # 标记步骤完成
│       ├── terminal/                      # 终端工具
│       │   ├── CommandAllowlist.java      # 命令白名单
│       │   ├── CommandChainParser.java    # 命令链解析
│       │   ├── CommandTokenizer.java      # 命令分词
│       │   ├── ProcessTreeKiller.java     # 进程树终止
│       │   └── SmartDecoder.java          # 智能解码
│       └── solon/                         # Solon 集成层
│           ├── SolonToTools.java          # Solon 工具转换接口
│           ├── ToolManager.java           # 工具管理器
│           ├── common/Agent4JSkillProvider.java # Skill 文件系统提供者
│           ├── mcp/Agent4JMcpSkill.java         # MCP 协议技能
│           ├── openapi/Agent4JOpenApiSkill.java # OpenAPI 技能
│           ├── plugin/                         # 插件系统
│           │   ├── PluginAgentTool.java        # 插件技能包装
│           │   ├── PluginConfig.java           # 插件配置
│           │   └── PluginToolProvider.java     # 插件自动扫描
│           ├── sys/Agent4JSysSkill.java        # 系统技能（时钟等）
│           └── webfetch/Agent4JWebSkill.java   # Web 技能（webfetch/codesearch）
│
├── agent4j-bin/                          # [Maven 模块] 可执行入口
│   ├── pom.xml
│   └── src/main/java/site/sorghum/agent4j/bin/
│       ├── AppConfig.java                 # main 入口
│       ├── agent/                         # 代理核心（~2000 行）
│       │   ├── Agent4jAgent.java          # Agent 工厂+外观（554 行）
│       │   ├── AgentLoop.java             # 推理循环核心（1300 行）
│       │   ├── AgentLoopListener.java     # 循环事件监听
│       │   ├── AgentOutput.java           # 输出接口
│       │   ├── ConsoleAgentOutput.java    # 控制台输出
│       │   ├── SubAgent.java              # 子代理实现
│       │   ├── SubAgentAgentOutput.java   # 子代理输出传播
│       │   ├── ChatMessage.java           # 消息模型（290 行，多模态）
│       │   ├── ToolCallEntry.java         # 工具调用条目
│       │   ├── ConversationContext.java   # 会话上下文管理
│       │   ├── PromptPrefix.java          # 前缀缓存（序+system）
│       │   ├── ContextFolding.java        # 上下文折叠（430 行）
│       │   ├── MessageHealer.java         # 消息修复器（180 行）
│       │   ├── StormBreaker.java          # 风暴断路器（170 行）
│       │   ├── ReasonBreaker.java         # 推理断路器
│       │   ├── Scavenger.java             # 工具调用回收
│       │   ├── ChoiceOption.java          # 选择选项
│       │   ├── HitlState.java             # HITL 状态枚举
│       │   └── LogLevel.java              # 日志级别
│       ├── builtin/                       # 内置组件
│       │   ├── TaskTool.java              # 单子代理工具
│       │   ├── MultiTaskTool.java         # 并行多子代理工具
│       │   ├── WorkspaceListTool.java     # workspace_list
│       │   ├── WorkspaceReadTool.java     # workspace_read
│       │   ├── WorkspaceWriteTool.java    # workspace_write
│       │   └── WorkspaceWatchTool.java    # workspace_watch（阻塞事件）
│       ├── command/                       # 聊天命令
│       │   ├── ChatCommand.java           # 命令接口
│       │   ├── ChatCommandContext.java
│       │   ├── ChatCommandRegistry.java
│       │   └── impl/
│       │       ├── NewSessionCommand.java # /new
│       │       ├── PlanCommand.java       # /plan
│       │       ├── ExecuteCommand.java    # /execute
│       │       ├── CompactCommand.java    # /compact
│       │       ├── RetryCommand.java      # /retry
│       │       ├── RewindCommand.java     # /rewind N
│       │       ├── SessionsCommand.java   # /sessions
│       │       ├── LoadCommand.java       # /load N
│       │       ├── InitCommand.java       # /init
│       │       ├── HitlCommand.java       # /hitl
│       │       ├── AgreeCommand.java      # /agree
│       │       ├── DenyCommand.java       # /deny
│       │       └── HelpCommand.java       # /help
│       ├── config/
│       │   ├── Agent4jConfig.java         # 配置单例（~490 行）
│       │   └── ConfigService.java         # 配置服务
│       ├── mcp/
│       │   ├── McpManageService.java
│       │   ├── McpServerDTO.java
│       │   ├── McpToolInfoDTO.java
│       │   └── McpToolListDTO.java
│       ├── model/
│       │   ├── ModelClient.java           # 客户端接口
│       │   └── HttpModelClient.java       # HTTP SSE 客户端（558 行）
│       ├── session/
│       │   ├── SessionStore.java          # 存储接口
│       │   ├── JsonlSessionStore.java     # JSONL 存储（530 行，异步写入）
│       │   └── SessionService.java        # 会话服务
│       ├── tool/
│       │   ├── ToolDef.java               # 工具定义
│       │   ├── ToolDefHelper.java
│       │   ├── ToolDispatcher.java        # 工具调度器（plan/storm/HITL）
│       │   ├── ToolRegistry.java          # 工具注册表
│       │   ├── ToolScanUtil.java
│       │   ├── ToolSchemaFlattener.java   # Schema 展平
│       │   └── ToolSystemInitializer.java
│       ├── util/ONodeUtil.java
│       └── workspace/
│           ├── SharedWorkspace.java       # 共享工作区核心存储
│           ├── WorkspaceEventBus.java     # 事件总线
│           ├── WorkspaceManager.java      # 工作区管理
│           ├── WatchHandler.java          # 事件监听处理器
│           └── model/*.java               # KV/文档数据模型
│
├── agent4j-web/                          # [Maven 模块] Web API
│   ├── pom.xml
│   └── src/main/java/site/sorghum/agent4j/web/
│       ├── Agent4jWebApp.java            # Web 入口
│       ├── common/
│       │   ├── GlobalExceptionFilter.java
│       │   └── ServiceException.java
│       ├── controller/                    # 9 个 REST 控制器
│       │   ├── AgentController.java       # /api/agent/*
│       │   ├── ChatController.java        # /api/chat/*
│       │   ├── ConfigController.java      # /api/config/*
│       │   ├── GitController.java         # /api/git/*
│       │   ├── McpController.java         # /api/mcp/*
│       │   ├── OpenApiController.java     # /api/openapi/*
│       │   ├── SessionController.java     # /api/sessions/*
│       │   ├── SystemController.java      # /api/system/*
│       │   └── ToolController.java        # /api/tools/*
│       ├── doc/DocConfig.java
│       ├── model/                         # 30+ DTO
│       │   ├── ApiResponse.java / ChatRequest.java / ChatResultDTO.java
│       │   ├── AgentStatusDTO.java / SessionInfoDTO.java / ToolInfoDTO.java
│       │   ├── ConfigDTO.java / UsageDTO.java / SkillMetaDTO.java
│       │   └── ...
│       ├── service/
│       │   ├── AgentService.java          # Agent 服务（1092 行）
│       │   ├── OpenApiManageService.java
│       │   ├── SseAgentOutput.java        # SSE 输出
│       │   ├── SseEmitter.java            # SSE 发射器
│       │   └── WebUsageListener.java      # 用量追踪
│       └── market/
│           ├── SkillMarketController.java # 技能市场
│           └── impl/SkillhubMarket.java
│
├── agent4j-front/                        # [Vue3] 前端界面
│   ├── index.html / package.json / vite.config.js
│   └── src/
│       ├── App.vue / main.js
│       ├── assets/styles/main.css
│       ├── components/                    # 11 个组件
│       │   ├── BlockRenderer.vue          # 消息块渲染
│       │   ├── ChatInput.vue              # 聊天输入
│       │   ├── ConfirmDialog.vue          # 确认对话框
│       │   ├── GitPanel.vue               # Git 面板
│       │   ├── SetupScreen.vue            # 设置屏幕
│       │   ├── Sidebar.vue / StatusBar.vue / TabBar.vue / TitleBar.vue
│       │   ├── SplashScreen.vue           # 启动画面
│       │   └── UsagePanel.vue             # 用量面板
│       ├── composables/                   # 组合式函数
│       │   ├── useConfirm.js / useTerminal.js / useTheme.js
│       ├── router/index.js                # 路由
│       ├── services/
│       │   ├── api.js                     # Axios 封装
│       │   └── tauri.js                   # Tauri 桥接
│       ├── stores/app.js                  # Pinia 状态管理
│       ├── utils/helpers.js
│       └── views/                         # 7 个视图
│           ├── Chat.vue / Home.vue / Sessions.vue
│           ├── Settings.vue / Tools.vue / Help.vue / NotFound.vue
│
├── agent4j-tauri/                        # [Tauri 2.x] 桌面应用
│   ├── package.json
│   ├── INTEGRATION_COMPLETE.md / TAURI_WEB_INTEGRATION.md
│   └── src-tauri/
│       ├── Cargo.toml                     # Rust 依赖（serde, tauri-plugin-shell...）
│       ├── tauri.conf.json / build.rs
│       ├── capabilities/ / icons/ / resources/
│       └── src/
│           ├── main.rs                    # Rust 入口
│           └── lib.rs                     # Tauri 命令
│
└── agent4j-tui/                          # [占位] TUI 终端界面
```

---

## 3. 技术栈

### 后端（Java）

| 组件 | 版本 | 用途 |
|------|------|------|
| **Java** | 17+ | 开发语言 |
| **Maven** | 3.x | 构建工具 |
| **Solon** | 4.0.0-M1 | IoC 容器 / AOP / Web 框架 |
| **Snack4** | 4.0.49 | JSON 解析与序列化 |
| **Snack4-jsonpath** | 4.0.49 | JSONPath 查询 |
| **Lombok** | 1.18.34 | @Slf4j / @Getter / @Setter / @Builder |
| **OkHttp** | 4.12.0 | HTTP SSE 流式客户端 |
| **JUnit Jupiter** | 5.10.3 | 单元测试 |
| **Logback / SLF4J** | via Solon | 日志门面 |
| **Solon AI Skill** | 4.0.0-M1 | 技能文件系统（file/sys/web/pdf/harness） |

### 前端（Vue3）

| 组件 | 版本 | 用途 |
|------|------|------|
| **Vue** | 3.4+ | 前端框架 |
| **Vite** | 5.1+ | 构建工具 |
| **Pinia** | 2.1+ | 状态管理 |
| **Vue Router** | 4.3+ | 前端路由 |
| **Axios** | 1.6+ | HTTP 客户端 |
| **Ant Design Vue** | 4.2+ | UI 组件库 |
| **Marked** | 12.0+ | Markdown 渲染 |
| **@tauri-apps/api** | 2.0+ | Tauri 桥接 |

### 桌面端（Rust / Tauri）

| 组件 | 版本 | 用途 |
|------|------|------|
| **Tauri** | 2.x | 桌面应用框架 |
| **Rust** | 2021 edition | 桌面端原生语言 |
| **serde / serde_json** | 1.x | 序列化 |
| **tauri-plugin-shell** | 2.x | Shell 插件 |
| **flate2 / tar / zip** | - | 打包解压 |
| **sha2** | 0.10 | 哈希计算 |
| **ureq** | 2.x | HTTP 客户端 |

---

## 4. 架构设计

### 整体架构（4 层）

```
┌──────────────────────────────────────────────────────────────────┐
│                      交互层 Interface                             │
│   ┌──────────┐    ┌──────────────┐    ┌──────────────────┐       │
│   │ Console  │    │  Vue3 Web   │    │  Tauri Desktop   │       │
│   │   CLI    │    │   (Browser) │    │   (Rust+WebVew)  │       │
│   └────┬─────┘    └──────┬──────┘    └────────┬─────────┘       │
│        │                │                     │                 │
│        │        ┌───────▼────────┐            │                 │
│        └────────►  REST API      ◄────────────┘                 │
│                 │  (Solon Web)   │                               │
│                 └───────┬────────┘                               │
└─────────────────────────┼───────────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────────┐
│                 接口层 Agent4jWebApp / AppConfig                  │
│              IoC 容器 + Web 控制器 + 命令行入口                    │
└─────────────────────────┬───────────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────────┐
│              Agent4jAgent（工厂 + 外观 + 会话管理）                │
│   组装 ModelClient + ToolRegistry + SessionStore + Workspace     │
└────┬──────────────────────────────┬─────────────────────────────┘
     │                              │
┌────▼──────────┐    ┌──────────────▼─────────────────────────┐
│   AgentLoop   │    │         ToolDispatcher                  │
│   推理循环      │    │   PlanMode / StormBreaker / HITL      │
│   LLM ↔ Tool  │    │   Schema 展平 / 会话ID注入 / Hooks     │
│   上下文折叠    │    │   强制只读 / 路径校验                  │
│   消息修复     │    │                                        │
│   断路器       │    │         ToolRegistry                   │
│   子代理       │    │   注册/查询/动态刷新/禁用/屏蔽         │
└────┬──────────┘    └────────────────────────────────────────┘
     │
┌────▼──────────────────────────────────────────────────────────┐
│                   工具层 Tools                                  │
│  文件操作 │ 代码分析 │ 终端 │ 网络 │ 后台作业                    │
│  记忆 │ 计划 │ 交互 │ 子代理 │ 工作区共享 │ 插件/Skill/MCP/OpenAPI│
└─────────────────────────────────────────────────────────────────┘
```

### 模块依赖关系

```
agent4j (父 POM)
  ├── agent4j-tool    (核心工具库，无 Solon Web 依赖)
  │   └── 依赖: solon-ai-skill-{file,sys,web,pdf}, solon-ai-harness
  │
  ├── agent4j-bin     (可执行入口，依赖 agent4j-tool)
  │   └── 依赖: solon-lib, snack4, snack4-jsonpath, logback, okhttp
  │
  ├── agent4j-web     (Web API，依赖 agent4j-bin)
  │   └── 依赖: solon-web, solon-web-cors, maven-shade-plugin
  │
  ├── agent4j-front   (Vue3 SPA，独立于 Maven 构建)
  │
  ├── agent4j-tauri   (Tauri 桌面壳，内嵌前端 + 调用 Web API)
  │
  └── agent4j-tui     (占位，待实现)
```

### 推理循环流程（AgentLoop 核心编排）

```
用户输入
   │
   ▼
AgentLoop.run(message)
   │
   ├── 1. ctx.addUser(msg)                  ← 追加用户消息
   ├── 2. ctx.buildMessages()               ← 组装 system + history
   ├── 3. MessageHealer.heal()              ← 修复消息（截断/配对/去重）
   ├── 4. ContextFolding.fold()             ← 超长时折叠旧消息为摘要
   ├── 5. HttpModelClient.chatStream()      ← 流式调用 LLM (SSE)
   │       ├── onReasoningDelta()           ← 思考过程实时打印
   │       ├── onContentDelta()             ← 内容实时打印
   │       └── onToolCalls()               ← 工具调用收集
   │
   ├── 6. Scavenger.scavenge()              ← 从 reasoning 回收丢失的调用
   │
   ├── 7. 无 tool_calls → 返回文本回复
   │
   └── 8. 有 tool_calls
           ├── StormBreaker.inspect()        ← 滑动窗口检测重复（大小6，阈值3）
           ├── ReasonBreaker 实时检测         ← 推理循环检测（200字符窗口）
           ├── HITL 审批（可选）               ← 人工审批非只读工具
           ├── dispatch() 并行执行            ← CompletableFuture 并行分发
           ├── ctx.addAssistant()            ← 助理消息
           └── ctx.addToolResult()           ← 工具结果
                 │
                 └── 回到步骤 2（继续循环）
```

### SSE 流式解析（双路径优化）

```
HttpModelClient.chatStream()
   │
   ├── 数据行 → "data: {...}" → 提取 JSON
   │
   ├── 快路径（~99%）：extractJsonStringField() 字符串扫描
   │   ├── content 块 → 直接提取 "content":"..." 值
   │   └── reasoning 块 → 直接提取 "reasoning":"..." 值
   │   └── 完全跳过全量 JSON 解析（~50 倍性能提升）
   │
   └── 慢路径（~1%）：ONode.ofJson() 完整解析
       ├── tool_calls 出现时 → 解析参数对象
       └── usage 出现时 → 统计 token 用量
```

### 上下文折叠机制

```
ContextFolding.fold()
   │
   ├── 1. 估算总字符数，判断是否超过 maxContextChars（200KB）
   │
   ├── 2. 未超阈值 → 返回原始列表（零拷贝，P0.3 优化）
   │
   ├── 3. 超出阈值：
   │      ├── 从尾部往前累积，保留 ~keepTailChars（80KB）尾部
   │      └── 确保不切在 tool_calls/tool 对中间
   │
   ├── 4. 头部调用一次 LLM 压缩为摘要
   │      └── 摘要器清理 tool_calls/tool 对简化上下文
   │
   └── 5. 返回：摘要消息 + 保留的尾部消息
```

### 会话持久化

```
JsonlSessionStore
   │
   ├── 文件格式：每行一个 JSON 消息对象
   ├── 文件位置：~/.agent4j/workspace/{md5(workspacePath)[:12]}/sessions/{name}.jsonl
   ├── 写入策略：异步批量写入
   │   ├── LinkedBlockingQueue（容量 10K）缓冲
   │   ├── 后台消费者线程逐批取出（batchSize=50）
   │   ├── 定时 flush（flushIntervalSec=30s）兜底
   │   └── 延迟创建：空白会话不落盘
   ├── 并发安全：ReentrantLock 保护读写
   └── 工作区隔离：基于 MD5 哈希目录

工作区结构：
~/.agent4j/workspace/{hash}/
├── workspace.json          (工作区配置)
└── sessions/
    ├── current.jsonl       (当前会话)
    └── session-{N}.jsonl   (历史会话)
```

### 安全设计

| 机制 | 说明 |
|------|------|
| **风暴断路器（StormBreaker）** | 滑动窗口检测重复工具调用（可配窗口6次，阈值3次），自动抑制并提示 |
| **推理断路器（ReasonBreaker）** | 滑动窗口检测 reasoning 文本循环（200字符窗口，重复3次触发） |
| **路径穿越防护** | 所有文件操作通过 ToolContext 路径校验，禁止越界访问 |
| **原子编辑回滚** | multi_edit 全验证→全写入→失败回滚，保证数据一致性 |
| **HITL 审批** | 非只读工具执行前可要求用户审批；沙箱越界工具强制审批 |
| **计划模式** | 只读模式限制：仅允许只读工具 |
| **消息修复器（MessageHealer）** | 发送前自动修复 tool_calls/tool 配对、重复 ID、孤立消息 |
| **命令白名单** | 终端工具支持白名单过滤 |
| **forceDenyTools** | 子代理场景强制排除递归 spawn 工具 |

---

## 5. 全部工具列表

### 文件操作（来自 Skill 系统）

| 工具名 | 只读 | 说明 |
|--------|:----:|------|
| `read` | ✅ | 读取文件（支持行范围/头/尾） |
| `write` | ❌ | 创建新文件或覆盖现有文件 |
| `edit` | ❌ | SEARCH/REPLACE 精准文本替换（search 必须唯一） |
| `multi_edit`（别名） | ❌ | 批量原子编辑（全验证→全写入→失败回滚） |
| `glob` | ✅ | 按 glob 通配符模式搜索文件 |
| `grep` | ✅ | 递归搜索文件内容（返回路径:行号:内容） |
| `ls` | ✅ | 列出目录内容（支持递归 Tree 展示） |
| `get_file_info`（别名） | ✅ | 文件/目录元信息 |
| `copy_file`（别名） | ❌ | 复制文件或目录 |

### 终端

| 工具名 | 只读 | 说明 |
|--------|:----:|------|
| `bash` | ❌ | 执行非交互式 Shell 指令（多行脚本） |
| `bash_start` | ❌ | 启动 shell 命令会话（超时不终止，返回 session_id） |
| `bash_wait` | ❌ | 继续等待仍在运行的命令会话 |
| `bash_stdin` | ❌ | 向运行中的会话写入 stdin |
| `bash_stop` | ❌ | 终止运行中的命令会话及其子进程 |
| `run_background` | ❌ | 启动后台进程并分离 |
| `job_output` | ✅ | 读取后台作业最新输出（支持增量） |
| `wait_for_job` | ❌ | 等待作业完成 |
| `stop_job` | ❌ | 停止作业（SIGTERM → SIGKILL） |
| `list_jobs` | ✅ | 列出所有活跃后台作业 |

### 网络

| 工具名 | 只读 | 说明 |
|--------|:----:|------|
| `webfetch` | ✅ | 从 URL 获取网页内容（markdown/text/html） |
| `codesearch` | ✅ | Exa Code API 搜索编程相关上下文 |

### 记忆

| 工具名 | 只读 | 说明 |
|--------|:----:|------|
| `remember` | ❌ | 持久化保存到 ~/.agent4j/memory/（scope: global/project） |
| `recall_memory` | ✅ | 读取记忆内容 |
| `forget` | ❌ | 删除记忆 |

### 计划与交互

| 工具名 | 只读 | 说明 |
|--------|:----:|------|
| `submit_plan` | ❌ | 提交计划供用户审查 |
| `revise_plan` | ❌ | 修订进行中的计划 |
| `mark_step_complete` | ❌ | 标记计划步骤为已完成 |
| `ask_choice` | ❌ | 用户选择菜单（2-6 个选项） |
| `todo_write` | ❌ | 任务跟踪列表 |

### 子代理

| 工具名 | 只读 | 说明 |
|--------|:----:|------|
| `task` | ❌ | 创建隔离子代理处理复杂多步任务（单任务） |
| `multi_task` | ❌ | 并行创建多个隔离子代理（各自独立执行） |

### 工作区共享

| 工具名 | 只读 | 说明 |
|--------|:----:|------|
| `workspace_list` | ✅ | 列出共享工作区中的条目键 |
| `workspace_read` | ✅ | 读取 KV 或文档条目（优先 KV，其次文档） |
| `workspace_write` | ❌ | 写入 KV 或文档条目 |
| `workspace_watch` | ❌ | 阻塞等待工作区键变更事件（通配符匹配） |

### 系统

| 工具名 | 只读 | 说明 |
|--------|:----:|------|
| `get_current_time` | ✅ | 获取系统当前日期、精确时间、时区 |

### 技能系统

| 工具名 | 只读 | 说明 |
|--------|:----:|------|
| `skilllist` | ✅ | 列出所有挂载池中的可用技能 |
| `skillread` | ✅ | 读取技能详细说明书 |
| `skillrefresh` | ❌ | 重新扫描所有挂载池，更新技能列表 |
| `using-superpowers` | ❌ | 使用超级能力技能组合完成复杂任务 |

### MCP / OpenAPI / 插件（动态注册）

| 来源 | 说明 |
|------|------|
| **MCP 服务器** | 通过 Model Context Protocol 注册的外部工具 |
| **OpenAPI 规范** | 通过 OpenAPI 规范自动导入为 API 工具 |
| **插件系统** | `~/.agent4j/plugin/` 目录下的插件自动发现为工具 |
| **代码搜索** | `codesearch` — Exa Code API 编程上下文搜索 |
| **API 查询** | `search_apis` / `get_api_detail` / `call_api` |
| **文档查询** | `resolve-library-id` / `query-docs` — Context7 文档查询 |

### 工具总数

稳定内置工具约 **30+** 个，加上通过 MCP/OpenAPI/Plugin/Skill 动态注册的工具，总数可按需扩展。

---

## 6. 配置参考

### 配置文件位置

`~/.agent4j/config.json`（首次启动自动生成）

### 完整配置项

```json
{
  /* ====== LLM 连接 ====== */
  "baseUrl": "http://localhost:11434/v1",
  "apiKey": "sk-your-api-key",
  "model": "deepseek-v4-flash",

  /* ====== 工作区 ====== */
  "workspaceDir": "",
  "editMode": "auto",
  "reasoningEffort": "high",
  "lang": "ZH",
  "hitl": false,

  /* ====== 安全 ====== */
  "disabledTools": [],
  "blockedPaths": [],

  /* ====== 模型定价 ====== */
  "price": {
    "deepseek-v4-flash":   { "input": "1", "cache": "0.025", "output": "2" },
    "deepseek-v4-pro":     { "input": "3", "cache": "0.02",  "output": "6" },
    "mimo-v2.5":           { "input": "1", "cache": "0.02",  "output": "2" },
    "mimo-v2.5-pro":       { "input": "3", "cache": "0.025", "output": "6" }
  },
  "availableModels": ["deepseek-v4-flash", "deepseek-v4-pro", "mimo-v2.5", "mimo-v2.5-pro"],

  /* ====== 可调优参数（P1.1 外部化，原硬编码常量） ====== */
  "maxContextChars": 200000,
  "keepTailChars": 80000,
  "toolTimeoutSec": 360,
  "maxSelfCorrectionAttempts": 5,
  "maxStreamErrorRetries": 10,
  "flushIntervalSec": 30,
  "foldHeadCharsLimit": 60000,
  "stormWindowSize": 6,
  "stormThreshold": 3,
  "toolResultTruncateChars": 16000,
  "toolResultKeepChars": 12000
}
```

### 参数说明

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `baseUrl` | `http://localhost:11434/v1` | OpenAI 兼容 API 基础地址 |
| `apiKey` | - | API 密钥 |
| `model` | `deepseek-v4-flash` | 模型名称 |
| `workspaceDir` | `""` | 工作区目录（默认 CWD） |
| `editMode` | `auto` | 编辑模式：auto（需确认）/ yolo（直接执行） |
| `reasoningEffort` | `high` | 推理力度：low/medium/high/max |
| `lang` | `EN` | 界面语言：ZH / EN |
| `hitl` | `false` | HITL 默认状态 |
| `disabledTools` | `[]` | 禁用工具列表 |
| `blockedPaths` | `[]` | 屏蔽目录列表（文件操作跳过） |
| `maxContextChars` | `200000` | 上下文总字符阈值（触发折叠） |
| `keepTailChars` | `80000` | 折叠时保留尾部预算字符 |
| `toolTimeoutSec` | `360` | 工具执行超时（秒） |
| `maxSelfCorrectionAttempts` | `5` | 每回合最大自愈尝试次数 |
| `maxStreamErrorRetries` | `10` | 流式错误最大重试次数 |
| `flushIntervalSec` | `30` | 定时刷入间隔（秒） |
| `foldHeadCharsLimit` | `60000` | 折叠头部字符限制 |
| `stormWindowSize` | `6` | 风暴断路器滑动窗口大小 |
| `stormThreshold` | `3` | 风暴断路器触发阈值 |
| `toolResultTruncateChars` | `16000` | 工具结果截断字符数 |
| `toolResultKeepChars` | `12000` | 工具结果截断后保留字符数 |

### 环境变量覆盖

| 环境变量 | 覆盖配置 | 说明 |
|----------|----------|------|
| `OPENAI_BASE_URL` | `baseUrl` | API 基础地址 |
| `OPENAI_API_KEY` | `apiKey` | API 密钥 |
| `MODEL` | `model` | 模型名称 |
| `AGENT4J_DISABLED_TOOLS` | `disabledTools` | 禁用工具（逗号分隔） |
| `AGENT4J_BLOCKED_PATHS` | `blockedPaths` | 屏蔽路径（逗号分隔） |
| `AGENT4J_MAX_CONTEXT_TOKENS` | - | 最大上下文 token 数 |

### CLI 命令

| 命令 | 功能 |
|------|------|
| `/new` | 开启新会话 |
| `/plan` | 进入计划模式（仅只读工具） |
| `/execute` | 退出计划模式 |
| `/compact` | 折叠历史消息（语义摘要） |
| `/retry` | 撤回最后一条消息并重试 |
| `/rewind N` | 回退到第 N 轮对话 |
| `/sessions` | 列出历史会话 |
| `/load N` | 加载指定会话 |
| `/init` | 自动分析项目生成 agent4j.md |
| `/hitl` | 切换 HITL 模式（人工审批） |
| `/agree` | 批准待执行的 HITL 工具调用 |
| `/deny` | 拒绝待执行的 HITL 工具调用 |
| `/help` | 显示帮助信息 |
| `/exit` | 退出程序 |

---

## 7. 关键类参考

| 类 | 行数 | 位置 | 职责 |
|----|------|------|------|
| **AgentLoop** | ~1300 | agent4j-bin/agent | 核心推理循环编排，LLM ↔ 工具交互 |
| **Agent4jAgent** | ~554 | agent4j-bin/agent | Agent 工厂+外观，组装组件 |
| **HttpModelClient** | ~558 | agent4j-bin/model | HTTP SSE 流式客户端，双路径解析 |
| **AgentService** | ~1092 | agent4j-web/service | Web 服务层，SSE 输出管理 |
| **ChatMessage** | ~290 | agent4j-bin/agent | 消息模型，多模态支持，copy() 方法 |
| **MessageHealer** | ~180 | agent4j-bin/agent | 消息修复器，操作 ChatMessage 对象 |
| **StormBreaker** | ~170 | agent4j-bin/agent | 风暴断路器，滑动窗口检测重复 |
| **ReasonBreaker** | ~120 | agent4j-bin/agent | 推理断路器，检测 reasoning 循环 |
| **ContextFolding** | ~430 | agent4j-bin/agent | 语义上下文折叠，LLM 摘要压缩 |
| **JsonlSessionStore** | ~530 | agent4j-bin/session | JSONL 持久化，异步批量写入 |
| **ConversationContext** | ~300 | agent4j-bin/agent | 会话上下文管理，消息历史 |
| **ToolRegistry** | ~350 | agent4j-bin/tool | 工具注册/查询/刷新/禁用/屏蔽 |
| **ToolDispatcher** | ~200 | agent4j-bin/tool | 工具调度器，plan/storm/HITL/Hooks |
| **ToolSchemaFlattener** | ~100 | agent4j-bin/tool | Schema 展平（深层嵌套参数） |
| **Scavenger** | ~80 | agent4j-bin/agent | 从 reasoning 回收丢失的 tool_calls |
| **Agent4jConfig** | ~490 | agent4j-bin/config | 配置单例，所有参数读取+热更新 |
| **SubAgent** | ~200 | agent4j-bin/agent | 子代理实现，隔离执行上下文 |
| **SharedWorkspace** | ~150 | agent4j-bin/workspace | 共享工作区核心存储（KV+文档） |
| **WorkspaceEventBus** | ~120 | agent4j-bin/workspace | 工作区事件总线，变更通知 |

---

## 8. 最近优化（P0.1–P1.1）

### P0.1 — 空 catch 块日志化（2026-01）

- **问题**：AgentLoop 中 36 处空的 catch 块（catch Exception silently）
- **修改**：替换为 `log.warn("...", e)` / `log.debug("...", e)`，SLF4J 门面
- **影响**：降低调试成本，异常不再被静默吞没

### P0.2 — 异步会话写入（2026-01）

- **问题**：JsonlSessionStore 每次 append 同步写盘，延迟高
- **方案**：LinkedBlockingQueue 缓冲（容量 10K）+ 后台消费者线程
  - 批量写入: batchSize=50
  - 定时刷入兜底: flushIntervalSec=30s
  - 延迟创建: 空白会话不落盘
- **影响**：高频写入延迟显著降低

### P0.3 — MessageHealer 直接操作 ChatMessage（2026-01）

- **问题**：MessageHealer 需 ChatMessage↔Map 序列化往返，性能损失
- **修改**：
  - MessageHealer 直接操作 `List<ChatMessage>`
  - 新增 `ChatMessage.copy()` 方法
  - `ContextFolding.fold()` 未超阈值时返回原始列表
- **影响**：消除序列化开销，减少 GC 压力

### P0.4 — SSE JSON 快慢双路径（2026-01）

- **问题**：每个 SSE 数据块都用 ONode.ofJson() 完整解析，CPU 开销大
- **方案**：
  - **快路径**（~99%）：`extractJsonStringField()` 字符串扫描提取 content/reasoning
  - **慢路径**（~1%）：`ONode.ofJson()` 完整解析，仅在 tool_calls/usage 时使用
- **影响**：SSE 解析性能提升约 50 倍

### P1.1 — 配置外部化（2026-01）

- **问题**：12+ 硬编码常散落在 AgentLoop/StormBreaker/JsonlSessionStore 等类中
- **修改**：
  - 所有常量迁移到 `~/.agent4j/config.json`
  - `Agent4jConfig.getInstance()` 单例统一读取
  - StormBreaker 构造函数接受 `windowSize` / `threshold` 参数
  - 新增 `updateAndSave()` 热更新支持
  - 自动合并默认配置（适配旧版本 config.json）
- **外部化参数**：maxContextChars, keepTailChars, toolTimeoutSec, maxSelfCorrectionAttempts, maxStreamErrorRetries, flushIntervalSec, foldHeadCharsLimit, stormWindowSize, stormThreshold, toolResultTruncateChars, toolResultKeepChars

---

## 9. 测试覆盖

### 测试概况

| 模块 | 测试类数 | 测试用例数 | 状态 |
|------|---------|-----------|------|
| **agent4j-tool** | 6 | ~77 | ✅ 基础工具测试 |
| **agent4j-bin** | 12 | ~103 | ✅ 核心测试 |
| ├── agent/ | 7 | ~45 | AgentLoop/MessageHealer/StormBreaker/ContextFolding/Scavenger/ReasonBreaker/PromptPrefix |
| ├── session/ | 1 | ~13 | JsonlSessionStore |
| ├── tool/ | 2 | ~17 | ToolDispatcher + ToolRegistry |
| └── workspace/ | 2 | ~17 | SharedWorkspace + WorkspaceEventBus |
| **agent4j-web** | 0 | 0 | ❌ 完全无测试 |
| **agent4j-front** | 0 | 0 | ❌ 完全无测试 |

### 未覆盖区域

以下包和模块 **零测试覆盖**：
- `command/` — 所有聊天命令（/new, /plan, /compact 等）
- `builtin/` — TaskTool, MultiTaskTool, 所有 Workspace 工具
- `config/` — Agent4jConfig, ConfigService
- `mcp/` — MCP 管理服务
- `model/` — HttpModelClient（需 mock SSE 端点）
- `interact/`, `job/`, `memory/`, `plan/` 工具
- `solon/` 集成层 — SolonToTools, ToolManager, 所有 Skill 提供者
- Web 控制器层 — 9 个 REST 控制器
- Vue 组件 — 所有 19 个前端组件

### 主要测试文件

| 测试类 | 位置 | 覆盖内容 |
|--------|------|----------|
| `ContextFoldingTest` | bin/agent | 折叠阈值、摘要生成、零拷贝返回 |
| `ConversationContextTest` | bin/agent | 消息增删查、上下文构建 |
| `MessageHealerTest` | bin/agent | 消息配对、重复 ID 清理、截断 |
| `PromptPrefixTest` | bin/agent | 前缀缓存、工具排序稳定性 |
| `ReasonBreakerTest` | bin/agent | 推理循环检测阈值 |
| `ScavengerTest` | bin/agent | reasoning 中回收 tool_calls |
| `StormBreakerTest` | bin/agent | 滑动窗口、重复检测、阈值 |
| `JsonlSessionStoreTest` | bin/session | 持久化、加载、重放 |
| `ToolDispatcherTest` | bin/tool | plan mode、风暴拦截、前置/后置钩子 |
| `ToolRegistryTest` | bin/tool | 注册、禁用、刷新、forceDeny |
| `SharedWorkspaceTest` | bin/workspace | KV 存储、文档存储、键检索 |
| `WorkspaceEventBusTest` | bin/workspace | 事件发布、订阅、通配符匹配 |

---

## 10. 设计亮点

- **缓存优先** — `PromptPrefix` 不可变前缀 + 稳定的 tool specs（工具按名称排序），最大化 DeepSeek / Ollama 前缀缓存命中率
- **SSE 双路径解析** — 快路径用字符串扫描直接提取 content/reasoning（~99% 场景），慢路径用 ONode 完整解析仅用于 tool_calls/usage，性能提升 ~50x
- **并发安全** — `CountDownLatch` 替代忙等待、`AtomicBoolean` 保证 happens-before、`ReentrantLock` 保护文件写入
- **异步批量写入** — JsonlSessionStore 用 `LinkedBlockingQueue` + 后台消费者线程批量刷盘（batch=50, buffer=10K），定时 flush 兜底
- **延迟文件创建** — 空白会话不落盘，首次写入消息才创建 `.jsonl` 文件
- **消息直接操作** — MessageHealer 直接操作 `List<ChatMessage>`，消除序列化往返；`ContextFolding.fold()` 未超阈值返回原始列表（零拷贝）
- **配置外部化** — 所有可调优参数从硬编码常量集中到 `~/.agent4j/config.json`，支持运行时热更新
- **原子编辑** — multi_edit 全验证→全写入→失败回滚，确保数据一致性
- **安全防护** — 路径校验 + 风暴断路器 + 推理断路器 + HITL + plan mode + forceDenyTools 多层防御
- **子代理隔离** — 独立循环上下文，排除递归工具和用户交互工具，AgentOutput 通过 ThreadLocal 传播
- **工作区共享** — SharedWorkspace 支持 KV/文档存储，事件总线通知变更，子代理间数据协作
- **插件系统** — `~/.agent4j/plugin/` 目录自动扫描，`tool.json` 配置动态注册为 AgentTool
- **Skill 系统** — 基于 Solon AI Skill 的 Markdown+YAML 技能文件，inline/subagent 两种运行模式
- **JSON 修复** — StormBreaker 支持自动补全被截断的 JSON（补引号、花括号、方括号）
- **消息整流** — MessageHealer 单次遍历完成四项修复（截断/配对/去重/排序），确保 API 请求合规
- **Schema 展平** — ToolSchemaFlattener 展平深层嵌套参数为扁平 JSON Schema，兼容 OpenAI function-calling 格式

---

## 11. 运行方式

### 环境要求

- **JDK 17+**
- **Maven 3.x**
- **Node.js 18+ / pnpm 8+**（前端 / Tauri 需要）
- **Rust 工具链**（Tauri 桌面端需要）
- 一个兼容 OpenAI API 的 LLM 端点（如 DeepSeek / Ollama / OpenAI）

### 控制台 CLI 模式

```bash
mvn compile -pl agent4j-bin -am
mvn exec:java -pl agent4j-bin \
  -Dexec.mainClass="site.sorghum.agent4j.bin.app.AppConfig"
```

首次启动自动生成 `~/.agent4j/config.json`，编辑填入 API 信息后重启。

### Web API 模式

```bash
mvn compile -pl agent4j-web -am -DskipTests
java -jar agent4j-web/target/agent4j-web.jar
# 服务在 http://localhost:8097 启动
```

### 前端开发模式

```bash
cd agent4j-front
pnpm install
pnpm dev          # → http://localhost:3000
pnpm build        # → dist/
```

### Tauri 桌面模式

```bash
cd agent4j-tauri
pnpm install
pnpm tauri dev    # 开发调试
pnpm tauri build  # 构建分发包
```

---

## 12. 开发指引

### 运行测试

```bash
# 全部测试
mvn test

# 特定模块
mvn test -pl agent4j-tool
mvn test -pl agent4j-bin
```

### 添加新工具

1. 在 `agent4j-tool` 中继承 `AgentTool` 抽象类
2. 实现 `getName()` / `getDescription()` / `getParameters()` / `execute()` 方法
3. 标注 `@Component` 注解，Solon IoC 自动发现注册
4. 可选择实现 `toToolSpec()` 提供 Skill 格式的文档描述

### 添加新命令

1. 实现 `ChatCommand` 接口
2. 标注 `@Component` 注解
3. 在 `ChatCommandRegistry` 中自动注册

### 添加新 Skill

1. 在 `~/.agent4j/plugin/` 或 `~/.claude/skills/` 下创建目录
2. 添加 `tool.json`（可选多工具映射）和 `skill.md`（技能正文）
3. 插件系统自动扫描发现为工具

---

*本文档由 Agent4j AI Agent 知识库维护 · 项目作者 [Sorghum](https://gitee.com/ezdemo)*
