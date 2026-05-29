# Agent4j — Java AI Agent 框架

> 参考 **Reasonix 架构**的纯 Java AI 代理框架，提供推理循环、工具调用、会话管理、流式输出等完整能力。
> 作者: Sorghum · 许可证: MIT

---

## 项目概述

Agent4j 是一个 Java 17 实现的 AI 编码代理框架，将 LLM（大语言模型）与可扩展的工具系统结合，形成一个自主工作的编码代理。

| 属性 | 值 |
|------|-----|
| **全称** | Agent4j — Code Agent for Java |
| **版本** | 1.0-SNAPSHOT |
| **Java 目标** | 17 |
| **构建工具** | Maven 多模块 |
| **IoC 框架** | Solon 3.9.6 |
| **包名** | `site.sorghum.agent4j` |
| **远程仓库** | https://gitee.com/ezdemo/agent4j.git |

### 核心能力

- ✅ 对话式推理循环（多轮上下文保持）
- ✅ OpenAI 兼容 API 流式调用（SSE）
- ✅ 工具注册/调度/并行执行（CompletableFuture.allOf）
- ✅ 计划模式（Plan Mode — 只读探索 + 计划提交审批）
- ✅ 风暴断路器（StormBreaker — 滑动窗口防重复调用死循环）
- ✅ 推理断路器（ReasonBreaker — 流式检测思考循环）
- ✅ 消息自愈（MessageHealer — 发送前修复消息序列）
- ✅ 上下文折叠（ContextFolding — 长对话语义压缩）
- ✅ 工具调用回收（Scavenger — 从 reasoning_content 回收丢失的调用）
- ✅ 会话持久化（JSONL + BufferedWriter + 定时刷入 + ReentrantLock）
- ✅ Token 用量追踪与缓存统计
- ✅ 子代理隔离（SubAgent — 独立循环委派复杂任务）
- ✅ HITL 人机协同（Human-In-The-Loop — 写入操作需审批）
- ✅ 沙箱越界审批（路径穿越时强制触发 HITL，与普通 HITL 独立）
- ✅ 后台作业管理（run_background / job_output / wait_for_job）
- ✅ 记忆服务（持久化键值记忆，支持 global/project 作用域）
- ✅ Skill 系统（V2 — install_skill / run_skill，支持 inline/subagent 模式）
- ✅ 网络搜索与网页抓取（DuckDuckGo Lite + HTML 正文提取）
- ✅ Web REST API（Solon-Web + SSE 流式输出）
- ✅ Vue 3 前端（agent4j-front — Vite + Pinia + Vue Router）
- ✅ Tauri 桌面端（agent4j-tauri — Rust 后端 + WebView 前端）

---

## 目录结构

```
agent4j/
├── pom.xml                                  # 父 POM，多模块管理（Maven 3.x）
├── README.md                                # 项目说明
├── ANALYSIS.md                              # 项目分析报告
├── agent4j.md                               # 本文档
├── SKILL_README.md                          # Skill 系统说明
├── TEST_SUMMARY.md                          # 测试总结
├── icon.png                                 # 项目图标
├── .gitignore
│
├── agent4j-tool/                            # ★ 核心工具库模块（agent4j-tool）
│   ├── pom.xml
│   └── src/main/java/site/sorghum/agent4j/tool/
│       ├── AgentTool.java                   # 工具抽象基类（模板方法模式）
│       ├── ToolContext.java                 # 工具执行上下文（参数/根目录/API/沙箱旁路）
│       ├── ToolParameter.java              # 工具参数定义（record: name/type/required/description）
│       ├── ToolResult.java                 # 工具执行结果封装（success/text/data/errorCode/suggestion）
│       ├── HitlRequiredException.java      # HITL 异常（沙箱越界时抛出）
│       ├── code/                            # 代码分析服务
│       │   ├── CodeQueryService.java        # Java 源码查找服务
│       │   ├── FindInCodeTool.java          # AST 标识符查找
│       │   ├── GetSymbolsTool.java          # tree-sitter 符号大纲
│       │   └── JavaSourceTool.java          # 全限定类名定位源码
│       ├── file/                            # 文件系统服务
│       │   ├── FileSystemService.java       # 目录列表/文件名搜索/路径安全解析
│       │   ├── FileEdit.java               # SEARCH/REPLACE 编辑引擎
│       │   ├── FileTool.java               # 基础文件操作（create/move/copy/delete/stat）
│       │   ├── ReadFileTool.java           # 读取文件（支持 head/tail/range）
│       │   ├── EditFileTool.java           # SEARCH/REPLACE 单文件编辑
│       │   ├── WriteFileTool.java          # 创建/覆盖文件
│       │   ├── MultiEditTool.java          # 多文件原子批量编辑
│       │   ├── CopyFileTool.java           # 复制文件/目录
│       │   └── GetFileInfoTool.java        # 查看文件元信息
│       ├── interact/                        # 用户交互服务
│       │   ├── InteractionService.java     # 用户选择菜单服务
│       │   ├── AskChoiceTool.java          # 箭头键选择菜单（2-6 选项）
│       │   └── TodoWriteTool.java          # 任务跟踪列表
│       ├── job/                             # 后台作业服务
│       │   ├── JobRegistry.java           # 后台作业注册表（进程管理）
│       │   ├── JobService.java            # 作业服务（启动/读取/等待/停止）
│       │   ├── RunBackgroundTool.java     # 启动后台进程
│       │   ├── JobOutputTool.java         # 读取作业输出（增量轮询）
│       │   ├── WaitForJobTool.java        # 等待作业完成
│       │   ├── StopJobTool.java           # 停止作业（SIGTERM→SIGKILL）
│       │   └── ListJobsTool.java          # 列出所有后台作业
│       ├── memory/                          # 记忆服务
│       │   ├── MemoryService.java          # 持久化键值记忆 CRUD
│       │   ├── RememberTool.java           # 保存记忆（name/type/scope/description/content/priority）
│       │   ├── RecallMemoryTool.java       # 读取完整记忆内容
│       │   └── ForgetTool.java             # 删除记忆
│       ├── plan/                            # 计划管理服务
│       │   ├── PlanService.java            # 计划服务（提交/修订/步骤跟踪）
│       │   ├── SubmitPlanTool.java         # 提交执行计划供审查
│       │   ├── RevisePlanTool.java         # 修订进行中计划的剩余步骤
│       │   └── MarkStepCompleteTool.java   # 标记计划步骤完成
│       ├── search/                          # 搜索服务
│       │   ├── WorkspaceIndex.java         # 工作区文件索引（缓存 mtime 增量刷新 + .gitignore）
│       │   ├── GrepTool.java               # 正则内容搜索（自动跳过二进制/大文件）
│       │   ├── GlobTool.java               # glob 文件名匹配（毫秒级响应）
│       │   ├── TreeTool.java               # 目录树生成（maxDepth 控制深度）
│       │   ├── SearchMatch.java            # 搜索结果数据结构
│       │   └── FileMeta.java               # 文件元数据
│       ├── terminal/                        # 终端服务
│       │   ├── TerminalTool.java           # Shell 命令执行（自解析 argv，跨平台）
│       │   ├── CommandAllowlist.java       # 命令白名单校验（只读/测试/检查类命令免确认）
│       │   ├── CommandChainParser.java     # 管道/重定向链解析（|、&&、||、;、>、>>、<）
│       │   ├── CommandTokenizer.java       # shell 分词器
│       │   ├── ProcessTreeKiller.java      # 进程树终止器（SIGTERM→SIGKILL）
│       │   └── SmartDecoder.java           # 智能输出解码器（自动检测编码）
│       ├── web/                             # 网络服务
│       │   ├── WebService.java             # 搜索 + 抓取服务
│       │   ├── WebSearchTool.java          # DuckDuckGo Lite 搜索
│       │   └── WebFetchTool.java           # URL 下载 + HTML 正文提取
│       └── test/java/.../tool/             # 单元测试
│           ├── file/FileEditTest.java
│           ├── file/FileToolTest.java
│           ├── search/WorkspaceIndexTest.java
│           ├── search/SearchToolsTest.java
│           ├── ToolContextTest.java
│           ├── ToolParameterTest.java
│           └── ToolResultTest.java
│
├── agent4j-bin/                             # ★ 可执行入口模块（agent4j-bin）
│   ├── pom.xml
│   ├── src/main/resources/
│   │   ├── app.yml                          # Solon 应用配置
│   │   └── logback.xml                      # 日志配置
│   └── src/main/java/site/sorghum/agent4j/bin/
│       ├── Agent4jApp.java                  # ★ CLI 主入口（main + Scanner 交互循环）
│       ├── agent/                           # 核心代理引擎
│       │   ├── Agent4jAgent.java            # ★ 代理工厂（Builder 模式组装组件）
│       │   ├── AgentLoop.java               # ★ 核心推理循环引擎（prompt→LLM→tool→循环 690 行）
│       │   ├── AgentLoopListener.java       # 循环事件监听器接口（观察者模式）
│       │   ├── AgentOutput.java             # 输出抽象接口（支持控制台/SSE/自定义）
│       │   ├── ConsoleAgentOutput.java      # 控制台输出实现
│       │   ├── ConversationContext.java     # 会话上下文（内存消息历史 + JSONL 持久化）
│       │   ├── PromptPrefix.java            # 不可变前缀（system + tools，缓存优先）
│       │   ├── ContextFolding.java          # 上下文折叠（头部→API 摘要 + 尾部保留）
│       │   ├── MessageHealer.java           # 消息修复器（配对 tool_calls、截断、补 reasoning）
│       │   ├── StormBreaker.java            # 风暴断路器（滑动窗口 W=6 T=3 + JSON 指纹）
│       │   ├── ReasonBreaker.java           # 推理断路器（流式检测思考循环）
│       │   ├── Scavenger.java               # 工具调用回收（从 reasoning 中提取遗漏的调用）
│       │   └── SubAgent.java                # 子代理（独立循环隔离执行复杂任务）
│       ├── app/
│       │   └── AppConfig.java               # Solon IoC Bean 配置（@Configuration）
│       ├── builtin/                         # 内置 Skill 工具
│       │   ├── InstallSkillTool.java        # 创建/保存 skill（inline/subagent 模式）
│       │   ├── RunSkillTool.java            # 调用用户定义的 skill
│       │   └── TaskTool.java                # 子代理任务委派
│       ├── command/                         # 命令系统（命令模式 + IoC 自动注册）
│       │   ├── ChatCommand.java             # 命令接口（matches/execute/CommandResult）
│       │   ├── ChatCommandContext.java      # 命令执行上下文
│       │   ├── ChatCommandRegistry.java     # 命令注册表（自动收集 @Component Bean）
│       │   └── impl/                        # 14 个命令实现
│       │       ├── HelpCommand.java         # /help — 显示帮助
│       │       ├── NewSessionCommand.java   # /new — 开启新会话
│       │       ├── PlanCommand.java         # /plan — 进入计划模式
│       │       ├── ExecuteCommand.java      # /execute — 退出计划模式
│       │       ├── CompactCommand.java      # /compact — 折叠历史消息
│       │       ├── RetryCommand.java        # /retry — 撤回最后一条消息重试
│       │       ├── RewindCommand.java       # /rewind N — 回退到第 N 轮
│       │       ├── SessionsCommand.java     # /sessions — 列出历史会话
│       │       ├── LoadCommand.java         # /load N — 加载指定会话
│       │       ├── InitCommand.java         # /init — 自动分析项目生成 agent4j.md
│       │       ├── HitlCommand.java         # /hitl — 切换 HITL 模式
│       │       ├── AgreeCommand.java        # /agree — 批准工具调用
│       │       ├── DenyCommand.java         # /deny — 拒绝工具调用
│       │       └── ExitCommand.java         # /exit — 退出程序
│       ├── config/
│       │   └── Agent4jConfig.java           # 配置加载（~/.agent4j/config.json + 环境变量）
│       ├── model/                           # LLM API 客户端
│       │   ├── ModelClient.java             # 模型客户端接口（策略模式）
│       │   └── HttpModelClient.java         # HTTP/SSE 实现（OpenAI 兼容 API）
│       ├── session/                         # 会话持久化
│       │   ├── SessionStore.java            # 持久化仓库接口
│       │   ├── JsonlSessionStore.java       # JSONL 文件实现（BufferedWriter + ReentrantLock + 30s 定时刷入）
│       │   └── SessionService.java          # 会话管理服务（加载/保存/切换/用量统计）
│       ├── skill/                           # Skill 系统 V2
│       │   ├── SkillV2.java                 # Skill 接口
│       │   ├── SkillV2Impl.java             # Skill 实现
│       │   └── SkillStoreV2.java            # Skill 存储（project + global 两级目录）
│       ├── tool/                            # 工具注册/调度/展平
│       │   ├── ToolDef.java                 # 工具定义（record: name/description/params/fn/readOnly/stormExempt/toolSpec）
│       │   ├── ToolDefHelper.java           # 工具定义辅助（param → ParamDef 转换）
│       │   ├── ToolRegistry.java            # 工具注册中心（注册/查询/toOpenAiTools）
│       │   ├── ToolDispatcher.java          # 工具调度器（PlanMode/Storm/Hooks 门控）
│       │   └── ToolSchemaFlattener.java     # Schema 展平（减少 token 开销）
│       ├── util/
│       │   └── ONodeUtil.java               # Snack4 ONode → Map 转换工具
│       ├── workspace/
│       │   └── WorkspaceManager.java        # 工作区管理器（多工作区隔离 + 会话目录）
│       └── test/java/.../bin/               # 单元测试
│           ├── agent/
│           │   ├── ContextFoldingTest.java
│           │   ├── ConversationContextTest.java
│           │   ├── MessageHealerTest.java
│           │   ├── PromptPrefixTest.java
│           │   ├── ReasonBreakerTest.java
│           │   ├── ScavengerTest.java
│           │   └── StormBreakerTest.java
│           ├── session/
│           │   └── JsonlSessionStoreTest.java
│           ├── skill/
│           │   ├── SkillStoreV2Test.java
│           │   └── SkillV2ImplTest.java
│           └── tool/
│               ├── ToolDispatcherTest.java
│               └── ToolRegistryTest.java
│
├── agent4j-web/                             # ★ Web REST API 模块（agent4j-web）
│   ├── pom.xml
│   └── src/main/java/site/sorghum/agent4j/web/
│       ├── Agent4jWebApp.java               # ★ Web 入口（Solon-Web，端口 8080）
│       ├── controller/                      # REST API 控制器
│       │   ├── ChatController.java          # 聊天 API（POST 同步 + GET SSE 流式）
│       │   ├── SessionController.java       # 会话管理 API（列表/切换/删除/归档）
│       │   ├── AgentController.java         # Agent 状态 API（模型/工具/工作区切换）
│       │   ├── ToolController.java          # 工具管理 API（列出/执行/禁用）
│       │   └── ConfigController.java        # 配置 API（查看/更新配置 + 用量统计）
│       ├── service/
│       │   ├── AgentService.java            # Agent 单例服务（串行锁保证线程安全）
│       │   ├── SseEmitter.java              # SSE 流式输出工具类
│       │   └── ApiAgentOutPut.java          # AgentOutput → SSE 事件桥接
│       └── model/
│           ├── ApiResponse.java             # 统一响应封装
│           ├── ChatRequest.java             # 聊天请求体（message + sessionId）
│           └── ToolExecuteRequest.java      # 工具执行请求体
│
├── agent4j-front/                           # ★ Vue 3 前端模块（agent4j-front）
│   ├── package.json                         # Vite + Vue 3 + Pinia + Vue Router
│   ├── vite.config.js
│   ├── index.html
│   ├── src/
│   │   ├── App.vue                          # 根组件
│   │   ├── main.js                          # 入口（createApp + router + pinia）
│   │   ├── views/                           # 页面
│   │   │   ├── Chat.vue                     # 聊天主页面（终端 + Markdown 渲染）
│   │   │   ├── Home.vue                     # 首页
│   │   │   ├── Sessions.vue                 # 会话列表
│   │   │   ├── Settings.vue                 # 设置页面
│   │   │   ├── Tools.vue                    # 工具列表
│   │   │   ├── Help.vue                     # 帮助页面
│   │   │   └── NotFound.vue                 # 404 页面
│   │   ├── components/                      # 组件
│   │   │   ├── Sidebar.vue                  # 侧边栏（会话列表/导航）
│   │   │   ├── Composer.vue                 # 消息输入框（命令选择 + 发送）
│   │   │   ├── StatusBar.vue                # 状态栏（模型/工作区/连接状态）
│   │   │   ├── TabBar.vue                   # 标签栏
│   │   │   ├── TitleBar.vue                 # 标题栏
│   │   │   └── UsagePanel.vue               # Token 用量面板
│   │   ├── composables/
│   │   │   └── useTerminal.js               # 终端组合式函数
│   │   ├── router/
│   │   │   └── index.js                     # Vue Router 配置
│   │   ├── services/
│   │   │   └── api.js                       # API 服务（axios/fetch）
│   │   ├── stores/
│   │   │   └── app.js                       # Pinia 全局状态
│   │   ├── assets/
│   │   │   ├── styles/main.css              # 全局样式
│   │   │   ├── logo.png                     # Logo（光栅）
│   │   │   └── logo.svg                     # Logo（矢量）
│   │   └── utils/
│   │       └── helpers.js                   # 工具函数
│   └── public/
│       ├── favicon.png
│       └── favicon.svg
│
├── agent4j-tauri/                           # ★ Tauri 桌面端模块（agent4j-tauri）
│   ├── package.json                         # Tauri CLI 配置
│   └── src-tauri/
│       ├── Cargo.toml                       # Rust 依赖
│       ├── tauri.conf.json                  # Tauri 配置（窗口/权限/图标）
│       ├── src/
│       │   ├── main.rs                      # Rust 入口（Tauri Builder）
│       │   └── lib.rs                       # Rust 命令注册
│       ├── icons/                           # 应用图标（多尺寸）
│       │   ├── icon.png / icon.ico
│       │   ├── 32x32.png / 128x128.png / 128x128@2x.png
│       └── capabilities/
│           └── default.json                 # Tauri v2 权限配置
│
├── agent4j-tui/                             # TUI 模块（预留）
│
├── demo/                                    # 演示 HTML 文件
│   ├── agent4j_landing.html                 # 项目官网介绍页
│   ├── community_life.html
│   ├── jiaxing.html
│   └── suzhou_travel.html
│
├── example-skills/                          # 示例 Skill 定义
│   ├── README.md
│   ├── explore/SKILL.md                     # 探索代码库 Skill
│   └── java-conventions/SKILL.md            # Java 编码规范 Skill
│
└── logs/
    ├── agent4j.log                          # 当前运行日志
    └── agent4j_2026-05-26_0.log             # 归档日志
```

**统计**: 208 个文件，~3.5 MB，其中 Java 源文件 ~90 个，Vue 源文件 ~15 个。

---

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| **Java** | 17 | 开发语言 |
| **Maven** | 3.x | 构建工具 & 多模块管理 |
| **Solon** | 3.9.6 | IoC 容器 / AOP / Web（轻量级替代 Spring Boot） |
| **Snack4** | 4.0.49 | JSON 解析与序列化 |
| **Lombok** | 1.18.34 | 样板代码简化（@Slf4j, @Getter, record 等） |
| **JUnit Jupiter** | 5.10.3 | 单元测试 |
| **Logback** | via Solon | 高性能日志实现 |
| **SLF4J** | 1.7.36 | 日志门面 |
| **Vue 3** | latest | 前端框架（agent4j-front） |
| **Vite** | latest | 前端构建工具 |
| **Pinia** | latest | 状态管理 |
| **Vue Router** | latest | 前端路由 |
| **Tauri** | v2 | 桌面端框架（Rust 后端 + WebView） |
| **Rust** | stable | Tauri 后端语言 |

---

## 架构设计

### 分层架构

```
┌──────────────────────────────────────────────────────────┐
│                 入口层 · Presentation                      │
│  CLI: Agent4jApp (Scanner 交互循环)                       │
│  Web: Agent4jWebApp (REST API, Solon-Web)                 │
│  Desktop: Tauri (Rust WebView + Vue)                      │
└────────────────────────┬─────────────────────────────────┘
                         │
┌────────────────────────▼─────────────────────────────────┐
│              核心层 · Core Engine                          │
│                                                           │
│  Agent4jAgent (工厂 · Builder 模式)                        │
│    ├── 组装 ModelClient + ToolRegistry + SessionService    │
│    ├── 自动发现 @Component AgentTool Bean                  │
│    ├── 加载项目文档 (agent4j.md / CLAUDE.md)                │
│    └── 构建缓存优先 PromptPrefix                           │
│                                                           │
│  AgentLoop (推理循环引擎)                                   │
│    ├── prepareMessages  → 构建 + Heal + Fold + 工具指引    │
│    ├── streamLLM        → 流式调用 (CountDownLatch)        │
│    ├── scavengeToolCalls→ 回收丢失的调用                   │
│    ├── executeToolCalls → 并行执行 (CompletableFuture)     │
│    ├── handleSelfCorrection → 风暴自愈                     │
│    └── HITL 拦截        → 写入前审批 + 沙箱越界审批        │
│                                                           │
│  ConversationContext (会话上下文 · 内存 + 持久化)           │
│    ├── 内存历史: List<Map<String,Object>>                  │
│    ├── PromptPrefix: 不可变前缀 (缓存优先)                  │
│    └── SessionStore: JSONL 持久化                          │
│                                                           │
│  ToolDispatcher (工具调度器)                                │
│    ├── PlanMode 门控 (写入工具拦截)                         │
│    ├── StormBreaker 检查 (滑动窗口去重)                     │
│    ├── Pre/Post Hooks (拦截器链)                           │
│    └── 参数注入 (sessionId)                                │
└────────────────────────┬─────────────────────────────────┘
                         │
┌────────────────────────▼─────────────────────────────────┐
│              基础层 · Infrastructure                       │
│                                                           │
│  ModelClient      ToolRegistry      SessionStore          │
│  (策略接口)        (工具注册中心)     (持久化接口)           │
│  │                 │                  │                    │
│  └─HttpModelClient └─LinkedHashMap    └─JsonlSessionStore │
│    (HTTP/SSE)        (名称索引)         (JSONL + ReentrantLock)│
│                                                           │
│  JobRegistry       MemoryService      WorkspaceManager    │
│  (进程注册表)       (~/.agent4j/memory/) (多工作区隔离)     │
│                                                           │
│  SkillStoreV2      WorkspaceIndex     CommandAllowlist     │
│  (Skill 存储)       (文件索引缓存)      (命令白名单)         │
└──────────────────────────────────────────────────────────┘
```

### 核心推理循环流程

```
用户输入 (chat)
    │
    ▼
AgentLoop.run(message)
    │
    ├── HITL 恢复? → resumeAfterHITL(approved/denied)
    │
    ├── 1. ctx.addUser(msg)              ← 追加用户消息 + 持久化
    │
    ├── for each step (until text response):
    │   │
    │   ├── a. prepareMessages()          ← buildMessages + MessageHealer.heal()
    │   │      └── 预检折叠 (prompt_tokens > 80% 上下文窗口)
    │   │         └── ContextFolding.fold() → ctx.compact()
    │   │      └── 注入工具使用指引 (edit_file/multi_edit/glob/grep/run_command)
    │   │
    │   ├── b. streamLLM()               ← client.chatStream(SSE)
    │   │      ├── onReasoningDelta()     ← ReasonBreaker 流式循环检测
    │   │      ├── onContentDelta()       ← 实时推送
    │   │      ├── onToolCalls()          ← 收集工具调用
    │   │      └── onUsage()              ← Token 用量回调
    │   │
    │   ├── c. scavengeToolCalls()        ← Scavenger 从 reasoning 回收
    │   │
    │   ├── d. 无 tool_calls → handleTextResponse() → 返回文本
    │   │
    │   ├── e. HITL 拦截 → interceptForHITL() → 暂停等待审批
    │   │
    │   └── f. executeToolCalls()         ← 并行执行
    │          ├── dispatcher.dispatch()  ← PlanMode / Storm / Hooks 门控
    │          │   └── (HitlRequiredException → 沙箱越界 HITL)
    │          ├── ctx.addAssistant()
    │          ├── ctx.addToolResult()    ← 写入工具结果
    │          │
    │          └── handleSelfCorrection() ← 全部被 storm 抑制? → 有限次自愈
    │              └── MAX 5 次后返回 fallback
    │
    └── 返回最终 assistant content
```

### 消息流转

```
用户输入 "帮我重构 UserService"
    │
    ▼
[user] "帮我重构 UserService"
    │ (注入工具指引)
    ▼
LLM API (流式 SSE)
    │
    ├─> reasoning: "我需要先阅读 UserService.java..."
    ├─> tool_call: read_file("src/UserService.java")
    │
    ▼ (并行)
ToolDispatcher.dispatch("read_file", args)
    │
    ├─ PlanMode? → 否
    ├─ StormBreaker.inspect() → 通过
    └─ ToolFn.call(args) → 返回文件内容
    │
    ▼
[assistant] (tool_calls: [read_file])
[tool]      (tool_call_id=xxx, content="{文件内容}")
    │
    ▼
LLM API → "我来分析一下... 需要修改 greet 方法"
    │
    ├─> tool_call: edit_file(path, search, replace)
    │
    ▼ (并行)
[assistant] (tool_calls: [edit_file])
[tool]      (tool_call_id=yyy, content="[OK] edit applied")
    │
    ▼
LLM API → "已完成 UserService 重构，greet 方法已更新。"
    │
    ▼
返回给用户
```

### 关键设计模式

| 模式 | 位置 | 说明 |
|------|------|------|
| **不可变前缀** | `PromptPrefix` | system prompt + tool specs 跨 turn 不变，实现 DeepSeek 前缀缓存命中 |
| **工厂模式** | `Agent4jAgent.Builder` | 链式 Builder 构建复杂对象，支持轻量级构建共享组件 |
| **策略模式** | `ModelClient` 接口 | 可替换的 LLM API 实现（HTTP/SSE） |
| **观察者模式** | `AgentLoopListener` | 推理/工具调用/用量的事件回调 |
| **模板方法** | `AgentTool` | 子类实现 getName/getDescription/getParameters/execute |
| **命令模式** | `ChatCommand` + `ChatCommandRegistry` | "/" 命令通过 Solon IoC 自动注册和匹配 |
| **断路器** | `StormBreaker` | 滑动窗口检测重复工具调用（W=6, T=3）+ JSON 指纹去重 |
| **断路器** | `ReasonBreaker` | 流式检测推理死循环 |
| **并行执行** | `AgentLoop.executeToolCalls()` | CompletableFuture.supplyAsync 并行分发 |
| **CAS 替代锁** | `CountDownLatch` / `AtomicBoolean` | 流式等待无忙等待 |
| **缓冲写入** | `JsonlSessionStore` | BufferedWriter 保持打开 + flush() + 30s 定时刷入 |
| **单一入口** | `Agent4jAgent.chat()` | 命令/消息统一路由，避免逻辑重复 |
| **沙箱旁路** | `ToolContext.enableSandboxBypass()` | ThreadLocal 控制，HITL 审批后跳过越界检查 |
| **享元模式** | `PromptPrefix` | 共享 system prompt + tool defs，多个 Agent 实例复用 |

---

## 全部工具列表

### 核心工具（agent4j-tool · 5 个）

| 工具名 | 只读 | 风暴豁免 | 说明 |
|--------|------|----------|------|
| `run_command` | ❌ | ❌ | Shell 命令执行（自解析 argv，跨平台；支持管道和重定向） |
| `file` | ❌ | ❌ | 文件系统操作（create_dir/create_file/delete_file/delete_dir/move/copy/stat） |
| `glob` | ✅ | ✅ | glob 文件名匹配（基于 WorkspaceIndex 缓存，毫秒级） |
| `grep` | ✅ | ✅ | 正则内容搜索（自动跳过二进制/大文件/denylist 目录） |
| `tree` | ✅ | ✅ | 目录树生成（maxDepth 控制深度，0=根，-1=无限） |

### 内置工具（agent4j-tool 子包 · 23 个）

#### 文件操作（6 个）

| 工具名 | 只读 | 风暴豁免 | 参数 | 说明 |
|--------|------|----------|------|------|
| `read_file` | ✅ | ✅ | path, head?, tail?, range? | 完整读取文件内容（>100 MiB 拒绝） |
| `edit_file` | ❌ | ❌ | path, search, replace | SEARCH/REPLACE 单文件编辑（search 必须唯一） |
| `write_file` | ❌ | ❌ | path, content | 创建/覆盖文件（自动创建父目录） |
| `multi_edit` | ❌ | ❌ | edits[{path,search,replace}] | 批量原子编辑（全验证→全写入→失败回滚） |
| `copy_file` | ❌ | ❌ | source, destination | 复制文件或目录 |
| `get_file_info` | ✅ | ✅ | path | 查看文件/目录元信息（JSON: type/size/mtime） |

#### 代码分析（3 个）

| 工具名 | 只读 | 风暴豁免 | 参数 | 说明 |
|--------|------|----------|------|------|
| `get_symbols` | ✅ | ✅ | path | tree-sitter AST 顶层符号大纲 |
| `find_in_code` | ✅ | ✅ | path, name | 在文件中查找标识符（AST 过滤） |
| `java_source` | ✅ | ✅ | className, jarKeyword? | 通过全限定类名定位 Java 源码 |

#### 网络（2 个）

| 工具名 | 只读 | 风暴豁免 | 参数 | 说明 |
|--------|------|----------|------|------|
| `web_search` | ✅ | ✅ | query | DuckDuckGo Lite 搜索（返回标题/URL/摘要） |
| `web_fetch` | ✅ | ✅ | url | 下载 URL 并提取可视化文本（去标签去广告） |

#### 记忆（3 个）

| 工具名 | 只读 | 风暴豁免 | 参数 | 说明 |
|--------|------|----------|------|------|
| `remember` | ❌ | ❌ | name, type, scope, description, content, priority? | 持久化保存记忆（~/.agent4j/memory/） |
| `recall_memory` | ✅ | ✅ | name | 读取完整记忆内容 |
| `forget` | ❌ | ❌ | name | 删除记忆 |

#### 后台作业（5 个）

| 工具名 | 只读 | 风暴豁免 | 参数 | 说明 |
|--------|------|----------|------|------|
| `run_background` | ❌ | ❌ | command, cwd?, waitSec? | 启动后台进程并分离 |
| `job_output` | ✅ | ✅ | jobId, since?, tailLines? | 读取作业输出（支持增量轮询） |
| `wait_for_job` | ❌ | ❌ | jobId, timeoutMs?, waitFor? | 阻塞等待作业完成 |
| `stop_job` | ❌ | ❌ | jobId | 停止作业（SIGTERM→SIGKILL） |
| `list_jobs` | ✅ | ✅ | (无参数) | 列出所有后台作业及状态 |

#### 计划与交互（5 个）

| 工具名 | 只读 | 风暴豁免 | 参数 | 说明 |
|--------|------|----------|------|------|
| `submit_plan` | ❌ | ❌ | summary?, plan, steps[{id?,title?,action?}] | 提交 Markdown 执行计划供审查 |
| `revise_plan` | ❌ | ❌ | reason, remainingSteps | 修订进行中计划的剩余步骤 |
| `mark_step_complete` | ❌ | ❌ | stepId, result?, evidence? | 标记计划步骤完成 |
| `ask_choice` | ❌ | ❌ | question, options, allowCustom? | 箭头键选择菜单（2-6 选项） |
| `todo_write` | ❌ | ❌ | todos[{status,content,activeForm?}] | 任务跟踪列表（pending/in_progress/completed） |

#### Skill 系统（3 个）

| 工具名 | 只读 | 风暴豁免 | 参数 | 说明 |
|--------|------|----------|------|------|
| `install_skill` | ❌ | ❌ | name, description, body, scope?, runAs? | 创建/保存 skill（inline/subagent 模式） |
| `run_skill` | ✅ | ✅ | name, arguments? | 调用用户定义的 skill |
| `task` | ❌ | ❌ | name, arguments? | 创建隔离子代理处理复杂多步任务 |

---

## 会话生命周期

```
SessionService
  │
  ├── 启动: loadOrCreate() → 恢复最近会话
  │      └── switchTo() → 暂不创建文件（延迟到首次 append）
  │
  ├── 运行时: ctx.addUser/addAssistant/addToolResult()
  │      └── persist() → SessionStore.append()
  │           └── ensureWriter() → 首次写入时创建 .jsonl
  │           └── BufferedWriter.write + newLine
  │      ├── flush() → 显式刷入（退出/compact/每轮对话后）
  │      └── 定时刷入 → ScheduledExecutorService 每 30s 自动 flush()
  │
  ├── /compact: 语义折叠 → ContextFolding.fold()
  │      ├── 头部 → LLM 摘要（单次 API 调用）
  │      ├── 尾部保留（~80KB）
  │      └── ctx.compact() → rewrite() 回写 JSONL
  │
  ├── /retry / /rewind N: 回退消息
  │      └── ctx.retryLastUser() / ctx.rewindToUser(N)
  │           └── rewrite() 回写 JSONL
  │
  ├── /new: 归档当前 → 创建新会话
  │      ├── flush() + closeWriter()
  │      ├── archive() → 重命名为 xxx__archive_yyyymmddHHmmss.jsonl
  │      └── newSessionName() → 不创建空文件
  │
  └── 退出: flush() → saveUsage() → shutdown()
         └── stopPeriodicFlush() + closeWriter()
```

**文件位置**: `~/.agent4j/`（支持工作区隔离：`~/.agent4j/workspace/{hash}/sessions/`）
- `config.json` — LLM 配置（baseUrl/apiKey/model/workspaceDir/editMode/reasoningEffort/lang/hitl/disabledTools/blockedPaths）
- `sessions/*.jsonl` — 会话消息（JSONL 格式，每行一条消息）
- `sessions/*.usage` — Token 用量统计（prompt/completion/cacheHit/cacheMiss/lastPromptTokens）
- `sessions/*.meta` — 会话元信息（标题等）
- `memory/*.json` — 持久化记忆
- `skills/` — 用户自定义 Skill（project: `.agent4j/skills/`，global: `~/.agent4j/skills/`）

---

## 运行方式

### 前置条件

- JDK 17+
- Maven 3.x
- 一个兼容 OpenAI API 的 LLM 端点（如 DeepSeek / Ollama / OpenAI）

### 配置文件

首次启动自动创建 `~/.agent4j/config.json`：

```json
{
  "baseUrl": "https://api.deepseek.com/v1",
  "apiKey": "sk-your-api-key",
  "model": "deepseek-v4-flash",
  "workspaceDir": ".",
  "editMode": "auto",
  "reasoningEffort": "max",
  "lang": "ZH",
  "hitl": false,
  "disabledTools": [],
  "blockedPaths": [],
  "availableModels": ["deepseek-v4-flash", "gpt-4o", "claude-3.5-sonnet"]
}
```

环境变量覆盖：`OPENAI_BASE_URL`、`OPENAI_API_KEY`、`MODEL`、`AGENT4J_DISABLED_TOOLS`、`AGENT4J_BLOCKED_PATHS`。

### 编译

```bash
# 完整编译
mvn compile

# 仅编译核心模块
mvn compile -pl agent4j-bin
```

### 运行

```bash
# CLI 模式
mvn exec:java -pl agent4j-bin -Dexec.mainClass="site.sorghum.agent4j.bin.Agent4jApp"

# Web 模式
mvn exec:java -pl agent4j-web -Dexec.mainClass="site.sorghum.agent4j.web.Agent4jWebApp"

# 直接运行
java -cp agent4j-bin/target/classes:agent4j-tool/target/classes \
  site.sorghum.agent4j.bin.Agent4jApp
```

### 交互命令（CLI）

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
| `/load N` | 加载指定会话（按列表序号） |
| `/init` | 自动分析项目生成 agent4j.md |
| `/hitl` | 切换 HITL 模式（工具执行前需审批） |
| `/agree` | 批准 HITL 待执行的工具调用 |
| `/deny` | 拒绝 HITL 待执行的工具调用 |
| `/exit` | 退出程序（别名 `/quit`） |

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
  │     ├── 无模块间依赖
  │     └── 外部: solon-lib, lombok, junit-jupiter
  │
  ├── agent4j-bin      (可执行入口 · CLI)
  │     ├── 依赖: agent4j-tool
  │     └── 外部: solon-lib, solon-web, solon-logging-logback, snack4, lombok
  │
  ├── agent4j-web      (Web REST API)
  │     ├── 依赖: agent4j-bin
  │     └── 外部: solon-web, solon-logging-logback, snack4, lombok
  │
  ├── agent4j-front    (Vue 3 前端)
  │     └── 外部: Vue 3, Vite, Pinia, Vue Router
  │
  └── agent4j-tauri    (Tauri 桌面端)
        └── 外部: Tauri v2, Rust
```

---

## 项目亮点

1. **缓存优先设计** — `PromptPrefix` 不可变前缀 + 稳定 tool specs 排序，最大化 DeepSeek 前缀缓存命中
2. **并行执行** — CompletableFuture.allOf 并行分发工具调用，CountDownLatch 无忙等待
3. **风暴断路器 × 推理断路器** — StormBreaker（滑动窗口 + JSON 指纹）+ ReasonBreaker（流式循环检测），双保险防止死循环
4. **消息自愈** — MessageHealer 发送前自动修复（配对 tool_calls、补 reasoning、截断过长结果）
5. **上下文折叠** — ContextFolding 用一次 LLM 调用将旧消息压缩为摘要，替代机械截断
6. **工具调用回收** — Scavenger 从 reasoning_content 中提取因 API 格式问题遗漏的 tool_calls
7. **子代理隔离** — SubAgent 创建独立循环执行复杂任务，排除递归 spawn 和用户交互工具
8. **HITL 双模式** — 普通 HITL（写入操作审批）+ 沙箱越界 HITL（路径穿越强制审批，独立管道）
9. **原子批量编辑** — multi_edit 全验证→全写入→失败回滚，保证跨文件数据一致性
10. **缓冲写入与安全** — BufferedWriter 保持打开 + 显式 flush + 30s 定时刷入 + ReentrantLock
11. **命令模式扩展** — 新增命令只需实现 ChatCommand 接口 + @Component，IoC 自动收集
12. **序列化安全** — Snack4 ONode 替代手工 JSON 拼接，避免 XSS/注入
13. **路径穿越防护** — resolveSafe() 严格校验 + 屏蔽目录列表 + 沙箱越界 HITL
14. **Skill 系统 V2** — 用户可定义可复用的 Skill playbook，支持 inline 和 subagent 两种运行模式
15. **多端交付** — CLI / Web REST API / Vue 3 前端 / Tauri 桌面端，四端统一核心

---

*本文档由 Agent4j 自动分析生成 · 2025 年 7 月*
