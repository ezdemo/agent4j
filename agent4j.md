# Agent4j — Java AI Agent 框架

<p align="center">
  <strong>Java 17 实现的 AI 编码代理框架</strong><br>
  推理循环 · 工具调用 · 会话管理 · 流式输出 · 子代理 · 多界面
</p>

---

## 项目概述

Agent4j 是一个纯 Java 实现的 AI 编码代理框架，将 LLM（大语言模型）与可扩展的工具系统深度整合，形成能自主工作的编程助手。灵感来源于 Claude Code、Devin 等 AI 编码代理，但在 Java 生态中完整实现，并提供了 **CLI 控制台**、**Vue3 Web 界面** 和 **Tauri 桌面应用** 三种交互方式。

**核心价值**：在终端/浏览器/桌面中跑一个 AI 程序员，自主完成代码分析、文件编辑、命令执行等开发任务。

### 核心能力

| 能力 | 说明 |
|------|------|
| 🤖 **推理循环** | 多轮对话上下文中自主思考 → 调用工具 → 观察结果 → 继续推理 |
| 🔧 **工具系统** | 27+ 内置工具（文件/搜索/终端/网络/记忆/作业/计划），支持 Skill 扩展 |
| 🔌 **模型无关** | OpenAI 兼容 API，可对接 DeepSeek、Ollama、OpenAI、Claude、Gemini 等 |
| 📋 **计划模式** | 只读探索 → 提交计划 → 审批后执行，安全可控 |
| 🛡️ **安全机制** | 风暴断路器（防循环调用）、路径穿越防护、原子编辑回滚、HITL 人工审批 |
| 💾 **会话管理** | JSONL 持久化、缓冲写入、定时刷入、语义折叠、Token 用量追踪 |
| 📊 **Token 追踪** | 用量统计、前缀缓存命中率、按会话/模型累计、费用估算 |
| 🧩 **子代理** | 复杂任务委派，隔离执行上下文，支持 AgentOutput 传播 |
| 🔍 **代码分析** | 源码定位、符号大纲、标识符查找、正则/glob 搜索 |
| 🌐 **联网能力** | 网络搜索（DuckDuckGo）、网页抓取 |
| 📝 **记忆服务** | 跨会话持久化键值记忆 |
| 🔌 **MCP 协议** | 支持 Model Context Protocol 工具注册 |
| 📄 **OpenAPI** | 支持 OpenAPI 规范自动导入为工具 |
| 🎨 **多界面** | 控制台 CLI / Vue3 Web UI / Tauri 桌面应用 |

---

## 目录结构

```
agent4j/
├── pom.xml                           # 父 POM（多模块 Maven 项目）
├── README.md                         # 项目说明文档
├── agent4j.md                        # 本文件（项目知识库）
├── SKILL_README.md                   # Skill 系统说明
├── ANALYSIS.md                       # 架构分析文档
├── TEST_SUMMARY.md                   # 测试总结
├── UPGRADE_CHECKLIST.md              # 升级检查清单
├── icon.png / intro/index.html        # 项目图标 / 介绍页
├── logs/                             # 运行时日志
│   ├── agent4j.log
│   └── agent4j_2026-*.log
│
├── agent4j-tool/                     # [Maven 模块] 核心工具库
│   ├── pom.xml
│   └── src/main/java/site/sorghum/agent4j/tool/
│       ├── AgentTool.java             # 工具抽象基类
│       ├── ToolContext.java           # 工具执行上下文
│       ├── ToolParameter.java         # 工具参数定义
│       ├── ToolResult.java            # 工具执行结果
│       ├── HitlRequiredException.java # HITL 审批异常
│       │
│       ├── interact/                  # 交互工具
│       │   ├── AskChoiceTool.java     # 用户选择菜单
│       │   ├── TodoWriteTool.java     # 任务跟踪列表
│       │   └── InteractionService.java
│       │
│       ├── job/                       # 后台作业工具
│       │   ├── JobRegistry.java       # 作业注册表
│       │   ├── JobService.java        # 作业服务
│       │   ├── RunBackgroundTool.java # 启动后台进程
│       │   ├── JobOutputTool.java     # 读取作业输出
│       │   ├── WaitForJobTool.java    # 等待作业完成
│       │   ├── StopJobTool.java       # 停止作业
│       │   └── ListJobsTool.java      # 列出所有作业
│       │
│       ├── memory/                    # 记忆工具
│       │   ├── MemoryService.java
│       │   ├── RememberTool.java      # 保存记忆
│       │   ├── RecallMemoryTool.java  # 读取记忆
│       │   └── ForgetTool.java        # 删除记忆
│       │
│       ├── plan/                      # 计划工具
│       │   ├── PlanService.java
│       │   ├── SubmitPlanTool.java    # 提交计划
│       │   ├── RevisePlanTool.java    # 修订计划
│       │   └── MarkStepCompleteTool.java # 标记步骤完成
│       │
│       ├── terminal/                  # 终端工具
│       │   ├── CommandAllowlist.java  # 命令白名单
│       │   ├── CommandChainParser.java# 命令链解析器
│       │   ├── CommandTokenizer.java  # 命令分词器
│       │   ├── ProcessTreeKiller.java # 进程树终止
│       │   └── SmartDecoder.java      # 智能解码器
│       │
│       └── solon/                     # Solon 集成
│           ├── SolonToTools.java      # Solon 工具转换
│           ├── ToolManager.java       # 工具管理器
│           ├── common/Agent4JSkillProvider.java # Skill 提供者
│           ├── mcp/Agent4JMcpSkill.java        # MCP 技能
│           ├── openapi/Agent4JOpenApiSkill.java # OpenAPI 技能
│           ├── sys/Agent4JSysSkill.java         # 系统技能
│           └── webfetch/Agent4JWebSkill.java    # 网页抓取技能
│
├── agent4j-bin/                      # [Maven 模块] 可执行入口
│   ├── pom.xml
│   └── src/main/java/site/sorghum/agent4j/bin/
│       ├── agent/                    # 代理核心
│       │   ├── Agent4jAgent.java     # Agent 工厂+外观
│       │   ├── AgentLoop.java        # 推理循环（核心编排）
│       │   ├── AgentLoopListener.java # 循环事件监听
│       │   ├── AgentOutput.java      # 输出接口
│       │   ├── ConsoleAgentOutput.java # 控制台输出
│       │   ├── SubAgent.java         # 子代理实现
│       │   ├── SubAgentAgentOutput.java # 子代理输出
│       │   ├── ChatMessage.java      # 消息模型
│       │   ├── ToolCallEntry.java    # 工具调用条目
│       │   ├── ConversationContext.java # 会话上下文
│       │   ├── PromptPrefix.java     # 前缀缓存
│       │   ├── ContextFolding.java   # 上下文折叠
│       │   ├── MessageHealer.java    # 消息修复器
│       │   ├── StormBreaker.java     # 风暴断路器
│       │   ├── ReasonBreaker.java    # 推理断路器
│       │   ├── Scavenger.java        # 工具调用回收
│       │   ├── ChoiceOption.java     # 选择选项
│       │   ├── HitlState.java        # HITL 状态枚举
│       │   └── LogLevel.java         # 日志级别
│       │
│       ├── app/                      # 应用入口
│       │   └── AppConfig.java        # main 入口
│       │
│       ├── builtin/                  # 内置组件
│       │   ├── TaskTool.java         # 子代理工具
│       │   └── SubAgentListener.java # 子代理监听器
│       │
│       ├── command/                  # 聊天命令
│       │   ├── ChatCommand.java      # 命令接口
│       │   ├── ChatCommandContext.java
│       │   ├── ChatCommandRegistry.java
│       │   └── impl/                 # 命令实现
│       │       ├── AgreeCommand.java    # /agree
│       │       ├── CompactCommand.java  # /compact
│       │       ├── DenyCommand.java     # /deny
│       │       ├── ExecuteCommand.java  # /execute
│       │       ├── HelpCommand.java     # /help
│       │       ├── HitlCommand.java     # /hitl
│       │       ├── InitCommand.java     # /init
│       │       ├── LoadCommand.java     # /load
│       │       ├── NewSessionCommand.java # /new
│       │       ├── PlanCommand.java     # /plan
│       │       ├── RetryCommand.java    # /retry
│       │       ├── RewindCommand.java   # /rewind
│       │       └── SessionsCommand.java # /sessions
│       │
│       ├── config/                   # 配置模块
│       │   ├── Agent4jConfig.java    # 配置加载/保存
│       │   └── ConfigService.java    # 配置服务
│       │
│       ├── mcp/                      # MCP 管理
│       │   ├── McpManageService.java
│       │   ├── McpServerDTO.java
│       │   ├── McpToolInfoDTO.java
│       │   └── McpToolListDTO.java
│       │
│       ├── model/                    # LLM 客户端
│       │   ├── ModelClient.java      # 模型客户端接口
│       │   └── HttpModelClient.java  # HTTP SSE 实现
│       │
│       ├── session/                  # 会话管理
│       │   ├── SessionStore.java     # 会话存储接口
│       │   ├── JsonlSessionStore.java # JSONL 存储实现
│       │   └── SessionService.java   # 会话服务
│       │
│       ├── tool/                     # 工具系统
│       │   ├── ToolDef.java          # 工具定义
│       │   ├── ToolDefHelper.java    # 工具辅助
│       │   ├── ToolDispatcher.java   # 工具调度器
│       │   ├── ToolRegistry.java     # 工具注册表
│       │   ├── ToolScanUtil.java     # 工具扫描工具
│       │   ├── ToolSchemaFlattener.java # Schema 展平
│       │   └── ToolSystemInitializer.java # 系统初始化
│       │
│       ├── util/                     # 工具类
│       │   └── ONodeUtil.java        # ONode 工具
│       │
│       └── workspace/               # 工作区管理
│           └── WorkspaceManager.java # 多工作区支持
│
├── agent4j-web/                      # [Maven 模块] Web API
│   ├── pom.xml
│   └── src/main/java/site/sorghum/agent4j/web/
│       ├── Agent4jWebApp.java       # Web 入口
│       ├── common/                  # 公共组件
│       │   ├── GlobalExceptionFilter.java
│       │   └── ServiceException.java
│       ├── controller/              # REST 控制器
│       │   ├── AgentController.java    # /api/agent/*
│       │   ├── ChatController.java     # /api/chat/*
│       │   ├── ConfigController.java   # /api/config/*
│       │   ├── GitController.java      # /api/git/*
│       │   ├── McpController.java      # /api/mcp/*
│       │   ├── OpenApiController.java  # /api/openapi/*
│       │   ├── SessionController.java  # /api/sessions/*
│       │   ├── SystemController.java   # /api/system/*
│       │   └── ToolController.java     # /api/tools/*
│       ├── doc/                     # API 文档
│       │   └── DocConfig.java
│       ├── model/                   # DTO 模型
│       │   ├── ApiResponse.java
│       │   ├── ChatRequest.java
│       │   ├── ChatResultDTO.java
│       │   ├── AgentStatusDTO.java
│       │   ├── SessionInfoDTO.java
│       │   ├── ToolInfoDTO.java
│       │   ├── ConfigDTO.java
│       │   ├── UsageDTO.java
│       │   └── ... (共 30+ DTO)
│       └── service/                 # 服务层
│           ├── AgentService.java     # Agent 服务
│           ├── OpenApiManageService.java
│           ├── SseAgentOutput.java   # SSE 输出
│           ├── SseEmitter.java       # SSE 发射器
│           └── WebUsageListener.java # 用量监听
│
├── agent4j-front/                    # [Vue3] 前端界面
│   ├── index.html
│   ├── package.json                  # pnpm / Vite
│   ├── vite.config.js
│   └── src/
│       ├── App.vue                   # 根组件（侧栏+主区+设置）
│       ├── main.js                   # Vue 入口
│       ├── assets/styles/main.css    # 全局样式
│       ├── components/               # 组件
│       │   ├── BlockRenderer.vue     # 消息块渲染
│       │   ├── ChatInput.vue         # 聊天输入框
│       │   ├── ConfirmDialog.vue     # 确认对话框
│       │   ├── GitPanel.vue          # Git 面板
│       │   ├── SetupScreen.vue       # 设置屏幕
│       │   ├── Sidebar.vue           # 侧边栏
│       │   ├── SplashScreen.vue      # 启动画面
│       │   ├── StatusBar.vue         # 状态栏
│       │   ├── TabBar.vue            # 标签栏
│       │   ├── TitleBar.vue          # 标题栏
│       │   └── UsagePanel.vue        # 用量面板
│       ├── composables/              # 组合式函数
│       │   ├── useConfirm.js
│       │   ├── useTerminal.js
│       │   └── useTheme.js
│       ├── router/index.js           # 路由配置
│       ├── services/                 # API 服务
│       │   ├── api.js                # Axios 封装
│       │   └── tauri.js              # Tauri 桥接
│       ├── stores/app.js             # Pinia 状态
│       ├── utils/helpers.js          # 工具函数
│       └── views/                    # 视图页面
│           ├── Chat.vue              # 对话页
│           ├── Home.vue              # 首页
│           ├── Sessions.vue          # 会话管理
│           ├── Settings.vue          # 设置页
│           ├── Tools.vue             # 工具列表
│           ├── Help.vue              # 帮助页
│           └── NotFound.vue          # 404
│
├── agent4j-tauri/                    # [Tauri] 桌面应用
│   ├── package.json
│   ├── INTEGRATION_COMPLETE.md
│   ├── TAURI_WEB_INTEGRATION.md
│   └── src-tauri/
│       ├── Cargo.toml                # Rust 依赖
│       ├── tauri.conf.json           # Tauri 配置
│       ├── build.rs
│       ├── capabilities/             # 权限配置
│       ├── icons/                    # 应用图标
│       ├── resources/                # 资源文件
│       └── src/
│           ├── main.rs               # Rust 入口
│           └── lib.rs                # Tauri 命令
│
└── agent4j-tui/                      # [占位] TUI 终端界面
```

---

## 技术栈

### 后端（Java）

| 组件 | 版本 | 用途 |
|------|------|------|
| **Java** | 17+ | 开发语言 |
| **Maven** | 3.x | 构建工具 |
| **Solon** | 4.0.0-M1 | IoC 容器 / AOP / Web 框架 |
| **Snack4** | 4.0.49 | JSON 解析与序列化 |
| **Snack4-jsonpath** | 4.0.49 | JSONPath 查询 |
| **Lombok** | 1.18.34 | 代码简化（@Slf4j / @Getter / @Setter） |
| **JUnit Jupiter** | 5.10.3 | 单元测试 |
| **Logback** | via Solon | 日志实现 |
| **SLF4J** | via Solon | 日志门面 |
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

## 架构设计

### 整体架构

```
┌──────────────────────────────────────────────────────────┐
│                    交互层 Interface                        │
│  ┌──────────┐  ┌──────────────┐  ┌──────────────────┐    │
│  │ Console  │  │  Vue3 Web   │  │  Tauri Desktop   │    │
│  │   CLI    │  │   (Browser) │  │   (Rust+WebVew)  │    │
│  └────┬─────┘  └──────┬───────┘  └────────┬─────────┘    │
│       │              │                    │              │
│       │     ┌────────▼────────┐           │              │
│       └─────►  REST API      ◄───────────┘              │
│             │  (Solon Web)   │                           │
│             └────────┬────────┘                           │
└──────────────────────┼───────────────────────────────────┘
                       │
┌──────────────────────▼───────────────────────────────────┐
│                   Agent4jWebApp                           │
│              IoC 容器 + Web 控制器                         │
└──────────────────────┬───────────────────────────────────┘
                       │
┌──────────────────────▼───────────────────────────────────┐
│                Agent4jAgent (工厂 + 外观)                  │
│           组装 ModelClient + ToolRegistry + Session        │
└────┬──────────────────────────┬──────────────────────────┘
     │                          │
┌────▼──────┐    ┌──────────────▼──────────────────────┐
│ AgentLoop │    │        ToolDispatcher                │
│ 推理循环   │    │   PlanMode/Storm/Hooks/Token截断     │
│ LLM ↔ Tool│    │   Schema 展平 / 会话ID注入           │
└────┬──────┘    └──────────────┬──────────────────────┘
     │                          │
┌────▼──────┐    ┌──────────────▼──────────────────────┐
│ModelClient│    │          ToolRegistry                │
│ API 客户端 │    │   注册/查询/动态刷新/OpenAI Schema   │
│ HTTP SSE  │    │   禁用/屏蔽/生命周期管理              │
└───────────┘    └─────────────────────────────────────┘
```

### 模块依赖关系

```
agent4j (父 POM)
  ├── agent4j-tool    (核心工具库，无 Solon Web 依赖)
  │   └── 依赖: solon-ai-skill-{file,sys,web,pdf}, solon-ai-harness
  │
  ├── agent4j-bin     (可执行入口，依赖 agent4j-tool)
  │   └── 依赖: solon-lib, snack4, snack4-jsonpath, logback
  │
  └── agent4j-web     (Web API，依赖 agent4j-bin)
      └── 依赖: solon-web, solon-web-cors, knife4j, maven-shade-plugin
```

### 推理循环流程（核心编排）

```
用户输入
   │
   ▼
AgentLoop.run(message)
   │
   ├── 1. ctx.addUser(msg)                ← 追加用户消息
   ├── 2. ctx.buildMessages()              ← 组装 system + history
   ├── 3. MessageHealer.heal()             ← 修复消息（截断/配对/去重）
   ├── 4. ContextFolding.fold()            ← 超长时折叠旧消息为摘要
   ├── 5. HttpModelClient.chatStream()     ← 流式调用 LLM (SSE)
   │       ├── onReasoningDelta()          ← 思考过程实时打印
   │       ├── onContentDelta()            ← 内容实时打印
   │       └── onToolCalls()              ← 工具调用收集
   │
   ├── 6. Scavenger.scavenge()             ← 从 reasoning 回收丢失的调用
   │
   ├── 7. 无 tool_calls → 返回文本回复
   │
   └── 8. 有 tool_calls
           ├── StormBreaker.inspect()      ← 检查重复调用（滑动窗口6次，阈值3次）
           ├── ReasonBreaker 实时检测       ← 推理循环检测（200字符窗口，重复3次）
           ├── HITL 审批（可选）             ← 人工审批非只读工具
           ├── dispatch() 并行执行          ← CompletableFuture 并行分发
           ├── ctx.addAssistant()          ← 助理消息
           └── ctx.addToolResult()         ← 工具结果
                 │
                 └── 回到步骤 2（继续循环）
```

### 上下文折叠机制

```
ContextFolding.fold()
   │
   ├── 1. 估算总字符数，判断是否超过阈值 (200KB)
   │
   ├── 2. 从尾部往前累积，保留 ~80KB 尾部
   │      └── 确保不切在 tool_calls/tool 对中间
   │
   ├── 3. 头部（~120KB）用一次 LLM 调用压缩为摘要
   │      └── 摘要器会清理 tool_calls/tool 对简化上下文
   │
   └── 4. 返回：摘要消息 + 保留的尾部消息
```

### 会话持久化

```
JsonlSessionStore
   │
   ├── 文件格式：每行一个 JSON 消息对象
   ├── 文件位置：~/.agent4j/workspace/{hash}/sessions/{name}.jsonl
   ├── 写入策略：BufferedWriter 保持打开 + 定时 30s 自动 Flush
   ├── 延迟创建：空白会话不落盘，首次写入消息才创建文件
   ├── 并发安全：ReentrantLock 保护写入
   ├── 工作区隔离：基于 MD5(workspacePath)[:12] 哈希目录
   └── 生命周期：SessionService 管理创建/切换/加载/用量

工作区存储结构：
~/.agent4j/workspace/{hash}/
├── workspace.json    (工作区配置)
└── sessions/         (会话目录)
    ├── session1.jsonl
    └── session2.jsonl
```

### 安全设计

| 机制 | 说明 |
|------|------|
| **风暴断路器** | 滑动窗口检测重复工具调用（窗口6次，阈值3次），自动抑制并提示 |
| **推理断路器** | 滑动窗口检测 reasoning 文本循环（200字符窗口，重复3次触发） |
| **路径穿越防护** | 所有文件操作通过 ToolContext 路径校验，禁止越界访问 |
| **原子编辑回滚** | multi_edit 全验证→全写入→失败回滚，保证数据一致性 |
| **HITL 审批** | 非只读工具执行前可要求用户审批；沙箱越界工具强制审批 |
| **计划模式** | 只读模式限制：仅 read_file/glob/grep/tree/get_file_info 可用 |
| **消息修复器** | 发送前自动修复 tool_calls/tool 配对、重复 ID、孤立消息 |
| **命令白名单** | run_command 工具支持白名单过滤 |

---

## 全部工具列表

### 文件操作（6 个）

| 工具名 | 只读 | 说明 |
|--------|:----:|------|
| `read_file` | ✅ | 读取文件（支持范围/头/尾） |
| `edit_file` | ❌ | SEARCH/REPLACE 精确编辑（search 必须唯一） |
| `write_file` | ❌ | 创建/覆盖文件 |
| `multi_edit` | ❌ | 批量原子编辑（全验证→全写入→失败回滚） |
| `copy_file` | ❌ | 复制文件或目录 |
| `get_file_info` | ✅ | 文件/目录元信息 |

### 代码分析（6 个）

| 工具名 | 只读 | 说明 |
|--------|:----:|------|
| `grep` | ✅ | 正则搜索文件内容 |
| `glob` | ✅ | 按 glob 模式匹配文件名 |
| `tree` | ✅ | 生成目录树结构 |
| `get_symbols` | ✅ | AST 符号大纲 |
| `find_in_code` | ✅ | 标识符查找 |
| `java_source` | ✅ | 通过全限定类名查找 Java 源码 |

### 终端（1 个）

| 工具名 | 只读 | 说明 |
|--------|:----:|------|
| `run_command` | ❌ | 执行 shell 命令（支持超时控制） |

### 网络（2 个）

| 工具名 | 只读 | 说明 |
|--------|:----:|------|
| `web_search` | ✅ | 互联网搜索（DuckDuckGo Lite） |
| `web_fetch` | ✅ | 网页抓取 |

### 记忆（3 个）

| 工具名 | 只读 | 说明 |
|--------|:----:|------|
| `remember` | ❌ | 保存记忆（跨会话持久化） |
| `recall_memory` | ✅ | 读取记忆 |
| `forget` | ❌ | 删除记忆 |

### 后台作业（5 个）

| 工具名 | 只读 | 说明 |
|--------|:----:|------|
| `run_background` | ❌ | 启动后台进程（分离执行） |
| `job_output` | ✅ | 读取作业最新输出（支持增量） |
| `wait_for_job` | ❌ | 等待作业完成 |
| `stop_job` | ❌ | 停止作业（SIGTERM → SIGKILL） |
| `list_jobs` | ✅ | 列出所有活跃作业 |

### 文件系统（1 个，通用）

| 工具名 | 只读 | 说明 |
|--------|:----:|------|
| `file` | ❌ | 创建/删除/移动/复制/查看文件或目录 |

### 计划与交互（5 个）

| 工具名 | 只读 | 说明 |
|--------|:----:|------|
| `submit_plan` | ❌ | 提交计划供用户审查 |
| `revise_plan` | ❌ | 修订进行中的计划 |
| `mark_step_complete` | ❌ | 标记计划步骤为已完成 |
| `ask_choice` | ❌ | 用户选择菜单（2-6 个选项） |
| `todo_write` | ❌ | 任务跟踪列表 |

### 子代理（1 个）

| 工具名 | 只读 | 说明 |
|--------|:----:|------|
| `task` | ❌ | 创建隔离子代理处理复杂多步任务 |

### MCP / OpenAPI（动态注册）

| 来源 | 说明 |
|------|------|
| **MCP 服务器** | 通过 MCP 协议动态注册的外部工具 |
| **OpenAPI 规范** | 通过 OpenAPI 规范自动导入为工具 |

---

## 运行方式

### 环境要求

- **JDK 17+**
- **Maven 3.x**
- **Node.js 18+ / pnpm 8+**（前端 / Tauri 需要）
- **Rust 工具链**（Tauri 桌面端需要）
- 一个兼容 OpenAI API 的 LLM 端点（如 DeepSeek / Ollama / OpenAI）

### 1. 控制台 CLI 模式

```bash
# 编译
mvn compile -pl agent4j-bin -am

# 运行
mvn exec:java -pl agent4j-bin \
  -Dexec.mainClass="site.sorghum.agent4j.bin.app.AppConfig"

# 或直接 java
java -cp "agent4j-bin/target/classes:agent4j-tool/target/classes" \
  site.sorghum.agent4j.bin.app.AppConfig
```

首次启动自动生成 `~/.agent4j/config.json`，编辑填入 API 信息后重启。

### 2. Web API 模式

```bash
# 编译并启动 Web 服务
mvn compile -pl agent4j-web -am
java -jar agent4j-web/target/agent4j-web.jar

# 服务在 http://localhost:8097 启动
# API 文档: http://localhost:8097/swagger-ui.html
```

### 3. 前端开发模式

```bash
cd agent4j-front
pnpm install
pnpm dev          # 开发服务器 → http://localhost:3000
pnpm build        # 构建生产包 → dist/
```

前端默认连接后端 `localhost:8097`，可在设置页修改后端地址。

### 4. Tauri 桌面模式

```bash
cd agent4j-tauri
pnpm install
pnpm tauri dev    # 开发调试
pnpm tauri build  # 构建分发包
```

### 配置说明

配置文件 `~/.agent4j/config.json`：

```json
{
  "baseUrl": "https://api.deepseek.com/v1",
  "apiKey": "sk-your-api-key",
  "model": "deepseek-v4-flash",
  "workspaceDir": ".",
  "editMode": "auto",
  "reasoningEffort": "high",
  "hitl": false,
  "lang": "ZH",
  "disabledTools": [],
  "blockedPaths": ["node_modules", ".git"],
  "price": {
    "deepseek-v4-flash": { "input": "1", "cache": "0.025", "output": "2" },
    "deepseek-v4-pro": { "input": "3", "cache": "0.02", "output": "6" }
  },
  "availableModels": ["deepseek-v4-flash", "deepseek-v4-pro"]
}
```

环境变量覆盖：`OPENAI_BASE_URL`、`OPENAI_API_KEY`、`MODEL`、`AGENT4J_DISABLED_TOOLS`、`AGENT4J_BLOCKED_PATHS`、`AGENT4J_MAX_CONTEXT_TOKENS`。

### 交互命令（CLI 模式）

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

## 设计亮点

- **缓存优先** — `PromptPrefix` 不可变前缀 + 稳定的 tool specs（工具按名称排序），最大化 DeepSeek 前缀缓存命中
- **并发安全** — `CountDownLatch` 替代忙等待、`AtomicBoolean` 保证 happens-before、`ReentrantLock` 保护文件写入
- **延迟文件创建** — 空白会话不落盘，首次写入消息才创建 `.jsonl` 文件
- **原子编辑** — `multi_edit` 全验证→全写入→失败回滚，确保数据一致性
- **安全防护** — 所有文件操作通过路径校验，防止路径穿越；沙箱越界工具强制 HITL
- **子代理隔离** — 独立循环上下文，排除递归工具和用户交互工具，专注复杂任务
- **AgentOutput 传播** — 子代理流式输出通过 ThreadLocal 传递给父代理输出通道
- **SSE 流式** — Web 模式下通过 Server-Sent Events 实时推送推理和工具调用进度
- **JSON 修复** — StormBreaker 支持自动补全被截断的 JSON（补引号、花括号、方括号）
- **消息整流** — MessageHealer 单次遍历完成四项修复，确保 API 请求合规

---

## 开发指引

### 测试

```bash
# 运行全部测试
mvn test

# 运行特定模块
mvn test -pl agent4j-tool
mvn test -pl agent4j-bin
```

### 添加新工具

1. 在 `agent4j-tool` 模块中继承 `AgentTool` 抽象类
2. 实现 `getName()`、`getDescription()`、`getParameters()`、`execute()` 方法
3. 标注 `@Component` 注解，Solon IoC 会自动发现并注册
4. 可选择实现 `toToolSpec()` 提供 Skill 格式的文档描述

### 添加新命令

1. 实现 `ChatCommand` 接口
2. 标注 `@Component` 注解
3. 在 `ChatCommandRegistry` 中自动注册

---

*本项目由 Agent4j 自动维护 · 作者 [Sorghum](https://gitee.com/ezdemo)*
