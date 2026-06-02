# Agent4j — Java AI Agent 框架

> 纯 Java 17 实现的 AI 编码代理框架，提供推理循环、工具调用、会话管理、流式输出等完整能力。
> 作者: Sorghum · 许可证: MIT · 远程仓库: https://gitee.com/ezdemo/agent4j.git

---

## 项目概述

Agent4j 是一个 Java 17 实现的 AI 编码代理框架，将 LLM（大语言模型）与可扩展的工具系统结合，形成一个自主工作的编码代理。

| 属性 | 值 |
|------|-----|
| **全称** | Agent4j — Code Agent for Java |
| **版本** | 1.0-SNAPSHOT |
| **Java 目标** | 17 |
| **构建工具** | Maven 多模块（3 个 Java 模块 + 2 个前端模块） |
| **IoC 框架** | Solon 3.9.6（轻量级替代 Spring Boot） |
| **包名** | `site.sorghum.agent4j` |
| **LLM 兼容** | OpenAI API 协议（DeepSeek / Ollama / OpenAI 均可） |

### 核心能力

- ✅ 对话式推理循环（多轮上下文保持，无固定步数限制）
- ✅ OpenAI 兼容 API 流式调用（SSE + CountDownLatch 无忙等待）
- ✅ 工具注册/调度/并行执行（CompletableFuture.allOf）
- ✅ 计划模式（Plan Mode — 只读探索 + 计划提交审批）
- ✅ 风暴断路器（StormBreaker — 滑动窗口 W=6 T=3 + JSON 指纹去重）
- ✅ 推理断路器（ReasonBreaker — 流式检测思考循环）
- ✅ 消息自愈（MessageHealer — 发送前修复 tool_calls 配对/截断/补 reasoning）
- ✅ 上下文折叠（ContextFolding — 长对话头部语义压缩 + 尾部保留）
- ✅ 工具调用回收（Scavenger — 从 reasoning_content 回收丢失的调用）
- ✅ 会话持久化（JSONL + BufferedWriter + 30s 定时刷入 + ReentrantLock）
- ✅ Token 用量追踪与缓存统计（prompt/completion/cacheHit/cacheMiss）
- ✅ 子代理隔离（SubAgent — 独立循环委派复杂任务）
- ✅ HITL 人机协同（Human-In-The-Loop — 写入操作需审批 + 沙箱越界强制审批）
- ✅ 后台作业管理（run_background / job_output / wait_for_job / stop_job）
- ✅ 记忆服务（持久化键值记忆，支持 global/project 作用域）
- ✅ Skill 系统 V2（install_skill / run_skill，支持 inline/subagent 两种模式）
- ✅ 网络搜索与网页抓取（DuckDuckGo Lite + HTML 正文提取）
- ✅ Web REST API（Solon-Web + SSE 流式输出）
- ✅ Vue 3 前端（agent4j-front — Vite + Pinia + Vue Router）
- ✅ Tauri 桌面端（agent4j-tauri — Rust 后端 + WebView 前端）

---

## 目录结构

```
agent4j/
├── pom.xml                                  # 父 POM，多模块管理（Maven 3.x）
├── agent4j.md                               # 项目文档（本文件）
├── README.md                                # 项目说明
├── .gitignore
│
├── agent4j-tool/                            # ★ 核心工具库（无外部模块依赖）
│   ├── pom.xml
│   └── src/main/java/site/sorghum/agent4j/tool/
│       ├── AgentTool.java                   # 工具抽象基类（模板方法模式）
│       ├── ToolContext.java                 # 工具执行上下文（参数/根目录/沙箱旁路）
│       ├── ToolParameter.java              # 工具参数定义（record）
│       ├── ToolResult.java                 # 工具执行结果封装
│       ├── HitlRequiredException.java      # HITL 异常（沙箱越界时抛出）
│       ├── code/                            # 代码分析
│       │   ├── CodeQueryService.java        # Java 源码查找服务
│       │   ├── FindInCodeTool.java          # AST 标识符查找
│       │   ├── GetSymbolsTool.java          # tree-sitter 符号大纲
│       │   └── JavaSourceTool.java          # 全限定类名定位源码
│       ├── file/                            # 文件系统
│       │   ├── FileSystemService.java       # 目录搜索/路径安全解析
│       │   ├── FileEdit.java               # SEARCH/REPLACE 编辑引擎
│       │   ├── FileTool.java               # 基础操作（create/move/copy/delete/stat）
│       │   ├── ReadFileTool.java           # 读取文件（head/tail/range）
│       │   ├── EditFileTool.java           # 单文件 SEARCH/REPLACE 编辑
│       │   ├── WriteFileTool.java          # 创建/覆盖文件
│       │   ├── MultiEditTool.java          # 多文件原子批量编辑
│       │   ├── CopyFileTool.java           # 复制文件/目录
│       │   └── GetFileInfoTool.java        # 文件元信息
│       ├── interact/                        # 用户交互
│       │   ├── InteractionService.java     # 选择菜单服务
│       │   ├── AskChoiceTool.java          # 箭头键选择菜单（2-6 选项）
│       │   └── TodoWriteTool.java          # 任务跟踪列表
│       ├── job/                             # 后台作业
│       │   ├── JobRegistry.java            # 作业注册表（进程管理）
│       │   ├── JobService.java             # 作业服务（启动/读取/等待/停止）
│       │   ├── RunBackgroundTool.java      # 启动后台进程
│       │   ├── JobOutputTool.java          # 读取作业输出（增量轮询）
│       │   ├── WaitForJobTool.java         # 等待作业完成
│       │   ├── StopJobTool.java            # 停止作业（SIGTERM→SIGKILL）
│       │   └── ListJobsTool.java           # 列出所有后台作业
│       ├── memory/                          # 记忆服务
│       │   ├── MemoryService.java          # 持久化键值记忆 CRUD
│       │   ├── RememberTool.java           # 保存记忆
│       │   ├── RecallMemoryTool.java       # 读取记忆
│       │   └── ForgetTool.java             # 删除记忆
│       ├── plan/                            # 计划管理
│       │   ├── PlanService.java            # 计划服务
│       │   ├── SubmitPlanTool.java         # 提交执行计划
│       │   ├── RevisePlanTool.java         # 修订计划剩余步骤
│       │   └── MarkStepCompleteTool.java   # 标记步骤完成
│       ├── search/                          # 搜索服务
│       │   ├── WorkspaceIndex.java         # 文件索引（mtime 缓存 + .gitignore）
│       │   ├── GrepTool.java               # 正则内容搜索
│       │   ├── GlobTool.java               # glob 文件名匹配
│       │   ├── TreeTool.java               # 目录树生成
│       │   ├── SearchMatch.java            # 搜索结果
│       │   └── FileMeta.java               # 文件元数据
│       ├── terminal/                        # 终端服务
│       │   ├── TerminalTool.java           # Shell 命令执行（自解析 argv）
│       │   ├── CommandAllowlist.java       # 命令白名单校验
│       │   ├── CommandChainParser.java     # 管道/重定向链解析
│       │   ├── CommandTokenizer.java       # Shell 分词器
│       │   ├── ProcessTreeKiller.java      # 进程树终止器
│       │   └── SmartDecoder.java           # 智能输出解码器
│       ├── web/                             # 网络服务
│       │   ├── WebService.java             # 搜索 + 抓取服务
│       │   ├── WebSearchTool.java          # DuckDuckGo Lite 搜索
│       │   └── WebFetchTool.java           # URL 下载 + 正文提取
│       └── test/java/                       # 单元测试（7 个测试类）
│
├── agent4j-bin/                             # ★ 可执行入口（CLI 模式）
│   ├── pom.xml
│   ├── src/main/resources/
│   │   ├── app.yml                          # Solon 应用配置
│   │   └── logback.xml                      # 日志配置
│   └── src/main/java/site/sorghum/agent4j/bin/
│       ├── Agent4jApp.java                  # ★ CLI 主入口（main + Scanner 交互循环）
│       ├── agent/                           # 核心代理引擎（12 个类）
│       │   ├── Agent4jAgent.java            # 代理工厂（Builder 模式组装组件）
│       │   ├── AgentLoop.java               # ★ 推理循环引擎（prompt→LLM→tool→循环）
│       │   ├── AgentLoopListener.java       # 循环事件监听器接口
│       │   ├── AgentOutput.java             # 输出抽象接口
│       │   ├── ConsoleAgentOutput.java      # 控制台输出实现
│       │   ├── ConversationContext.java     # 会话上下文（内存 + 持久化）
│       │   ├── PromptPrefix.java            # 不可变前缀（system + tools，缓存优先）
│       │   ├── ContextFolding.java          # 上下文折叠（语义压缩）
│       │   ├── MessageHealer.java           # 消息修复器
│       │   ├── StormBreaker.java            # 风暴断路器
│       │   ├── ReasonBreaker.java           # 推理断路器
│       │   ├── Scavenger.java               # 工具调用回收
│       │   └── SubAgent.java                # 子代理（独立循环）
│       ├── builtin/                         # 内置 Skill 工具
│       │   ├── InstallSkillTool.java        # 创建/保存 skill
│       │   ├── RunSkillTool.java            # 调用 skill
│       │   └── TaskTool.java                # 子代理任务委派
│       ├── command/                         # 命令系统（14 个命令）
│       │   ├── ChatCommand.java             # 命令接口
│       │   ├── ChatCommandContext.java      # 命令执行上下文
│       │   ├── ChatCommandRegistry.java     # 命令注册表
│       │   └── impl/                        # 14 个命令实现
│       ├── config/
│       │   └── Agent4jConfig.java           # 配置加载
│       ├── model/
│       │   ├── ModelClient.java             # 模型客户端接口
│       │   └── HttpModelClient.java         # HTTP/SSE 实现
│       ├── session/
│       │   ├── SessionStore.java            # 持久化接口
│       │   ├── JsonlSessionStore.java       # JSONL 实现
│       │   └── SessionService.java          # 会话管理服务
│       ├── skill/
│       │   ├── SkillV2.java                 # Skill 接口
│       │   ├── SkillV2Impl.java             # Skill 实现
│       │   └── SkillStoreV2.java            # Skill 存储
│       ├── tool/                            # 工具注册/调度
│       │   ├── ToolDef.java                 # 工具定义
│       │   ├── ToolDefHelper.java           # 工具定义辅助
│       │   ├── ToolRegistry.java            # 工具注册中心
│       │   ├── ToolDispatcher.java          # 工具调度器
│       │   └── ToolSchemaFlattener.java     # Schema 展平
│       └── workspace/
│           └── WorkspaceManager.java        # 工作区管理器
│
├── agent4j-web/                             # ★ Web REST API 模块
│   ├── pom.xml
│   └── src/main/java/site/sorghum/agent4j/web/
│       ├── Agent4jWebApp.java               # Web 入口（端口 8097）
│       ├── controller/
│       │   ├── ChatController.java          # 聊天 API（POST 同步 + GET SSE）
│       │   ├── SessionController.java       # 会话管理 API
│       │   ├── AgentController.java         # Agent 状态 API
│       │   ├── ToolController.java          # 工具管理 API
│       │   ├── ConfigController.java        # 配置 API
│       │   ├── SystemController.java        # 系统状态 API
│       │   └── GitController.java           # Git 操作 API
│       ├── service/
│       │   ├── AgentService.java            # Agent 单例服务（串行锁）
│       │   ├── SseEmitter.java              # SSE 流式输出
│       │   ├── SseAgentOutput.java          # AgentOutput → SSE 桥接
│       │   ├── ApiAgentOutPut.java          # API 输出实现
│       │   └── WebUsageListener.java        # Web 用量监听
│       ├── model/
│       │   ├── ApiResponse.java             # 统一响应封装
│       │   ├── ChatRequest.java             # 聊天请求体
│       │   ├── ChatResultDTO.java           # 聊天结果 DTO
│       │   ├── ToolExecuteRequest.java      # 工具执行请求
│       │   ├── SessionInfoDTO.java          # 会话信息 DTO
│       │   ├── ToolInfoDTO.java             # 工具信息 DTO
│       │   ├── ConfigDTO.java               # 配置 DTO
│       │   └── ... (20+ DTO 类)
│       └── common/
│           ├── ServiceException.java        # 业务异常
│           └── GlobalExceptionFilter.java   # 全局异常过滤
│
├── agent4j-front/                           # ★ Vue 3 前端
│   ├── package.json / vite.config.js / index.html
│   └── src/
│       ├── App.vue / main.js
│       ├── views/    (Chat, Home, Sessions, Settings, Tools, Help, NotFound)
│       ├── components/ (Sidebar, Composer, StatusBar, TabBar, TitleBar, UsagePanel)
│       ├── composables/useTerminal.js
│       ├── router/index.js
│       ├── services/api.js
│       └── stores/app.js
│
├── agent4j-tauri/                           # ★ Tauri 桌面端
│   ├── package.json
│   └── src-tauri/
│       ├── Cargo.toml / tauri.conf.json
│       └── src/ (main.rs, lib.rs)
│
├── agent4j-tui/                             # TUI 模块（预留）
├── demo/                                    # 演示 HTML 文件
└── example-skills/                          # 示例 Skill 定义
    ├── explore/SKILL.md
    └── java-conventions/SKILL.md
```

**统计**: ~172 个 Java 源文件，5 个 Maven 模块，工具类 28 个，命令 14 个，DTO 20+ 个

---

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| **Java** | 17 | 开发语言（record / sealed / pattern matching） |
| **Maven** | 3.x | 构建工具 & 多模块管理 |
| **Solon** | 3.9.6 | IoC 容器 / AOP / Web（轻量级替代 Spring Boot） |
| **Snack4** | 4.0.49 | JSON 解析与序列化（ONode API） |
| **Lombok** | 1.18.34 | 样板代码简化（@Slf4j, @Getter, record 等） |
| **JUnit Jupiter** | 5.10.3 | 单元测试 |
| **Logback** | via Solon | 高性能日志实现 |
| **SLF4J** | 1.7.36 | 日志门面 |
| **tree-sitter** | native | AST 解析（代码符号提取） |
| **Vue 3** | 3.x | 前端框架（agent4j-front） |
| **Vite** | 5.x | 前端构建工具 |
| **Pinia** | 2.x | 状态管理 |
| **Vue Router** | 4.x | 前端路由 |
| **Tauri** | v2 | 桌面端框架（Rust 后端 + WebView） |
| **Rust** | stable | Tauri 后端语言 |

---

## 架构设计

### 三层架构

```
┌──────────────────────────────────────────────────────────┐
│              入口层 · Presentation                         │
│                                                          │
│  CLI:  Agent4jApp     (main + Scanner 交互循环)          │
│  Web:  Agent4jWebApp  (REST API, Solon-Web, 端口 8097)  │
│  Desktop: Tauri       (Rust WebView + Vue 前端)          │
└────────────────────────┬─────────────────────────────────┘
                         │
┌────────────────────────▼─────────────────────────────────┐
│              核心层 · Core Engine                          │
│                                                          │
│  Agent4jAgent (工厂 · Builder 模式)                       │
│    ├── 组装 ModelClient + ToolRegistry + SessionService   │
│    ├── 自动发现 @Component AgentTool Bean                 │
│    ├── 加载项目文档 (agent4j.md / CLAUDE.md)               │
│    └── 构建缓存优先 PromptPrefix                          │
│                                                          │
│  AgentLoop (推理循环引擎 · ~1300 行)                       │
│    ├── prepareMessages   → 构建 + Heal + Fold + 工具指引  │
│    ├── streamLLM         → 流式调用 (CountDownLatch)      │
│    ├── scavengeToolCalls → 回收丢失的调用                 │
│    ├── executeToolCalls  → 并行执行 (CompletableFuture)   │
│    ├── handleSelfCorrection → 风暴自愈 (MAX 5 次)         │
│    └── HITL 拦截         → 写入前审批 + 沙箱越界审批      │
│                                                          │
│  ConversationContext (会话上下文)                          │
│    ├── 内存历史: List<ChatMessage>                       │
│    ├── PromptPrefix: 不可变前缀 (缓存优先)                 │
│    └── SessionStore: JSONL 持久化                         │
│                                                          │
│  ToolDispatcher (工具调度器)                               │
│    ├── PlanMode 门控 (写入工具拦截)                        │
│    ├── StormBreaker 检查 (滑动窗口去重)                    │
│    ├── Pre/Post Hooks (拦截器链)                          │
│    └── 参数注入 (sessionId)                               │
└────────────────────────┬─────────────────────────────────┘
                         │
┌────────────────────────▼─────────────────────────────────┐
│              基础层 · Infrastructure                       │
│                                                          │
│  ModelClient       ToolRegistry       SessionStore       │
│  (策略接口)         (工具注册中心)      (持久化接口)        │
│  │                  │                   │                 │
│  └─HttpModelClient  └─LinkedHashMap     └─JsonlSessionStore│
│    (HTTP/SSE)         (名称索引)          (JSONL+ReentrantLock)│
│                                                          │
│  JobRegistry        MemoryService      WorkspaceManager  │
│  (进程注册表)        (~/.agent4j/memory/)  (多工作区隔离)   │
│                                                          │
│  SkillStoreV2       WorkspaceIndex     CommandAllowlist  │
│  (Skill 存储)        (文件索引缓存)       (命令白名单)      │
└──────────────────────────────────────────────────────────┘
```

### 核心推理循环流程

```
用户输入 (chat)
    │
    ▼
Agent4jAgent.chat(input)
    ├── "/" 命令? → ChatCommandRegistry.dispatch() → 返回命令结果
    └── 普通消息 → AgentLoop.run(message)
         │
         ├── HITL 恢复? → resumeAfterHITL(approved/denied)
         │
         ├── 1. ctx.addUser(msg)              ← 追加用户消息 + 持久化
         │
         ├── for each step (直到模型返回纯文本):
         │   │
         │   ├── a. prepareMessages()
         │   │      ├── buildMessages()
         │   │      ├── MessageHealer.heal()  ← 修复消息序列
         │   │      ├── 预检折叠 (tokens > 80% 窗口)
         │   │      │   └── ContextFolding.fold() → ctx.compact()
         │   │      └── 注入工具使用指引
         │   │
         │   ├── b. streamLLM()
         │   │      ├── onReasoningDelta()     ← ReasonBreaker 循环检测
         │   │      ├── onContentDelta()       ← 实时推送
         │   │      ├── onToolCalls()          ← 收集工具调用
         │   │      └── onUsage()              ← Token 用量
         │   │
         │   ├── c. scavengeToolCalls()        ← 回收丢失调用
         │   │
         │   ├── d. 无 tool_calls → handleTextResponse() → 返回
         │   │
         │   ├── e. HITL 拦截 → 暂停等待审批
         │   │
         │   └── f. executeToolCalls()         ← 并行执行
         │          ├── dispatcher.dispatch()  ← PlanMode/Storm/Hooks 门控
         │          ├── ctx.addToolResult()    ← 写入结果
         │          └── handleSelfCorrection() ← 风暴自愈 (MAX 5)
         │
         └── 返回最终 assistant content
```

### 关键设计模式

| 模式 | 位置 | 说明 |
|------|------|------|
| **不可变前缀** | `PromptPrefix` | system prompt + tool specs 跨 turn 不变，最大化 DeepSeek 前缀缓存命中 |
| **工厂模式** | `Agent4jAgent.Builder` | 链式 Builder 构建复杂对象，支持轻量级构建共享组件 |
| **策略模式** | `ModelClient` 接口 | 可替换的 LLM API 实现（HTTP/SSE） |
| **观察者模式** | `AgentLoopListener` | 推理/工具调用/用量的事件回调 |
| **模板方法** | `AgentTool` | 子类实现 getName/getDescription/getParameters/execute |
| **命令模式** | `ChatCommand` + `ChatCommandRegistry` | "/" 命令通过 Solon IoC 自动注册和匹配 |
| **断路器** | `StormBreaker` | 滑动窗口（W=6, T=3）+ JSON 指纹去重 |
| **断路器** | `ReasonBreaker` | 流式检测推理死循环 |
| **并行执行** | `AgentLoop.executeToolCalls()` | CompletableFuture.supplyAsync 并行分发 |
| **无忙等待** | `CountDownLatch` / `AtomicBoolean` | 流式等待不阻塞 |
| **缓冲写入** | `JsonlSessionStore` | BufferedWriter 保持打开 + flush() + 30s 定时刷入 |
| **单一入口** | `Agent4jAgent.chat()` | 命令/消息统一路由，避免逻辑重复 |
| **沙箱旁路** | `ToolContext.enableSandboxBypass()` | ThreadLocal 控制，HITL 审批后跳过越界检查 |
| **享元模式** | `PromptPrefix` | 共享 system prompt + tool defs，多 Agent 实例复用 |

---

## 全部工具列表

### 核心工具（5 个 · AgentTool 基类）

| 工具名 | 只读 | 风暴豁免 | 说明 |
|--------|------|----------|------|
| `run_command` | ❌ | ❌ | Shell 命令执行（自解析 argv，跨平台；支持管道/重定向） |
| `file` | ❌ | ❌ | 文件系统操作（create_dir/create_file/delete_file/delete_dir/move/copy/stat） |
| `glob` | ✅ | ✅ | glob 文件名匹配（基于 WorkspaceIndex 缓存，毫秒级） |
| `grep` | ✅ | ✅ | 正则内容搜索（自动跳过二进制/大文件/denylist 目录） |
| `tree` | ✅ | ✅ | 目录树生成（maxDepth 控制深度，0=根，-1=无限） |

### 文件操作工具（6 个）

| 工具名 | 只读 | 风暴豁免 | 参数 | 说明 |
|--------|------|----------|------|------|
| `read_file` | ✅ | ✅ | path, head?, tail?, range? | 完整读取文件（>100 MiB 拒绝） |
| `edit_file` | ❌ | ❌ | path, search, replace | SEARCH/REPLACE 单文件编辑（search 必须唯一） |
| `write_file` | ❌ | ❌ | path, content | 创建/覆盖文件（自动创建父目录） |
| `multi_edit` | ❌ | ❌ | edits[{path,search,replace}] | 原子批量编辑（全验证→全写入→失败回滚） |
| `copy_file` | ❌ | ❌ | source, destination | 复制文件或目录 |
| `get_file_info` | ✅ | ✅ | path | 查看元信息（JSON: type/size/mtime） |

### 代码分析工具（3 个）

| 工具名 | 只读 | 风暴豁免 | 参数 | 说明 |
|--------|------|----------|------|------|
| `get_symbols` | ✅ | ✅ | path | tree-sitter AST 顶层符号大纲 |
| `find_in_code` | ✅ | ✅ | path | 在文件中查找标识符（AST 过滤） |
| `java_source` | ✅ | ✅ | className, jarKeyword? | 通过全限定类名定位 Java 源码 |

### 网络工具（2 个）

| 工具名 | 只读 | 风暴豁免 | 参数 | 说明 |
|--------|------|----------|------|------|
| `web_search` | ✅ | ✅ | query | DuckDuckGo Lite 搜索 |
| `web_fetch` | ✅ | ✅ | url | 下载 URL + HTML 正文提取 |

### 记忆工具（3 个）

| 工具名 | 只读 | 风暴豁免 | 参数 | 说明 |
|--------|------|----------|------|------|
| `remember` | ❌ | ❌ | name, type, scope, description, content, priority? | 持久化保存记忆 |
| `recall_memory` | ✅ | ✅ | name | 读取完整记忆内容 |
| `forget` | ❌ | ❌ | name | 删除记忆 |

### 后台作业工具（5 个）

| 工具名 | 只读 | 风暴豁免 | 参数 | 说明 |
|--------|------|----------|------|------|
| `run_background` | ❌ | ❌ | command, cwd?, waitSec? | 启动后台进程并分离 |
| `job_output` | ✅ | ✅ | jobId, since?, tailLines? | 读取作业输出（增量轮询） |
| `wait_for_job` | ❌ | ❌ | jobId, timeoutMs?, waitFor? | 阻塞等待作业完成 |
| `stop_job` | ❌ | ❌ | jobId | 停止作业（SIGTERM→SIGKILL） |
| `list_jobs` | ✅ | ✅ | — | 列出所有后台作业 |

### 计划与交互工具（5 个）

| 工具名 | 只读 | 风暴豁免 | 参数 | 说明 |
|--------|------|----------|------|------|
| `submit_plan` | ❌ | ❌ | summary?, plan, steps[] | 提交 Markdown 执行计划 |
| `revise_plan` | ❌ | ❌ | reason, remainingSteps | 修订进行中计划的剩余步骤 |
| `mark_step_complete` | ❌ | ❌ | stepId, result?, evidence? | 标记计划步骤完成 |
| `ask_choice` | ❌ | ❌ | question, options, allowCustom? | 箭头键选择菜单（2-6 选项） |
| `todo_write` | ❌ | ❌ | todos[{status,content}] | 任务跟踪列表 |

### Skill 系统工具（3 个）

| 工具名 | 只读 | 风暴豁免 | 参数 | 说明 |
|--------|------|----------|------|------|
| `install_skill` | ❌ | ❌ | name, description, body, scope?, runAs? | 创建/保存 skill |
| `run_skill` | ✅ | ✅ | name, arguments? | 调用用户定义的 skill |
| `task` | ❌ | ❌ | name, arguments? | 创建隔离子代理处理复杂多步任务 |

**工具总计: 32 个**（核心 5 + 文件 6 + 代码 3 + 网络 2 + 记忆 3 + 作业 5 + 计划 5 + Skill 3）

---

## 会话生命周期

```
SessionService
  │
  ├── 启动: loadOrCreate() → 恢复最近会话
  │      └── switchTo() → 延迟创建文件（首次 append 时创建 .jsonl）
  │
  ├── 运行时: ctx.addUser/addAssistant/addToolResult()
  │      └── persist() → SessionStore.append()
  │           ├── BufferedWriter.write + newLine
  │           ├── flush() → 显式刷入（退出/compact/每轮对话后）
  │           └── 定时刷入 → ScheduledExecutorService 每 30s
  │
  ├── /compact: ContextFolding.fold()
  │      ├── 头部 → LLM 摘要（单次 API 调用）
  │      ├── 尾部保留（~80KB）
  │      └── rewrite() 回写 JSONL
  │
  ├── /retry / /rewind N: 消息回退
  │      └── rewrite() 回写 JSONL
  │
  ├── /new: 归档当前 → 创建新会话
  │      ├── archive() → 重命名 xxx__archive_yyyymmddHHmmss.jsonl
  │      └── 不创建空文件（延迟到首次写入）
  │
  └── 退出: flush() → saveUsage() → shutdown()
```

**文件位置**: `~/.agent4j/`（支持工作区隔离：`~/.agent4j/workspace/{hash}/`）

| 文件 | 说明 |
|------|------|
| `config.json` | LLM 配置（baseUrl/apiKey/model/workspaceDir/editMode/reasoningEffort/lang/hitl/disabledTools/blockedPaths） |
| `sessions/*.jsonl` | 会话消息（JSONL 格式，每行一条消息） |
| `sessions/*.usage` | Token 用量统计 |
| `sessions/*.meta` | 会话元信息（标题等） |
| `memory/*.json` | 持久化记忆 |
| `skills/` | 全局自定义 Skill |
| `.agent4j/skills/` | 项目级自定义 Skill |

---

## 运行方式

### 前置条件

- JDK 17+
- Maven 3.x
- 一个兼容 OpenAI API 的 LLM 端点（DeepSeek / Ollama / OpenAI）

### 配置文件

首次启动自动创建 `~/.agent4j/config.json`：

```json
{
  "baseUrl": "https://api.deepseek.com/v1",
  "apiKey": "sk-your-api-key",
  "model": "deepseek-chat",
  "workspaceDir": ".",
  "editMode": "auto",
  "reasoningEffort": "max",
  "lang": "ZH",
  "hitl": false,
  "disabledTools": [],
  "blockedPaths": [],
  "availableModels": ["deepseek-chat", "gpt-4o", "claude-3.5-sonnet"]
}
```

### 环境变量覆盖

| 环境变量 | 说明 |
|----------|------|
| `OPENAI_BASE_URL` | LLM API 地址 |
| `OPENAI_API_KEY` | API 密钥 |
| `MODEL` | 模型名称 |
| `AGENT4J_DISABLED_TOOLS` | 禁用的工具（逗号分隔） |
| `AGENT4J_BLOCKED_PATHS` | 禁止访问的路径（逗号分隔） |

### 编译

```bash
# 完整编译
mvn clean compile

# 仅编译核心模块
mvn compile -pl agent4j-tool,agent4j-bin
```

### 运行

```bash
# CLI 模式（交互式终端）
mvn exec:java -pl agent4j-bin \
  -Dexec.mainClass="site.sorghum.agent4j.bin.Agent4jApp"

# Web 模式（REST API，默认端口 8097）
mvn exec:java -pl agent4j-web \
  -Dexec.mainClass="site.sorghum.agent4j.web.Agent4jWebApp"

# 打 Web fat jar
mvn package -pl agent4j-web -DskipTests
java -jar agent4j-web/target/agent4j-web.jar
```

### CLI 交互命令

| 命令 | 功能 |
|------|------|
| `/help` | 显示帮助信息 |
| `/new` | 归档当前会话，开启新会话 |
| `/plan` | 进入计划模式（仅只读工具可用） |
| `/execute` | 退出计划模式，恢复所有工具 |
| `/compact` | 折叠历史消息（语义摘要） |
| `/retry` | 撤回最后一条消息并重试 |
| `/rewind N` | 回退到第 N 轮对话 |
| `/sessions` | 列出所有历史会话 |
| `/load N` | 加载指定会话 |
| `/init` | 自动分析项目生成 agent4j.md |
| `/hitl` | 切换 HITL 模式 |
| `/agree` | 批准 HITL 待执行的工具调用 |
| `/deny` | 拒绝 HITL 待执行的工具调用 |
| `/exit` | 退出程序 |

### Web REST API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/chat` | POST | 同步聊天（message + sessionId） |
| `/api/chat/stream` | GET | SSE 流式聊天 |
| `/api/sessions` | GET/POST | 会话列表 / 创建新会话 |
| `/api/sessions/{id}` | GET/DELETE | 获取 / 删除会话 |
| `/api/tools` | GET | 列出所有可用工具 |
| `/api/tools/{name}/execute` | POST | 执行工具 |
| `/api/agent` | GET | Agent 状态（模型/工具/工作区） |
| `/api/config` | GET | 配置查询 |

### 测试

```bash
# 所有测试
mvn test

# 仅工具模块
mvn test -pl agent4j-tool

# 仅 bin 模块
mvn test -pl agent4j-bin
```

---

## 模块依赖关系

```
agent4j (父 POM · pom)
  │
  ├── agent4j-tool     (核心工具库)
  │     └── 依赖: solon-lib, lombok, junit-jupiter
  │
  ├── agent4j-bin      (可执行入口 · CLI)
  │     ├── 依赖: agent4j-tool
  │     └── 外部: solon-lib, solon-web, solon-logging-logback, snack4
  │
  ├── agent4j-web      (Web REST API)
  │     ├── 依赖: agent4j-bin
  │     └── 外部: solon-web, solon-web-cors, snack4, maven-shade-plugin
  │
  ├── agent4j-front    (Vue 3 前端 · 独立)
  │     └── 依赖: Vue 3, Vite, Pinia, Vue Router
  │
  └── agent4j-tauri    (Tauri 桌面端 · 独立)
        └── 依赖: Tauri v2, Rust
```

---

## 项目亮点

1. **缓存优先设计** — PromptPrefix 不可变前缀 + 稳定 tool specs 排序，最大化 DeepSeek 前缀缓存命中
2. **并行执行** — CompletableFuture.allOf 并行分发工具调用，CountDownLatch 无忙等待
3. **风暴断路器 × 推理断路器** — StormBreaker（滑动窗口 + JSON 指纹）+ ReasonBreaker（流式检测），双保险防死循环
4. **消息自愈** — MessageHealer 发送前自动修复（配对 tool_calls、补 reasoning、截断过长结果）
5. **上下文折叠** — ContextFolding 用一次 LLM 调用将旧消息压缩为摘要，替代机械截断
6. **工具调用回收** — Scavenger 从 reasoning_content 中提取因 API 格式问题遗漏的 tool_calls
7. **子代理隔离** — SubAgent 创建独立循环执行复杂任务，排除递归 spawn 和用户交互工具
8. **HITL 双模式** — 普通 HITL（写入操作审批）+ 沙箱越界 HITL（路径穿越强制审批，独立管道）
9. **原子批量编辑** — multi_edit 全验证→全写入→失败回滚，保证跨文件数据一致性
10. **缓冲写入与安全** — BufferedWriter 保持打开 + 显式 flush + 30s 定时刷入 + ReentrantLock
11. **命令模式扩展** — 新增命令只需实现 ChatCommand 接口 + @Component，IoC 自动收集
12. **路径穿越防护** — resolveSafe() 严格校验 + 屏蔽目录列表 + 沙箱越界 HITL
13. **Skill 系统 V2** — 用户可定义可复用的 Skill playbook，支持 inline 和 subagent 两种运行模式
14. **多端交付** — CLI / Web REST API / Vue 3 前端 / Tauri 桌面端，四端共享同一核心

---

## Agent4j Web

> AI 编码代理 Web 服务 — 通过 REST API 暴露全部 Agent 功能

### 快速开始

1. **配置 API Key**
   编辑 `~/.agent4j/config.json`，填入你的 LLM API Key：
   ```json
   {
     "baseUrl": "https://api.deepseek.com/v1",
     "apiKey": "sk-your-api-key",
     "model": "deepseek-chat"
   }
   ```

2. **启动服务**
   ```bash
   agent4j-web
   ```

3. **访问 API**
   - 默认地址：http://localhost:8097
   - 聊天接口：POST /api/chat
   - 会话管理：GET/POST /api/sessions
   - 工具列表：GET /api/tools
   - Agent 控制：GET /api/agent
   - 配置查询：GET /api/config

### API 接口

**POST /api/chat**
```json
{
  "message": "帮我分析这个项目",
  "sessionId": "optional-session-id"
}
```

**GET /api/chat/stream?message=xxx**
返回 Server-Sent Events 流：
```
data: {"type":"content","content":"正在分析..."}
data: {"type":"tool_call","name":"read_file","args":{...}}
data: {"type":"tool_result","result":"..."}
data: {"type":"done"}
```

---

*本文档由 Agent4j 代码库分析生成 · 2025 年 7 月*
