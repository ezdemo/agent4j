# Agent4j — Java AI Agent 框架

<p align="center">
  <strong>Java 8 实现的 AI 编码代理框架</strong><br>
  推理循环 · 工具调用 · 会话管理 · 流式输出 · 子代理
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-8%2B-orange?logo=java" alt="Java 8+">
  <img src="https://img.shields.io/badge/Maven-3.x-blue?logo=apache-maven" alt="Maven">
  <img src="https://img.shields.io/badge/Solon-3.9.6-green" alt="Solon 3.9.6">
  <img src="https://img.shields.io/badge/license-MIT-yellow" alt="License">
</p>

---

## 概述

Agent4j 是一个纯 Java 实现的 AI 编码代理框架，将 LLM（大语言模型）与可扩展的工具系统结合，形成一个能自主工作的编程助手。灵感来源于 Claude Code、Devin 等 AI 编码代理，但完全在 Java 生态中实现。

**一句话**：在你的终端里跑一个 AI 程序员。

### 核心能力

| 能力 | 说明 |
|------|------|
| 🤖 **推理循环** | 多轮对话上下文中自主思考 → 调用工具 → 观察结果 → 继续推理 |
| 🔧 **工具系统** | 27+ 内置工具，可扩展，支持并行执行 |
| 🔌 **模型无关** | OpenAI 兼容 API，可对接 DeepSeek、Ollama、OpenAI 等 |
| 📋 **计划模式** | 只读探索 → 提交计划 → 审批后执行，安全可控 |
| 🛡️ **安全机制** | 风暴断路器（防循环调用）、路径穿越防护、原子编辑回滚 |
| 💾 **会话管理** | JSONL 持久化、缓冲写入、定时刷入、语义折叠 |
| 📊 **Token 追踪** | 用量统计、前缀缓存命中率、按会话累计 |
| 🧩 **子代理** | 复杂任务委派，隔离执行上下文 |
| 🔍 **代码分析** | 源码定位、符号大纲、标识符查找、正则/glob 搜索 |
| 🌐 **联网能力** | 网络搜索、网页抓取 |
| 📝 **记忆服务** | 跨会话持久化键值记忆 |

---

## 快速开始

### 前置条件

- JDK 8（推荐 Corretto 1.8）
- Maven 3.x
- 一个兼容 OpenAI API 的 LLM 端点（如 DeepSeek / Ollama / OpenAI）

### 下载 & 编译

```bash
git clone https://gitee.com/ezdemo/agent4j.git
cd agent4j
mvn compile -pl agent4j-bin
```

### 配置

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

### 运行

```bash
# Windows
set JAVA_HOME=C:\path\to\corretto-1.8.0_492
mvn exec:java -pl agent4j-bin -Dexec.mainClass="site.sorghum.agent4j.bin.Agent4jApp"
```

```bash
# Linux / macOS
JAVA_HOME=/path/to/jdk8 mvn compile -pl agent4j-bin
java -cp "agent4j-bin/target/classes:agent4j-tool/target/classes" \
  site.sorghum.agent4j.bin.Agent4jApp
```

### 交互命令

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
| `/exit` | 退出 |

---

## 架构设计

```
┌─────────────────────────────────────────────┐
│         Agent4jApp (main / CLI)              │
│          交互式命令行 / 循环入口               │
└──────────────────┬──────────────────────────┘
                   │
┌──────────────────▼──────────────────────────┐
│          Agent4jAgent (工厂 + 外观)           │
│   组装 ModelClient + ToolRegistry + Session   │
└────┬──────────────────────────┬─────────────┘
     │                          │
┌────▼──────┐    ┌──────────────▼──────────────┐
│ AgentLoop │    │     ToolDispatcher           │
│ 推理循环   │    │   PlanMode/Storm/Hooks       │
│ LLM ↔ Tool│    │   Schema 展平 / 截断          │
└────┬──────┘    └──────────────┬──────────────┘
     │                          │
┌────▼──────┐    ┌──────────────▼──────────────┐
│ModelClient│    │       ToolRegistry           │
│ API 客户端 │    │   注册/查询/转 OpenAI Schema │
│(HTTP SSE) │    │                              │
└───────────┘    └─────────────────────────────┘
```

### 推理循环流程

```
用户输入
   │
   ▼
AgentLoop.run(message)
   │
   ├── 1. ctx.addUser(msg)              ← 追加用户消息
   ├── 2. ctx.buildMessages()            ← 组装 system + history
   ├── 3. MessageHealer.heal()           ← 修复消息（截断/配对）
   ├── 4. ContextFolding.fold()          ← 超长时折叠旧消息
   ├── 5. client.chatStream()            ← 流式调用 LLM
   │       ├── onReasoningDelta()        ← 思考过程实时打印
   │       ├── onContentDelta()          ← 内容实时打印
   │       └── onToolCalls()             ← 工具调用收集
   │
   ├── 6. Scavenger.scavenge()           ← 从 reasoning 回收丢失的调用
   │
   ├── 7. 无 tool_calls → 返回文本回复
   │
   └── 8. 有 tool_calls
           ├── StormBreaker.inspect()    ← 检查重复调用
           ├── dispatch() 并行执行       ← 并行分发
           ├── ctx.addAssistant()        ← 助理消息
           └── ctx.addToolResult()       ← 工具结果
                 │
                 └── 回到步骤 2（继续循环）
```

---

## 全部工具

### 文件操作（6 个）

| 工具名 | 只读 | 说明 |
|--------|------|------|
| `read_file` | ✅ | 读取文件（支持范围/头/尾） |
| `edit_file` | ❌ | SEARCH/REPLACE 精确编辑（search 必须唯一） |
| `write_file` | ❌ | 创建/覆盖文件 |
| `multi_edit` | ❌ | 批量原子编辑（全验证→全写入→失败回滚） |
| `copy_file` | ❌ | 复制文件或目录 |
| `get_file_info` | ✅ | 文件/目录元信息 |

### 代码分析（4 个）

| 工具名 | 只读 | 说明 |
|--------|------|------|
| `grep` | ✅ | 正则搜索文件内容 |
| `glob` | ✅ | 按 glob 模式匹配文件名 |
| `tree` | ✅ | 生成目录树结构 |
| `get_symbols` | ✅ | AST 符号大纲 |
| `find_in_code` | ✅ | 标识符查找 |
| `java_source` | ✅ | 通过全限定类名查找 Java 源码 |

### 终端（1 个）

| 工具名 | 只读 | 说明 |
|--------|------|------|
| `run_command` | ❌ | 执行 shell 命令（支持超时控制） |

### 网络（2 个）

| 工具名 | 只读 | 说明 |
|--------|------|------|
| `web_search` | ✅ | 互联网搜索（DuckDuckGo Lite） |
| `web_fetch` | ✅ | 网页抓取 |

### 记忆（3 个）

| 工具名 | 只读 | 说明 |
|--------|------|------|
| `remember` | ❌ | 保存记忆 |
| `recall_memory` | ✅ | 读取记忆 |
| `forget` | ❌ | 删除记忆 |

### 后台作业（5 个）

| 工具名 | 只读 | 说明 |
|--------|------|------|
| `run_background` | ❌ | 启动后台进程 |
| `job_output` | ✅ | 读取作业输出 |
| `wait_for_job` | ❌ | 等待作业完成 |
| `stop_job` | ❌ | 停止作业 |
| `list_jobs` | ✅ | 列出所有作业 |

### 文件系统（1 个，通用）

| 工具名 | 只读 | 说明 |
|--------|------|------|
| `file` | ❌ | 创建/删除/移动/复制/查看文件或目录 |

### 计划与交互（5 个）

| 工具名 | 只读 | 说明 |
|--------|------|------|
| `submit_plan` | ❌ | 提交计划供审查 |
| `revise_plan` | ❌ | 修订进行中的计划 |
| `mark_step_complete` | ❌ | 标记步骤完成 |
| `ask_choice` | ❌ | 用户选择菜单 |
| `todo_write` | ❌ | 任务跟踪列表 |

### 子代理（1 个）

| 工具名 | 只读 | 说明 |
|--------|------|------|
| `task` | ❌ | 创建隔离子代理处理复杂多步任务 |

---

## 技术栈

| 组件 | 版本 | 用途 |
|------|------|------|
| **Java** | 8 (Corretto 1.8) | 开发语言 |
| **Maven** | 3.x | 构建工具 |
| **Solon** | 3.9.6 | IoC 容器 / AOP / Web |
| **Snack4** | 4.0.49 | JSON 解析与序列化 |
| **Snack4-jsonpath** | 4.0.49 | JSONPath 查询 |
| **Lombok** | 1.18.34 | 代码简化 |
| **JUnit Jupiter** | 5.10.3 | 单元测试 |
| **Logback** | via Solon | 日志 |
| **SLF4J** | 1.7.36 | 日志门面 |

---

## 模块说明

```
agent4j/
├── agent4j-tool/    核心工具库（文件/终端/搜索）
├── agent4j-bin/     可执行入口（循环/会话/内置工具）
├── agent4j-web/     Web 模块（待实现）
└── pom.xml          父 POM 多模块管理
```

### 依赖关系

```
agent4j (父 POM)
  ├── agent4j-tool    (核心工具，无外部依赖除 Solon)
  └── agent4j-bin     (可执行入口，依赖 agent4j-tool)
        └── agent4j-web (未来 Web 接口)
```

---

## 设计亮点

- **缓存优先** — `PromptPrefix` 不可变前缀 + 稳定 tool specs，最大化 DeepSeek 前缀缓存命中
- **并发安全** — `CountDownLatch` 替代忙等待、`AtomicBoolean` 保证 happens-before
- **延迟文件创建** — 空白会话不落盘，首次写入消息才创建文件
- **原子编辑** — `multi_edit` 全验证→全写入→失败回滚，确保数据一致性
- **安全防护** — 所有文件操作通过路径校验，防止路径穿越
- **子代理隔离** — 独立循环上下文，排除递归工具，专注复杂任务

---

## 测试

```bash
mvn test -pl agent4j-tool
```

现有测试覆盖：工作区索引扫描、glob/grep/tree 集成测试。

---

## 许可证

本项目基于 MIT 许可证开源。

---

<p align="center">
  由 Agent4j 自动维护 · 作者 <a href="https://gitee.com/ezdemo">Sorghum</a>
</p>
