# 🧠 Agent4j — Java AI 编码代理框架

<p align="center">
  <strong>纯 Java 17 实现的 AI 编码代理框架</strong><br>
  推理循环 · 工具编排 · 会话管理 · 流式输出 · 三端界面
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-17%2B-orange?logo=java" alt="Java 17+">
  <img src="https://img.shields.io/badge/Maven-3.x-blue?logo=apache-maven" alt="Maven">
  <img src="https://img.shields.io/badge/Solon-4.0.0--M1-green" alt="Solon 4.0.0-M1">
  <img src="https://img.shields.io/badge/Vue-3.4-4FC08D?logo=vue.js" alt="Vue 3.4">
  <img src="https://img.shields.io/badge/Tauri-2.0-FFC131?logo=tauri" alt="Tauri 2.0">
  <img src="https://img.shields.io/badge/license-MIT-yellow" alt="License">
  <img src="https://img.shields.io/badge/LOC-%7E43K-brightgreen" alt="~43K LOC">
</p>

---

## 📋 概述

**Agent4j** 是一个纯 Java 生态的 AI 编码代理框架，它将大语言模型（LLM）与丰富的可扩展工具系统相结合，打造出一个能自主理解代码、编写代码、调试代码的编程助手。灵感源自 Claude Code、Devin 等 AI 编码代理，但完全基于 Java 17 构建。

> 💡 **一句话**：在你的终端、浏览器或桌面里跑一个 AI 程序员。

### ✨ 核心能力

| 能力 | 说明 |
|------|------|
| 🤖 **推理循环** | 多轮对话上下文中自主思考 → 调用工具 → 观察结果 → 继续推理 |
| 🔧 **工具系统** | 25+ 内置工具，支持 Solon Skill / MCP / OpenAPI 插件扩展 |
| 🔌 **模型无关** | 兼容 OpenAI API 格式，可对接 DeepSeek、Ollama、OpenAI、千问等 |
| 📋 **计划模式** | 只读探索 → 提交计划 → 审批后执行，安全可控 |
| 🛡️ **安全防护** | 风暴断路器（防循环调用）、路径穿越防护、原子编辑回滚 |
| 💾 **会话管理** | JSONL 持久化、异步批量写入（非阻塞 IO）、定时自动刷入、语义折叠 |
| 📊 **Token 追踪** | 用量统计、前缀缓存命中率、按模型/会话分别累计 |
| 🧩 **子代理** | 复杂任务委派，隔离执行上下文，排除递归工具 |
| 🔍 **代码分析** | 源码定位、符号大纲、标识符查找、正则/glob 搜索、目录树浏览 |
| 🌐 **联网能力** | 网络搜索（DuckDuckGo）、网页抓取 |
| 📝 **记忆服务** | 跨会话持久化键值记忆（全局/项目级） |
| 🔌 **MCP 协议** | 支持 MCP 服务器连接管理，动态注册外部工具 |
| 📐 **OpenAPI 集成** | 从 OpenAPI 规范自动生成工具定义 |

---

## 🚀 快速开始

### 前置条件

- **JDK 17+**（推荐 Eclipse Temurin / Amazon Corretto）
- **Maven 3.x**
- **Node.js 18+** & **pnpm 8+**（仅 Web/桌面端需要）
- 一个兼容 OpenAI API 的 LLM 端点（如 DeepSeek / Ollama / OpenAI / 千问）

### 📦 下载 & 编译

```bash
git clone https://gitee.com/ezdemo/agent4j.git
cd agent4j
mvn clean compile -pl agent4j-bin
```

全部模块编译（含 Web 后端）：

```bash
mvn clean compile
```

### ⚙️ 配置

首次启动会自动创建 `~/.agent4j/config.json`，编辑填入 API 信息：

```json
{
  "baseUrl": "https://api.deepseek.com/v1",
  "apiKey": "sk-your-api-key",
  "model": "deepseek-v4-flash",
  "workspaceDir": "",
  "editMode": "auto",
  "reasoningEffort": "high",
  "lang": "ZH",
  "hitl": false,
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

> 也可通过环境变量覆盖：`OPENAI_BASE_URL`、`OPENAI_API_KEY`、`MODEL`。

### ▶️ 运行

**CLI 模式（控制台交互）：**

```bash
# Windows
mvn exec:java -pl agent4j-bin -Dexec.mainClass="site.sorghum.agent4j.bin.app.AppConfig"

# Linux / macOS
mvn compile -pl agent4j-bin
mvn exec:java -pl agent4j-bin -Dexec.mainClass="site.sorghum.agent4j.bin.app.AppConfig"
```

**Web 模式（REST API + Vue3 前端）：**

```bash
# 启动后端 API 服务
mvn exec:java -pl agent4j-web -Dexec.mainClass="site.sorghum.agent4j.web.Agent4jWebApp"

# 启动前端开发服务器（新终端）
cd agent4j-front
pnpm install
pnpm dev
```

**桌面模式（Tauri）：**

```bash
cd agent4j-tauri
pnpm install
pnpm tauri dev
```

### 🎮 交互命令（CLI）

| 命令 | 功能 |
|------|------|
| `/new` | 开启新会话 |
| `/plan` | 进入计划模式（仅只读工具可用） |
| `/execute` | 退出计划模式，恢复全部工具 |
| `/compact` | 折叠历史消息（语义摘要，释放上下文空间） |
| `/retry` | 撤回最后一条消息并重试 |
| `/rewind N` | 回退到第 N 轮对话 |
| `/sessions` | 列出历史会话列表 |
| `/load N` | 加载指定编号的会话 |
| `/init` | 自动分析项目结构生成 `agent4j.md` 项目上下文 |
| `/help` | 显示帮助信息 |
| `/exit` | 退出程序 |

---

## 🏗️ 架构设计

```
┌────────────────────────────────────────────────────────────────────┐
│                        三端界面层                                   │
│  ┌──────────────┐  ┌──────────────────┐  ┌──────────────────┐     │
│  │  CLI Console  │  │  Vue3 Web UI    │  │  Tauri Desktop   │     │
│  │  (agent4j-bin)│  │  (agent4j-front) │  │  (agent4j-tauri) │     │
│  └──────┬───────┘  └────────┬─────────┘  └────────┬─────────┘     │
└─────────┼────────────────────┼──────────────────────┼──────────────┘
          │                    │                      │
┌─────────▼────────────────────▼──────────────────────▼──────────────┐
│                       Solon IoC 容器层                              │
│          @Component · @Configuration · @Bean · @Inject              │
│     AOP 拦截 · CORS 过滤 · Knife4j 文档 · 技能市场 API 调用         │
└─────────────────────────────┬──────────────────────────────────────┘
                              │
┌─────────────────────────────▼──────────────────────────────────────┐
│                       Agent4jAgent (外观)                           │
│              组装 ModelClient + ToolRegistry + SessionService       │
└────────────────┬────────────────────────────┬──────────────────────┘
                 │                            │
┌────────────────▼──────────┐  ┌─────────────▼──────────────────────┐
│      AgentLoop (推理循环)  │  │     ToolDispatcher (工具调度)      │
│  ┌────────────────────────┐│  │  ┌────────────────────────────┐   │
│  │ 1. 构建消息上下文       ││  │  │ StormBreaker 风暴断路器    │   │
│  │ 2. MessageHealer 修复  ││  │  │ PlanMode 权限过滤          │   │
│  │ 3. ContextFolding 折叠 ││  │  │ HITL 人工审批              │   │
│  │ 4. chatStream 流式调用 ││  │  │ Schema 展平/截断           │   │
│  │ 5. Scavenger 回收调用  ││  │  └────────────────────────────┘   │
│  │ 6. Dispatch 并行执行   ││  │                                    │
│  └────────────────────────┘│  │                                    │
└────────────────┬───────────┘  └────────────────┬───────────────────┘
                 │                               │
┌────────────────▼───────────┐  ┌────────────────▼───────────────────┐
│     ModelClient (LLM 客户端) │  │      ToolRegistry (工具注册表)      │
│  ┌────────────────────────┐│  │  ┌──────────────────────────────┐  │
│  │ HTTP SSE 流式/非流式   ││  │  │ 内置工具 (agent4j-tool)      │  │
│  │ SSE 快速路径 JSON 解析  ││  │  │   ├── 文件操作               │  │
│  │ 自动重试 ×10 次        ││  │  │   ├── 代码分析               │  │
│  │ 流中断信号 (Reason …… ││  │  │   ├── 终端/作业              │  │
│  │     Breaker)          ││  │  │   ├── 网络/记忆              │  │
│  │ 10 分钟读取超时        ││  │  │   ├── 计划/交互              │  │
│  └────────────────────────┘│  │  │   └── 子代理/工作区          │  │
└────────────────────────────┘  │  │ Solon Skill 插件             │  │
                                │  │   ├── MCP 服务器代理          │  │
                                │  │   ├── OpenAPI 工具            │  │
                                │  │   └── 技能市场工具            │  │
                                │  └──────────────────────────────┘  │
                                └────────────────────────────────────┘
```

### 🔄 推理循环流程

```
用户输入
   │
   ▼
AgentLoop.run(message)
   │
   ├── 1. ctx.addUser(msg)              ← 追加用户消息
   ├── 2. ctx.buildMessages()            ← 组装 system + history
   ├── 3. MessageHealer.heal()           ← 修复消息（截断/配对/去重）
   │       └── 直接操作 ChatMessage 对象，消除序列化往返
   ├── 4. ContextFolding.fold()          ← 超长时折叠旧消息（语义摘要）
   ├── 5. client.chatStream()            ← 流式调用 LLM（SSE 快速路径）
   │       ├── onReasoningDelta()        ← 思考过程实时打印
   │       ├── onContentDelta()          ← 内容实时打印
   │       └── onToolCalls()             ← 工具调用收集
   │
   ├── 6. Scavenger.scavenge()           ← 从 reasoning 回收丢失的调用
   │
   ├── 7. 无 tool_calls → 返回文本回复
   │
   └── 8. 有 tool_calls
           ├── StormBreaker.inspect()    ← 滑动窗口检查重复调用
           ├── ReasonBreaker.analyze()   ← 检测思考死循环
           ├── dispatch() 并行执行       ← 并行分发工具调用
           ├── ctx.addAssistant()        ← 助理消息
           └── ctx.addToolResult()       ← 工具结果
                 │
                 └── 回到步骤 2（继续循环，最多容忍 5 次自纠错）
```

---

## 🧰 全部工具

### 📁 文件操作（6 个）

| 工具名 | 只读 | 说明 |
|--------|:----:|------|
| `read_file` | ✅ | 读取文件（支持范围、头部、尾部截取） |
| `edit_file` | ❌ | SEARCH/REPLACE 精准编辑（search 必须全文唯一） |
| `write_file` | ❌ | 创建新文件或覆盖现有文件 |
| `multi_edit` | ❌ | 批量原子编辑（全验证 → 全写入 → 失败回滚） |
| `copy_file` | ❌ | 复制文件或目录 |
| `get_file_info` | ✅ | 获取文件/目录元信息（大小、权限、类型） |

### 🔍 代码分析（6 个）

| 工具名 | 只读 | 说明 |
|--------|:----:|------|
| `grep` | ✅ | 正则搜索文件内容（递归） |
| `glob` | ✅ | 按 glob 通配符模式匹配文件名 |
| `tree` | ✅ | 生成目录树结构 |
| `get_symbols` | ✅ | AST 符号大纲（类、方法、字段列表） |
| `find_in_code` | ✅ | 智能标识符查找 |
| `java_source` | ✅ | 通过全限定类名定位 Java 源文件 |

### 💻 终端（1 个）

| 工具名 | 只读 | 说明 |
|--------|:----:|------|
| `run_command` | ❌ | 执行 shell 命令（支持超时控制、路径安全过滤） |

### 🌐 网络（2 个）

| 工具名 | 只读 | 说明 |
|--------|:----:|------|
| `web_search` | ✅ | DuckDuckGo Lite 互联网搜索 |
| `web_fetch` | ✅ | 网页内容抓取与提取 |

### 🧠 记忆（3 个）

| 工具名 | 只读 | 说明 |
|--------|:----:|------|
| `remember` | ❌ | 持久化保存信息到 `~/.agent4j/memory/` |
| `recall_memory` | ✅ | 读取已保存的记忆条目 |
| `forget` | ❌ | 删除指定记忆条目 |

### ⏳ 后台作业（5 个）

| 工具名 | 只读 | 说明 |
|--------|:----:|------|
| `run_background` | ❌ | 启动后台 Shell 进程（分离式） |
| `job_output` | ✅ | 读取后台作业的最新输出 |
| `wait_for_job` | ❌ | 阻塞等待后台作业完成 |
| `stop_job` | ❌ | 终止后台作业（SIGTERM → SIGKILL） |
| `list_jobs` | ✅ | 列出所有活跃/历史后台作业 |

### 📋 计划与交互（5 个）

| 工具名 | 只读 | 说明 |
|--------|:----:|------|
| `submit_plan` | ❌ | 提交执行计划供用户审查 |
| `revise_plan` | ❌ | 修订进行中的计划 |
| `mark_step_complete` | ❌ | 标记计划步骤已完成 |
| `ask_choice` | ❌ | 用户选择菜单（HITL 交互） |
| `todo_write` | ❌ | 任务跟踪列表 |

### 🧩 子代理（1 个）

| 工具名 | 只读 | 说明 |
|--------|:----:|------|
| `task` | ❌ | 创建隔离子代理处理复杂多步任务（独立上下文，排除递归工具） |

### 📦 工作区（4 个，builtin）

| 工具名 | 只读 | 说明 |
|--------|:----:|------|
| `workspace_list` | ✅ | 列出共享工作区中的条目键 |
| `workspace_read` | ✅ | 读取工作区 KV 或文档条目（优先 KV → 文档 → NOT_FOUND） |
| `workspace_write` | ❌ | 写入工作区 KV 或文档条目 |
| `workspace_watch` | ✅ | 监听工作区条目变更事件 |

### 🔌 插件（3 个 Skill 子系统）

| 子系统 | 说明 |
|--------|------|
| **MCP 服务器代理** | 通过 MCP 协议连接外部工具服务器，动态注册工具 |
| **OpenAPI 集成** | 从 OpenAPI 规范（Swagger）自动解析生成工具定义 |
| **技能市场** | 从 Solon 技能市场安装/加载社区技能包 |

---

## 📐 配置参考

> 配置文件位置：`~/.agent4j/config.json`，首次启动自动生成。

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `baseUrl` | string | `http://localhost:11434/v1` | LLM API 基础地址 |
| `apiKey` | string | `sk-your-api-key` | API 密钥 |
| `model` | string | `mimo-v2.5` | 模型名称 |
| `workspaceDir` | string | `""` | 工作区目录（空=不限制） |
| `editMode` | string | `"auto"` | 编辑模式：`auto`（需确认）/ `yolo`（直接执行） |
| `reasoningEffort` | string | `"high"` | 推理力度：`low` / `medium` / `high` / `max` |
| `lang` | string | `"ZH"` | 界面语言：`ZH` / `EN` |
| `hitl` | boolean | `false` | 是否默认开启人工审批（Human-In-The-Loop） |

### ⚙️ 新增可调参数

以下参数在近期优化中外化到配置文件中，无需修改代码即可调整运行时行为：

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `maxContextChars` | number | `200000` | 触发上下文折叠的消息总字符阈值 |
| `keepTailChars` | number | `80000` | 折叠后保留的尾部字符数 |
| `toolTimeoutSec` | number | `360` | 工具调用超时时间（秒） |
| `maxSelfCorrectionAttempts` | number | `5` | 模型自纠错最大重试次数 |
| `maxStreamErrorRetries` | number | `10` | 流式请求最大重试次数（指数退避） |
| `flushIntervalSec` | number | `30` | 会话异步写入定时刷入间隔（秒） |
| `foldHeadCharsLimit` | number | `60000` | 折叠时头部用于摘要的字符预算 |
| `stormWindowSize` | number | `6` | 风暴断路器滑动窗口大小 |
| `stormThreshold` | number | `3` | 风暴断路器触发阈值（窗口内重复次数） |
| `toolResultTruncateChars` | number | `16000` | 工具结果截断阈值 |
| `toolResultKeepChars` | number | `12000` | 工具结果截断后保留字符数 |

### 🚫 安全配置

| 配置项 | 类型 | 说明 |
|--------|------|------|
| `disabledTools` | string[] | 全局禁用的工具名称列表 |
| `blockedPaths` | string[] | 屏蔽的文件系统路径列表 |
| `price` | object | 各模型的 token 计价配置（输入/缓存/输出） |
| `availableModels` | string[] | 可用的模型列表（前端/CLI 切换用） |

### 🌍 环境变量覆盖

| 环境变量 | 覆盖配置 |
|----------|----------|
| `OPENAI_BASE_URL` | `baseUrl` |
| `OPENAI_API_KEY` | `apiKey` |
| `MODEL` | `model` |
| `AGENT4J_DISABLED_TOOLS` | `disabledTools`（逗号分隔） |

---

## 🖥️ 三端界面

Agent4j 提供三种使用方式，覆盖不同场景：

### 1️⃣ CLI 控制台（agent4j-bin）

最直接的交互方式，在终端中运行完整的 AI 编码代理：

```bash
mvn exec:java -pl agent4j-bin -Dexec.mainClass="site.sorghum.agent4j.bin.app.AppConfig"
```

**特点：**
- 完整的交互式命令行体验
- 支持 `/plan`、`/compact`、`/rewind` 等丰富命令
- 实时流式输出思考过程和工具执行结果
- 无需任何前端依赖，即编即用

### 2️⃣ 🌐 Web 界面（agent4j-web + agent4j-front）

基于 Vue 3 + Ant Design Vue 的现代化 Web UI，提供可视化交互体验。

**后端 API 服务**（Solon Web）：

```bash
mvn exec:java -pl agent4j-web -Dexec.mainClass="site.sorghum.agent4j.web.Agent4jWebApp"
```

提供 REST API 端点：
- `GET  /api/chat` — SSE 流式聊天接口
- `GET  /api/sessions` — 会话 CRUD 管理
- `GET  /api/tools` — 工具列表与状态
- `GET  /api/openapi` — OpenAPI 管理
- `GET  /api/agent` — Agent 控制
- `GET  /api/config` — 配置查询与更新

**前端**（Vue 3 + Vite + Pinia）：

```bash
cd agent4j-front
pnpm install
pnpm dev
```

前端技术栈：Vue 3.4 + Ant Design Vue 4.2 + Pinia 状态管理 + Vue Router 4 + Axios + Marked

### 3️⃣ 🖥️ 桌面应用（agent4j-tauri）

基于 Tauri 2.0（Rust）的跨平台桌面应用，将 Web 前端打包为原生桌面程序。

```bash
cd agent4j-tauri
pnpm install
pnpm tauri dev
```

**技术栈：** Tauri 2.0 + Rust + 内嵌 Vue3 前端 + 原生系统集成
**优势：** 更小的体积（< 10MB）、原生窗口体验、系统托盘、自动更新

### 📱 界面切换关系

```
CLI 终端 ──(直接使用)──→ agent4j-bin (Solon IoC)
                            │
Web 浏览器 ──(HTTP/REST)──→ agent4j-web (Solon Web + Knife4j)
                            │
Vue3 前端 ──(axios)────────→ agent4j-web API
                            │
Tauri 桌面 ──(内嵌前端)────→ agent4j-front (Vue3 + Tauri Rust 后端)
```

---

## 💡 设计亮点

### ⚡ 性能优化

- **SSE 快速路径 JSON 解析** — 流式响应中直接解析 JSON 片段，避免完整字符串构建再解析的往返开销，减少 GC 压力和延迟
- **异步会话写入** — 消息先入内存队列，后台消费者线程异步批量写入（最大 50 条/批），关键路径零 IO 阻塞，定时 30 秒自动刷入
- **MessageHealer 序列化消除** — 四项消息修复（配对/去重/补充）直接操作 `ChatMessage` 对象，消除 `ChatMessage ↔ Map` 反复序列化/反序列化往返
- **缓存优先** — `PromptPrefix` 不可变前缀 + 稳定 tool specs 缓存指纹，最大化 DeepSeek 前缀缓存命中率
- **延迟文件创建** — 空白会话不落盘，首次写入消息才创建文件

### 🔒 安全机制

- **风暴断路器** — 滑动窗口（默认 6×3）检测重复工具调用，防止循环耗尽 token
- **ReasonBreaker 推理断路器** — 200 字符滑动窗口检测 thinking 死循环，自动注入警告
- **路径穿越防护** — 所有文件操作通过 `blockedPaths` 校验，防止越权访问
- **原子编辑** — `multi_edit` 全验证 → 全写入 → 失败回滚，确保数据一致性
- **HITL 人工审批** — 非只读工具执行前请求用户确认，安全可控

### 🧩 可扩展性

- **Solon Skill 插件系统** — 通过 `@Component` 自动发现工具，支持 MCP / OpenAPI 协议动态注册
- **插件市场** — 支持从文件系统加载第三方工具包（`plugin.json` 声明式配置）
- **工具热刷新** — 运行时动态刷新工具列表，无需重启进程
- **子代理隔离** — 独立循环上下文，排除递归工具，专注复杂任务

### 🏗 工程架构

- **模块化设计** — 5 个 Maven 模块 + Tauri/Rust 子项目，职责清晰
- **配置外化** — 12+ 运行时参数从代码硬编码迁移到 `config.json`，无需重新编译
- **异常吞咽修复** — 全项目 36 处异常吞咽问题已修复，提升系统稳定性
- **Solon 4.0.0-M1** — 采用新一代 Solon IoC 容器，轻量高效，启动毫秒级

---

## 🧪 测试

```bash
# 运行核心工具模块测试
mvn test -pl agent4j-tool

# 运行全部测试
mvn test
```

### 测试覆盖

| 模块 | 测试内容 |
|------|----------|
| `agent4j-tool` | ToolContext / ToolParameter / ToolResult 序列化，CommandChainParser / CommandTokenizer / SmartDecoder |

---

## 📦 模块结构

```
agent4j/                          # 父 POM（多模块管理）
│
├── agent4j-tool/                 # 🔧 核心工具库
│   ├── 文件/终端/网络/记忆/作业/计划 等工具实现
│   ├── Solon Skill 插件（MCP / OpenAPI / 技能市场 / 系统）
│   └── 依赖：Solon IoC + AI Skill SDK
│
├── agent4j-bin/                  # 🖥️ 可执行入口（CLI）
│   ├── AgentLoop 推理循环
│   ├── ConversationContext 上下文管理
│   ├── HttpModelClient LLM 客户端（SSE 流式）
│   ├── ToolRegistry / ToolDispatcher 工具编排
│   ├── JsonlSessionStore 会话持久化（异步批量写入）
│   ├── StormBreaker / ReasonBreaker / Scavenger
│   ├── MessageHealer / ContextFolding
│   ├── Workspace 共享工作区
│   ├── MCP 服务器管理服务
│   ├── 内置工具（子代理 / 工作区 / 多任务）
│   └── 依赖：agent4j-tool + Solon IoC + OkHttp + Snack4
│
├── agent4j-web/                  # 🌐 Web 服务模块
│   ├── REST API 控制器（聊天/会话/工具/配置/Agent/OpenAPI）
│   ├── Solon Web 容器 + Knife4j 文档
│   ├── CORS 跨域支持
│   └── 依赖：agent4j-bin + Solon Web
│
├── agent4j-front/                # 🎨 Vue3 前端界面
│   ├── Vue 3.4 + Vite 5 + Pinia + Vue Router
│   ├── Ant Design Vue 4.2 组件库
│   ├── Markdown 渲染（marked）
│   └── Tauri API 集成
│
├── agent4j-tauri/                # 🖥️ Tauri 桌面应用
│   ├── Rust 后端（Tauri 2.0）
│   ├── 内嵌 agent4j-front 前端
│   ├── 系统托盘 / 原生窗口 / 自动更新
│   └── 依赖：agent4j-front（前端）
│
└── agent4j-tui/                  # 🚧 TUI 终端界面（占位）
```

### 依赖关系图

```
agent4j (父 POM)
  │
  ├── agent4j-tool                （Solon AI Skill / IoC 基础依赖）
  │     │
  │     └── agent4j-bin           （核心执行入口，依赖 tool 模块）
  │           │
  │           ├── agent4j-web     （REST API 服务，依赖 bin 模块）
  │           │
  │           └── agent4j-front   （Vue3 前端，通过 HTTP 调用 web 模块）
  │
  └── agent4j-tauri               （Tauri 桌面壳，内嵌 front 前端）
```

---

## 📊 项目规模

| 维度 | 数据 |
|------|------|
| Java 文件 | 189 个，18,818 行 |
| Vue 文件 | 19 个，14,450 行 |
| **总代码量** | **~43,268 LOC** |
| Maven 模块 | 5 个（parent + tool + bin + web + front） |
| 其他项目 | agent4j-tauri（Rust）、agent4j-tui（占位） |

---

## 🛠️ 技术栈

| 组件 | 版本 | 用途 |
|------|------|------|
| **Java** | 17+ | 开发语言 |
| **Maven** | 3.x | 构建工具 |
| **Solon** | 4.0.0-M1 | IoC 容器 / AOP / Web MVC |
| **Snack4** | 4.0.49 | JSON 解析与序列化 |
| **Snack4-jsonpath** | 4.0.49 | JSONPath 查询 |
| **OkHttp** | 4.12.0 | HTTP 客户端（SSE 流式调用） |
| **Lombok** | 1.18.34 | 代码简化 |
| **JUnit Jupiter** | 5.10.3 | 单元测试 |
| **SLF4J / Logback** | via Solon | 日志门面与实现 |
| **Vue** | 3.4.21 | 前端框架 |
| **Ant Design Vue** | 4.2.6 | UI 组件库 |
| **Vite** | 5.1.4 | 前端构建工具 |
| **Tauri** | 2.0 | 桌面应用框架（Rust） |
| **Rust** | 2021 edition | Tauri 后端语言 |

---

## 📄 许可证

本项目基于 **MIT 许可证** 开源。

---

<p align="center">
  由 Agent4j 自动维护 · 作者 <a href="https://gitee.com/ezdemo">Sorghum</a><br>
  <sub>Built with ❤️ in pure Java</sub>
</p>
