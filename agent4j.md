# Agent4j — Java AI Agent 框架

> 参考 **Reasonix 架构**的纯 Java AI 代理框架，提供推理循环、工具调用、会话管理、流式输出等完整能力。
> 作者: Sorghum · 许可证: 未指定

---

## 项目概述

Agent4j 是一个 Java 8 实现的 AI 代码助手框架，
将 LLM（大语言模型）与可扩展的工具系统结合，形成一个自主工作的编码代理。

| 属性 | 值 |
|------|-----|
| **全称** | Agent4j — Code Agent for Java |
| **版本** | 1.0-SNAPSHOT |
| **Java 目标** | 8（Corretto 1.8） |
| **构建工具** | Maven 多模块 |
| **IoC 框架** | Solon 3.9.6 |
| **包名** | `site.sorghum.agent4j` |
| **远程仓库** | https://gitee.com/ezdemo/agent4j.git |

### 核心能力

- ✅ 对话式推理循环（多轮上下文保持）
- ✅ OpenAI 兼容 API 流式调用（SSE）
- ✅ 工具注册/调度/并行执行
- ✅ 计划模式（Plan Mode — 只读探索）
- ✅ 风暴断路器（StormBreaker — 防重复调用死循环）
- ✅ 消息自愈（MessageHealer — 发送前修复）
- ✅ 上下文折叠（ContextFolding — 长对话语义压缩）
- ✅ 工具调用回收（Scavenger — 从 reasoning_content 回收丢失的调用）
- ✅ 会话持久化（JSONL 文件存储，缓冲写入 + 显式刷入 + 定时自动刷入）
- ✅ Token 用量追踪与缓存统计
- ✅ 子代理隔离（SubAgent — 复杂任务委派）
- ✅ 后台作业管理
- ✅ 记忆服务（持久化键值记忆）
- ✅ 网络搜索与网页抓取

---

## 目录结构

```
agent4j/
├── pom.xml                              # 父 POM，多模块管理
├── agent4j.md                           # 本文件（项目文档）
├── ANALYSIS.md                          # 项目分析报告
├── .gitignore
│
├── agent4j-tool/                        # 核心工具库模块
│   ├── pom.xml
│   └── src/main/java/site/sorghum/agent4j/tool/
│       ├── AgentTool.java               # 工具抽象基类（顶层契约）
│       ├── ToolContext.java             # 工具执行上下文（参数/根目录/API 信息）
│       ├── ToolParameter.java           # 工具参数定义
│       ├── ToolResult.java              # 工具执行结果封装
│       ├── file/
│       │   └── FileTool.java            # 文件系统操作工具（create/move/copy/delete/stat）
│       ├── terminal/
│       │   └── TerminalTool.java        # 终端命令执行工具
│       └── search/
│           ├── WorkspaceIndex.java      # 工作区文件索引（缓存+增量刷新）
│           ├── GrepTool.java            # 内容搜索工具（正则）
│           ├── GlobTool.java            # 文件名匹配工具（glob）
│           ├── TreeTool.java            # 目录树生成工具
│           ├── SearchMatch.java         # 搜索结果数据结构
│           └── FileMeta.java            # 文件元数据
│
├── agent4j-bin/                         # 可执行入口模块
│   ├── pom.xml
│   ├── src/main/resources/app.yml       # Solon 应用配置
│   └── src/main/java/site/sorghum/agent4j/bin/
│       ├── Agent4jApp.java              # ★ 应用主入口（main）
│       ├── app/
│       │   └── AppConfig.java           # Solon IoC Bean 配置
│       ├── agent/
│       │   ├── Agent4jAgent.java        # ★ 代理工厂（组装客户端+工具→循环）
│       │   ├── AgentLoop.java           # ★ 核心推理循环引擎
│       │   ├── AgentLoopListener.java   # 循环事件监听器接口
│       │   ├── ConversationContext.java # 会话上下文（消息历史管理）
│       │   ├── PromptPrefix.java        # 不可变前缀（缓存优先）
│       │   ├── ContextFolding.java      # 上下文折叠（语义摘要）
│       │   ├── MessageHealer.java       # 消息修复器（发送前修复）
│       │   ├── StormBreaker.java        # 风暴断路器（防重复调用死循环）
│       │   ├── Scavenger.java           # 工具调用回收（从 reasoning 中找回）
│       │   └── SubAgent.java            # 子代理（隔离执行）
│       ├── config/
│       │   └── Agent4jConfig.java       # 配置加载（~/.agent4j/config.json）
│       ├── model/
│       │   ├── ModelClient.java         # LLM API 客户端接口
│       │   └── HttpModelClient.java     # HTTP 实现（OpenAI 兼容 API）
│       ├── tool/
│       │   ├── ToolDef.java             # 工具定义（名称/描述/参数/执行函数）
│       │   ├── ToolRegistry.java        # 工具注册中心
│       │   ├── ToolDispatcher.java      # 工具调度器（PlanMode / Storm / Hooks）
│       │   ├── ToolSchemaFlattener.java # Schema 展平（减少 token 开销）
│       │   └── FileEdit.java            # 文件读写编辑引擎（SEARCH/REPLACE）
│       ├── session/
│       │   ├── SessionStore.java        # 会话持久化接口
│       │   ├── JsonlSessionStore.java   # JSONL 文件实现
│       │   └── SessionService.java      # 会话管理服务
│       ├── job/
│       │   └── JobRegistry.java         # 后台作业注册表
│       ├── builtin/
│       │   ├── ReadFileTool.java        # 读取文件
│       │   ├── EditFileTool.java        # SEARCH/REPLACE 编辑
│       │   ├── WriteFileTool.java       # 创建/覆盖文件
│       │   ├── MultiEditTool.java       # 批量原子编辑
│       │   ├── CopyFileTool.java        # 复制文件/目录
│       │   ├── GetFileInfoTool.java     # 文件元信息查看
│       │   ├── GetSymbolsTool.java      # 符号大纲提取
│       │   ├── FindInCodeTool.java      # 标识符查找
│       │   ├── JavaSourceTool.java      # Java 源码定位
│       │   ├── WebSearchTool.java       # 网络搜索
│       │   ├── WebFetchTool.java        # 网页抓取
│       │   ├── RememberTool.java        # 保存记忆
│       │   ├── RecallMemoryTool.java    # 读取记忆
│       │   ├── ForgetTool.java          # 删除记忆
│       │   ├── RunBackgroundTool.java   # 启动后台进程
│       │   ├── JobOutputTool.java       # 读取作业输出
│       │   ├── WaitForJobTool.java      # 等待作业完成
│       │   ├── StopJobTool.java         # 停止作业
│       │   ├── ListJobsTool.java        # 列出作业
│       │   ├── SubmitPlanTool.java      # 提交计划
│       │   ├── RevisePlanTool.java      # 修订计划
│       │   ├── MarkStepCompleteTool.java# 标记步骤完成
│       │   ├── AskChoiceTool.java       # 用户选择菜单
│       │   ├── TodoWriteTool.java       # 任务跟踪列表
│       │   └── TaskTool.java            # 子代理任务
│       └── service/
│           ├── FileSystemService.java   # 文件系统服务
│           ├── JobService.java          # 后台作业服务
│           ├── WebService.java          # 网络搜索与抓取
│           ├── MemoryService.java       # 记忆存储服务
│           ├── PlanService.java         # 计划管理服务
│           ├── InteractionService.java  # 用户交互服务
│           └── CodeQueryService.java    # 代码查询服务
│
├── agent4j-web/                         # Web REST API 模块
│   ├── pom.xml
│   └── src/main/java/site/sorghum/agent4j/web/
│       ├── Agent4jWebApp.java           # ★ Web 入口（Solon）
│       ├── controller/
│       │   ├── ChatController.java      # 聊天 API（同步+SSE 流式）
│       │   ├── SessionController.java   # 会话管理 API
│       │   ├── AgentController.java     # Agent 状态查询 API
│       │   ├── ToolController.java      # 工具管理 API
│       │   └── ConfigController.java    # 配置与用量 API
│       ├── service/
│       │   ├── AgentService.java        # Agent 单例服务（串行锁保证线程安全）
│       │   ├── SseEmitter.java          # SSE 流式输出工具类
│       │   └── ApiAgentOutPut.java      # AgentOutput → SSE 桥接
│       └── model/
│           ├── ApiResponse.java         # 统一响应封装
│           ├── ChatRequest.java         # 聊天请求体
│           └── ToolExecuteRequest.java  # 工具执行请求体
│
├── demo/                                # 演示 HTML 文件
│   ├── community_life.html
│   ├── jiaxing.html
│   └── suzhou_travel.html
│
└── logs/
    └── agent4j.log                      # 运行日志
```

**统计**: 82 个文件，~541 KB，其中 Java 源文件 62 个。

---

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| **Java** | 8 (Corretto 1.8) | 开发语言 |
| **Maven** | 3.x | 构建工具 |
| **Solon** | 3.9.6 | IoC 容器 / AOP / Web（轻量级替代 Spring Boot） |
| **Snack4** | 4.0.49 | JSON 解析与序列化 |
| **Snack4-jsonpath** | 4.0.49 | JSONPath 查询 |
| **Lombok** | 1.18.34 | 代码简化（@Getter @AllArgsConstructor 等） |
| **JUnit Jupiter** | 5.10.3 | 单元测试 |
| **Logback** | via Solon | 日志实现 |
| **SLF4J** | 1.7.36 | 日志门面 |
| **Solon Maven Plugin** | 3.9.6 | 打包与启动 |

---

## 架构设计

### 分层架构

```
┌─────────────────────────────────────────────────────┐
│                    Agent4jApp (main)                 │
│             交互式命令行界面 / 循环入口                │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│              Agent4jAgent (工厂 + 外观)               │
│  组装 ModelClient + ToolRegistry + SessionService    │
└──────┬───────────────────────────────┬──────────────┘
       │                               │
┌──────▼──────────┐    ┌───────────────▼──────────────┐
│   AgentLoop     │    │     ToolDispatcher            │
│   推理循环       │    │    PlanMode / Storm / Hooks   │
│   LLM ↔ Tool    │    │    schema 展平 / 截断          │
└──────┬──────────┘    └───────────────┬──────────────┘
       │                               │
┌──────▼──────┐   ┌───────────────────▼───────────────┐
│ ModelClient │   │          ToolRegistry              │
│ API 客户端   │   │  注册 / 查询 / toOpenAiTools()    │
│ (HTTP SSE)  │   │                                    │
└─────────────┘   └───────────────────────────────────┘
                         │
              ┌──────────┴──────────┐
              │                     │
    ┌─────────▼──────┐   ┌─────────▼──────┐
    │ agent4j-tool   │   │ agent4j-bin    │
    │ 核心工具集      │   │ 内置工具集      │
    │ File / Terminal│   │ 文件编辑/搜索   │
    │ Grep / Glob    │   │ 网络/记忆/计划  │
    │ Tree           │   │ 作业/代码查询   │
    └────────────────┘   └────────────────┘
```

### 核心流程

```
用户输入
    │
    ▼
AgentLoop.run(message)
    │
    ├── 1. ctx.addUser(msg)            ← 追加用户消息
    ├── 2. ctx.buildMessages()          ← 组装 system prefix + history
    ├── 3. MessageHealer.heal()         ← 修复消息（截断/配对/补 reasoning）
    ├── 4. ContextFolding.fold()        ← 超长时折叠旧消息
    ├── 5. client.chatStream()          ← 流式调用 LLM API
    │       ├── onReasoningDelta()      ← 思考过程实时打印
    │       ├── onContentDelta()        ← 内容实时打印
    │       └── onToolCalls()           ← 工具调用收集
    │
    ├── 6. Scavenger.scavenge()         ← 从 reasoning 回收丢失的调用
    │
    ├── 7. 无 tool_calls → 返回文本回复
    │
    └── 8. 有 tool_calls
            ├── StormBreaker.inspect()  ← 检查重复调用
            ├── dispatch() 并行执行     ← CompletableFuture.allOf
            ├── ctx.addAssistant()      ← 助理消息
            └── ctx.addToolResult()     ← 工具结果
                  │
                  └── 回到步骤 2（继续循环）
```

### 关键设计模式

| 模式 | 位置 | 说明 |
|------|------|------|
| **不可变前缀** | `PromptPrefix` | System prompt + tool specs 跨 turn 不变，实现 DeepSeek 前缀缓存命中 |
| **单一职责** | 多文件拆分 | ToolRegistry / ToolDispatcher / ToolSchemaFlattener 职责分离 |
| **工厂模式** | `Agent4jAgent.Builder` | 链式 Builder 构建复杂对象 |
| **策略模式** | `ModelClient` 接口 | 可替换的 LLM API 实现 |
| **观察者模式** | `AgentLoopListener` | 推理/工具调用/用量的事件回调 |
| **模板方法** | `AgentTool` | 子类实现 getName/getDescription/getParameters/execute |
| **断路器** | `StormBreaker` | 滑动窗口检测重复工具调用（窗口=6，阈值=3） |
| **并行执行** | `AgentLoop` | CompletableFuture.allOf 并行分发工具调用 |
| **CAS 替代锁** | `CountDownLatch` / `AtomicBoolean` | 流式等待无忙等待 |
| **缓冲写入** | `JsonlSessionStore` | BufferedWriter 保持打开 + flush() 显式刷入 + 定时自动刷入，减少 IO 次数 |
| **命令模式** | `ChatCommandRegistry` + `ChatCommand` | "/" 命令通过 Solon IoC 自动注册和匹配，新增命令只需实现接口并标注 @Component |
| **单一入口** | `Agent4jAgent.chat()` | 用户输入（普通消息和 "/" 命令）统一经过此入口，由 ChatCommandRegistry 自动路由分发 |

### 会话生命周期

```
SessionService (JsonlSessionStore)
  │
  ├── 启动: loadOrCreate() → 恢复最近会话
  │      └── openWriter() → 打开 BufferedWriter（保持打开，避免频繁 IO）
  │
  ├── 运行时: append() → 写入 BufferedWriter 缓冲区
  │      ├── flush() → 显式刷入磁盘（退出/compact/每轮对话后调用）
  │      └── 定时刷入 → ScheduledExecutorService 每 30 秒自动 flush()
  │
  ├── /compact: 语义折叠 → rewrite() 回写
  │      ├── closeWriter() → 关闭当前 writer
  │      ├── 写入临时文件 → Files.move 原子替换
  │      └── openWriter() → 重新打开 writer
  │
  ├── /new: 存档当前 → 创建新会话
  │      ├── closeWriter() → 关闭旧会话文件
  │      ├── Files.move → 重命名为 archive 文件
  │      └── openWriter() → 打开新会话文件
  │
  └── 退出: flush() → 刷入缓冲区
         └── saveUsage() → 持久化 token 用量
```

**文件位置**: `~/.agent4j/`
- `config.json` — LLM 配置（API URL / Key / 模型 / 工作区）
- `sessions/*.jsonl` — 会话消息（JSONL 格式，每行一条消息）
- `sessions/*.usage` — Token 用量统计
- `memory/*.json` — 持久化记忆

---

## 全部工具列表

### agent4j-tool 核心工具（4 个）

| 工具名 | 只读 | 风暴豁免 | 说明 |
|--------|------|----------|------|
| `run_command` | ❌ | ❌ | 在工作区执行 shell 命令（支持超时控制） |
| `file` | ❌ | ❌ | 文件系统操作（create_dir / create_file / delete_file / delete_dir / move / copy / stat） |
| `glob` | ✅ | ✅ | 按 glob 模式匹配文件名（基于索引缓存，毫秒级响应） |
| `grep` | ✅ | ✅ | 在工作区文件中按正则表达式搜索内容 |
| `tree` | ✅ | ✅ | 生成工作区的目录树结构 |

### agent4j-bin 内置工具（23 个）

#### 文件操作（6 个）

| 工具名 | 只读 | 风暴豁免 | 参数 | 说明 |
|--------|------|----------|------|------|
| `read_file` | ✅ | ✅ | path, head?, tail?, range? | 读取文件（一次性读取完整内容，支持范围/头/尾） |
| `edit_file` | ❌ | ❌ | path, search, replace | SEARCH/REPLACE 编辑（search 必须唯一） |
| `write_file` | ❌ | ❌ | path, content | 创建/覆盖文件（自动创建父目录） |
| `multi_edit` | ❌ | ❌ | edits[{path,search,replace}] | 批量原子编辑（全验证→全写入→失败回滚） |
| `copy_file` | ❌ | ❌ | source, destination | 复制文件或目录 |
| `get_file_info` | ✅ | ✅ | path | 查看文件/目录元信息（JSON） |

#### 代码分析（3 个）

| 工具名 | 只读 | 风暴豁免 | 参数 | 说明 |
|--------|------|----------|------|------|
| `get_symbols` | ✅ | ✅ | path | AST 过滤的顶层符号大纲 |
| `find_in_code` | ✅ | ✅ | path, name | 在文件中查找标识符 |
| `java_source` | ✅ | ✅ | className, jarKeyword? | 通过全限定类名查找 Java 源码 |

#### 网络（2 个）

| 工具名 | 只读 | 风暴豁免 | 参数 | 说明 |
|--------|------|----------|------|------|
| `web_search` | ✅ | ✅ | query, topK? | 搜索互联网（DuckDuckGo Lite） |
| `web_fetch` | ✅ | ✅ | url | 下载 URL 并返回可视化文本内容 |

#### 记忆（3 个）

| 工具名 | 只读 | 风暴豁免 | 参数 | 说明 |
|--------|------|----------|------|------|
| `remember` | ❌ | ❌ | name, type, scope, description, content, priority? | 保存记忆 |
| `recall_memory` | ✅ | ✅ | name | 读取完整记忆内容 |
| `forget` | ❌ | ❌ | name | 删除记忆 |

#### 后台作业（5 个）

| 工具名 | 只读 | 风暴豁免 | 参数 | 说明 |
|--------|------|----------|------|------|
| `run_background` | ❌ | ❌ | (通过 JobService) | 启动后台进程 |
| `job_output` | ✅ | ✅ | jobId, since?, tailLines? | 读取后台作业输出 |
| `wait_for_job` | ❌ | ❌ | jobId, timeoutMs?, waitFor? | 等待作业完成 |
| `stop_job` | ❌ | ❌ | jobId | 停止后台作业（SIGTERM→SIGKILL） |
| `list_jobs` | ✅ | ✅ | (无参数) | 列出所有后台作业 |

#### 计划与交互（5 个）

| 工具名 | 只读 | 风暴豁免 | 参数 | 说明 |
|--------|------|----------|------|------|
| `submit_plan` | ❌ | ❌ | summary?, plan, steps[{id?,title?,action?}] | 提交计划供审查 |
| `revise_plan` | ❌ | ❌ | reason, remainingSteps | 修订进行中的计划 |
| `mark_step_complete` | ❌ | ❌ | stepId, result?, evidence? | 标记计划步骤完成 |
| `ask_choice` | ❌ | ❌ | question, options, allowCustom? | 向用户展示选择菜单 |
| `todo_write` | ❌ | ❌ | todos[{status,content,activeForm?}] | 任务跟踪列表 |

#### 子代理（1 个）

| 工具名 | 只读 | 风暴豁免 | 参数 | 说明 |
|--------|------|----------|------|------|
| `task` | ❌ | ❌ | name, arguments? | 创建隔离子代理处理复杂多步任务 |

---

## 运行方式

### 前置条件

- JDK 8（推荐 Corretto 1.8）
- Maven 3.x
- 一个兼容 OpenAI API 的 LLM 端点（如 DeepSeek / Ollama / OpenAI）

### 配置文件

首次启动自动创建 `~/.agent4j/config.json`，编辑填入 API 信息：

```json
{
  "baseUrl": "https://api.deepseek.com/v1",
  "apiKey": "sk-your-api-key",
  "model": "deepseek-v4-flash",
  "workspaceDir": ".",
  "editMode": "auto",
  "reasoningEffort": "max",
  "lang": "ZH"
}
```

也可通过环境变量覆盖：`OPENAI_BASE_URL`、`OPENAI_API_KEY`、`MODEL`。

### 编译

```bash
# Windows (在项目根目录执行)
set JAVA_HOME=C:\Users\15614\.jdks\corretto-1.8.0_492 && \
"C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.2\plugins\maven\lib\maven3\bin\mvn.cmd" compile -pl agent4j-bin
```

```bash
# Linux / macOS
JAVA_HOME=/path/to/jdk8 mvn compile -pl agent4j-bin
```

### 运行

编译后执行主类 `site.sorghum.agent4j.bin.Agent4jApp`：

```bash
# 使用 Solon Maven Plugin
mvn exec:java -pl agent4j-bin -Dexec.mainClass="site.sorghum.agent4j.bin.Agent4jApp"

# 或直接 java 命令
java -cp agent4j-bin/target/classes:agent4j-tool/target/classes \
  site.sorghum.agent4j.bin.Agent4jApp
```

### 交互命令（CLI）

所有以 "/" 开头的输入由 `Agent4jAgent.chat()` 自动路由到 `ChatCommandRegistry`，
通过 Solon IoC 收集的 `ChatCommand` Bean 执行。Web 模式下同样发送命令字符串到聊天接口即可。

| 命令 | 功能 |
|------|------|
| `/help` | 显示此帮助信息 |
| `/new` | 开启新会话 |
| `/plan` | 进入计划模式（仅只读工具可用） |
| `/execute` | 退出计划模式 |
| `/compact` | 折叠历史消息（语义摘要） |
| `/retry` | 撤回最后一条消息并重试 |
| `/rewind N` | 回退到第 N 轮对话 |
| `/sessions` | 列出历史会话 |
| `/load N` | 加载指定会话 |
| `/init` | 自动分析项目生成 agent4j.md |
| `/hitl` | 切换 HITL 模式（工具执行前需审批） |
| `/agree` | 批准 HITL 待执行的工具调用 |
| `/deny` | 拒绝 HITL 待执行的工具调用 |
| `/exit` | 退出（别名 `/quit`） |

### 测试

```bash
mvn test -pl agent4j-tool
```

现有测试覆盖：
- `WorkspaceIndexTest` — 工作区索引（扫描/glob/grep/tree/增量刷新）
- `SearchToolsTest` — GrepTool / GlobTool / TreeTool 集成测试

---

## 模块依赖关系

```
agent4j (父 POM)
  ├── agent4j-tool    (核心工具库，无外部依赖除 Solon)
  │     └── 依赖: solon-lib, lombok, junit-jupiter
  │
  ├── agent4j-bin     (可执行入口)
  │     ├── 依赖: agent4j-tool
  │     └── 依赖: solon-web, solon-logging-logback, snack4, snack4-jsonpath
  │
  └── agent4j-web     (Web REST API 模块)
        └── 依赖: agent4j-bin
        └── 依赖: solon-web, solon-logging-logback, snack4, snack4-jsonpath
```

---

## 项目亮点

1. **缓存优先设计** — `PromptPrefix` 不可变前缀 + 稳定 tool specs，最大化 DeepSeek 前缀缓存命中
2. **并发安全** — `CountDownLatch` 替代忙等待、`AtomicBoolean` 保证 happens-before、`ConcurrentHashMap` + DCL 工作区隔离
3. **序列化安全** — Snack4 `ONode` 替代手工 JSON 拼接，避免 XSS/注入
4. **高级特性一应俱全** — StormBreaker、Scavenger、MessageHealer、ContextFolding 等
5. **单一职责** — 从大型 Tools.java 拆分为 7 个独立 Service 类
6. **路径穿越防护** — 所有文件操作通过 `resolveSafe()` 严格校验
7. **原子编辑与回滚** — `multi_edit` 全验证→全写入→失败回滚，确保数据一致性
8. **子代理隔离** — `SubAgent` 创建独立循环，排除递归 spawn 和用户交互工具，专注复杂任务
9. **缓冲写入与会话安全** — `JsonlSessionStore` 使用 `BufferedWriter` 保持打开，避免每次 IO 打开/关闭开销；提供 `flush()` 显式刷入 + `ScheduledExecutorService` 每 30 秒自动刷入，确保数据不丢失；`ReentrantLock` 保证线程安全
10. **消除冗余** — Web 模块删除重复的命令 REST API，所有命令操作统一由 `Agent4jAgent.chat()` 通过 `ChatCommandRegistry` 处理，避免 Controller 层逻辑重复

---

*本文档由 Agent4j 自动分析生成。*
