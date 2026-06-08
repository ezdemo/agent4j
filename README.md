# Agent4j — Java AI 编码代理框架

<p align="center">
  <img src="icon.png" width="120" alt="Agent4j Logo"/>
</p>

<p align="center">
  <strong>纯 Java 17 实现的智能 AI 编码代理框架</strong><br>
  推理循环 · 工具调度 · 流式输出 · 会话持久化 · 子代理隔离<br>
  将 LLM 与可扩展工具系统结合，通过 CLI / Web / Desktop 三种界面，形成自主工作的编码代理。
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-blue?logo=openjdk"/>
  <img src="https://img.shields.io/badge/Solon-4.0.0--M3-important?logo=java"/>
  <img src="https://img.shields.io/badge/Vue-3.4-4FC08D?logo=vue.js"/>
  <img src="https://img.shields.io/badge/Tauri-2.0-FFC131?logo=tauri"/>
  <img src="https://img.shields.io/badge/license-MIT-green"/>
  <img src="https://img.shields.io/badge/version-26.6.8.1-lightgrey"/>
</p>

## 🚀 快速开始

### 🚀 一键安装

**Windows** (PowerShell)：

```powershell
irm https://raw.giteeusercontent.com/ezdemo/agent4j/raw/main/.release/setup.ps1 | iex
```

**Mac / Linux**：

```bash
curl -fsSL https://raw.giteeusercontent.com/ezdemo/agent4j/raw/main/.release/setup.sh | bash
```

### 直接运行

```bash
# 启动 Web 服务（随机端口，首次启动会自动生成配置文件）
agent4j web 0
```

首次启动会自动创建 `~/.agent4j/config.json`，**编辑配置文件填入 API Key 和模型信息后重启**：

```json
{
  "baseUrl": "https://api.deepseek.com/v1",
  "apiKey": "sk-your-api-key",
  "model": "deepseek-v4-flash",
  "workspaceDir": "/path/to/your/project",
  "reasoningEffort": "high",
  "lang": "ZH",
  "hitl": false
}
```

---

## 📖 简介

**Agent4j** 是一个纯 Java 17 实现的 AI 编码代理框架。它的核心是一个**推理循环（Reasoning Loop）**：LLM 收到用户消息 →
自主决定调用工具 → 工具结果反馈回 LLM → LLM 继续推理——循环往复，直到完成任务。

项目受到 Claude Code / Agent4j TS 等 AI 编码代理启发，但完全基于 **Java 生态** 构建：

- **后端**：基于 [Solon](https://solon.noear.org/) 框架，Java 17 + Maven 多模块
- **工具系统**：可扩展的 `AgentTool` 抽象层，MCP / OpenAPI / 插件技能
- **前端**：Vue 3 + Vite + Ant Design Vue，支持深色/浅色/复古主题
- **桌面端**：Tauri 2.0（Rust）包装，自动下载捆绑 JRE，开箱即用
- **流式输出**：SSE（Server-Sent Events）实时推送

---

## ✨ 核心特性

### 🤖 AI 推理引擎

- **推理循环**：自动编排 prompt → LLM → 工具调用 → 结果反馈的循环
- **流式输出**：实时推送内容片段、推理过程、工具调用和结果
- **上下文折叠**：旧消息语义摘要替代机械截断，节约 token
- **消息自愈**：自动修复被截断的 JSON 参数、不完整的 tool_calls/tool 对
- **风暴断路器**：滑动窗口检测重复工具调用，防止死循环
- **推理模型支持**：支持 `reasoning_content` 的思考过程展示

### 🛠️ 工具系统

- **AgentTool 抽象**：所有工具继承 `AgentTool` 基类，声明名称、描述、参数和执行逻辑
- **工具注册中心**：IoC 自动收集 + `ToolRegistry` 管理生命周期
- **MCP 支持**：Model Context Protocol 服务器管理（增删改查/启停）
- **OpenAPI 集成**：将任意 OpenAPI 规范注册为可调用工具
- **技能插件市场**：在线安装/卸载技能
- **Plan Mode**：只读模式下仅允许查询工具，用于制定计划
- **風暴豁免**：只读工具自动豁免风暴检测

### 🔄 子代理系统

- **隔离执行**：子代理拥有独立的 `ConversationContext` 和 `AgentLoop`
- **继承工具集**：复制父代理的工具注册表，排除递归 spawn 工具
- **独立输出通道**：子代理的流式输出通过独立 SSE 事件推送
- **用量追踪**：按模型分别累计 prompt/completion/cache tokens

### 👤 人机协同（HITL）

- **审批模式**：执行非只读工具前等待用户批准或拒绝
- **命令行控制**：`/agree` / `/deny` 命令快速决策
- **待办列表**：`todo_write` 工具维护会话任务跟踪

### 💬 会话管理

- **多会话支持**：创建、切换、删除、搜索会话
- **JSONL 持久化**：基于 JSON Lines 格式，工作区隔离存储
- **自动标题生成**：首条消息自动生成会话标题
- **Token 用量记录**：记录每次对话的 prompt/completion token 数

### 🌐 三种交互界面

| 界面          | 技术栈           | 启动方式                                                        |
|-------------|---------------|-------------------------------------------------------------|
| **CLI**     | Java 控制台      | `java -jar agent4j-bin.jar`                                 |
| **Web**     | Solon + Vue 3 | `java -jar agent4j-web.jar` → 浏览器访问 `http://localhost:4567` |
| **Desktop** | Tauri (Rust)  | `pnpm tauri build` → 原生桌面应用                                 |

### 🎨 前端特性

- 深色/浅色/复古绿/复古黄 四套主题
- SSE 流式打字机效果
- 工具调用可视化和状态展示
- 工作区管理 + Git 面板
- 响应式布局

### 🔌 扩展能力

- **MCP 服务器管理**：增删改查、连接检测、工具权限控制
- **技能市场**：在线浏览和安装社区技能
- **插件体系**：Solon `@Component` 自动注册
- **OpenAPI 注册**：将任意 REST API 描述为 Agent 工具

---

## 🏗️ 项目架构

```
agent4j/
├── agent4j-tool/          # 工具抽象层（核心 API）
│   ├── AgentTool          # 所有工具的基类
│   ├── ToolContext         # 工具执行上下文
│   ├── AgentOutput         # 输出接口（Console/SSE）
│   ├── terminal/           # Shell 命令执行（白名单/解析器/Killer）
│   ├── job/                # 后台作业管理（运行/停止/等待）
│   ├── memory/             # 持久化记忆系统
│   ├── plan/               # 计划系统（提交/修订/步骤标记）
│   ├── interact/           # 用户交互（选择/待办）
│   └── solon/              # Solon 集成（技能/MCP/OpenAPI/插件）
│
├── agent4j-bin/           # 核心代理（推理循环 + 工具调度）
│   ├── agent/
│   │   ├── AgentLoop       # 推理循环主引擎
│   │   ├── Agent4jAgent    # Agent 工厂与生命周期
│   │   ├── SubAgent        # 隔离子代理
│   │   ├── ConversationContext  # 对话上下文管理
│   │   ├── ContextFolding  # 语义上下文折叠
│   │   ├── StormBreaker    # 风暴断路器
│   │   ├── MessageHealer   # 消息自愈
│   │   └── HitlManager     # 人机协同管理
│   ├── tool/
│   │   ├── ToolRegistry    # 工具注册中心
│   │   ├── ToolDispatcher  # 工具调度器（Plan Mode/Storm/Hooks）
│   │   └── ToolSystemInitializer  # 工具系统初始化
│   ├── model/              # LLM 模型客户端（HTTP API）
│   ├── session/            # JSONL 会话持久化
│   ├── workspace/          # 共享工作区（KV + 文档存储 + 事件总线）
│   ├── mcp/                # MCP 服务器管理
│   ├── command/            # 聊天命令（/help, /retry, /compact 等）
│   └── config/             # ~/.agent4j/config.json 配置
│
├── agent4j-web/           # Web 后端（Solon REST API）
│   ├── controller/         # REST 控制器
│   │   ├── ChatController    # 聊天 API（同步 + SSE 流式）
│   │   ├── AgentController   # Agent 状态/历史/命令
│   │   ├── SessionController # 会话 CRUD
│   │   ├── ToolController    # 工具列表/详情
│   │   ├── GitController     # Git 操作
│   │   ├── McpController     # MCP 服务器管理
│   │   ├── SkillMarketController  # 技能市场
│   │   ├── OpenApiController     # OpenAPI 管理
│   │   └── SystemController     # 系统状态/版本
│   ├── service/
│   │   ├── AgentService     # 会话级 Agent 生命周期管理
│   │   ├── SseAgentOutput   # SSE 输出桥接
│   │   └── SseEmitter       # SSE 事件推送
│   └── market/              # 技能市场实现
│
├── agent4j-front/         # Vue 3 前端
│   ├── src/
│   │   ├── components/     # 组件（ChatInput, GitPanel, TitleBar...）
│   │   ├── views/          # 页面（Chat, Home, Settings, Tools...）
│   │   ├── services/api.js # Axios + SSE 流式 API
│   │   ├── stores/app.js   # Pinia 状态管理
│   │   ├── composables/    # 组合式函数
│   │   └── router/         # Vue Router 路由
│   └── vite.config.js      # Vite 配置（代理/CORS/构建）
│
├── agent4j-tauri/         # Tauri 桌面端
│   └── src-tauri/
│       ├── src/lib.rs      # Rust 后端（进程管理/JDK 自动安装）
│       └── tauri.conf.json # Tauri 配置（窗口/构建/打包）
│
├── intro/                  # 官网介绍页（独立 HTML）
├── docs/superpowers/       # 文档与规范
├── pom.xml                 # Maven 父 POM
└── LICENSE                 # MIT 许可证
```

---

## ⚙️ 配置说明

配置文件位于 `~/.agent4j/config.json`，支持以下配置项：

| 配置项               | 类型       | 默认值                         | 说明                                     |
|-------------------|----------|-----------------------------|----------------------------------------|
| `baseUrl`         | string   | `http://localhost:11434/v1` | OpenAI 兼容 API 地址                       |
| `apiKey`          | string   | `sk-your-api-key`           | API 密钥                                 |
| `model`           | string   | `deepseek-v4-flash`         | 模型名称                                   |
| `workspaceDir`    | string   | `""`                        | 工作区目录                                  |
| `reasoningEffort` | string   | `high`                      | 推理力度：`low` / `medium` / `high` / `max` |
| `lang`            | string   | `ZH`                        | 语言：`ZH` / `EN`                         |
| `hitl`            | boolean  | `false`                     | HITL 默认开关                              |
| `editMode`        | string   | `auto`                      | 编辑模式：`auto` / `yolo`                   |
| `maxContextChars` | int      | `200000`                    | 上下文最大字符数                               |
| `keepTailChars`   | int      | `80000`                     | 保留尾部字符预算                               |
| `toolTimeoutSec`  | int      | `360`                       | 工具执行超时（秒）                              |
| `stormWindowSize` | int      | `6`                         | 风暴断路器窗口大小                              |
| `stormThreshold`  | int      | `3`                         | 风暴断路器触发阈值                              |
| `disabledTools`   | string[] | `[]`                        | 禁用的工具列表                                |
| `blockedPaths`    | string[] | `[]`                        | 路径拦截列表                                 |
| `availableModels` | string[] | `[...]`                     | 可用模型列表                                 |

---

## 🎯 聊天命令

在聊天输入框中以 `/` 开头使用：

| 命令          | 说明               |
|-------------|------------------|
| `/help`     | 显示帮助信息           |
| `/new`      | 开启新会话            |
| `/plan`     | 进入计划模式（仅允许只读工具）  |
| `/execute`  | 退出计划模式           |
| `/compact`  | 折叠历史消息释放上下文      |
| `/retry`    | 撤回最后一条消息并重试      |
| `/rewind N` | 回退到第 N 轮对话       |
| `/sessions` | 列出历史会话           |
| `/load N`   | 加载指定会话           |
| `/init`     | 自动分析项目生成文档       |
| `/hitl`     | 切换 HITL 模式       |
| `/agree`    | 批准 HITL 待执行的工具调用 |
| `/deny`     | 拒绝 HITL 待执行的工具调用 |
| `/continue` | 继续被中断的生成         |
| `/exit`     | 退出系统             |

---

## 🧩 内置工具

Agent4j 提供丰富的内置工具，覆盖文件操作、代码搜索、Shell 执行等：

| 工具                                                                          | 说明                    |
|-----------------------------------------------------------------------------|-----------------------|
| `read`                                                                      | 读取文件内容                |
| `write`                                                                     | 创建或覆盖文件               |
| `edit`                                                                      | SEARCH/REPLACE 精准文本替换 |
| `glob`                                                                      | 通配符搜索文件               |
| `grep`                                                                      | 递归内容搜索                |
| `ls`                                                                        | 列出目录内容                |
| `bash`                                                                      | 执行 Shell 命令           |
| `bash_start` / `bash_wait` / `bash_stdin` / `bash_stop`                     | 交互式命令会话               |
| `task` / `multi_task`                                                       | 创建设置子代理/多子代理          |
| `workspace_read` / `workspace_write` / `workspace_list` / `workspace_watch` | 共享工作区操作               |
| `webfetch`                                                                  | 获取网页内容                |
| `codesearch`                                                                | 代码语义搜索                |
| `call_api`                                                                  | REST API 调用           |
| `remember` / `recall_memory` / `forget`                                     | 持久记忆                  |
| `submit_plan` / `revise_plan` / `mark_step_complete`                        | 计划管理                  |
| `ask_choice` / `todo_write`                                                 | 用户交互                  |
| `run_background` / `stop_job` / `wait_for_job` / `job_output` / `list_jobs` | 后台作业管理                |

---

## 🧪 技术栈

### 后端

| 技术                 | 用途              |
|--------------------|-----------------|
| **Java 17**        | 开发语言            |
| **Solon 4.0.0-M3** | Web 框架 + IoC 容器 |
| **Snack4**         | JSON 解析与操作      |
| **OkHttp**         | HTTP 客户端        |
| **Knife4j**        | API 文档          |
| **Maven**          | 构建管理            |
| **JUnit 5**        | 单元测试            |

### 前端

| 技术                   | 用途       |
|----------------------|----------|
| **Vue 3.4**          | 前端框架     |
| **Vite 5**           | 构建工具     |
| **Pinia**            | 状态管理     |
| **Vue Router**       | 路由管理     |
| **Ant Design Vue 4** | UI 组件库   |
| **Axios**            | HTTP 客户端 |
| **Vitest**           | 单元测试     |

### 桌面端

| 技术                     | 用途               |
|------------------------|------------------|
| **Tauri 2.0**          | 桌面应用框架           |
| **Rust**               | 桌面后端语言           |
| **flate2 / tar / zip** | 压缩包处理            |
| **ureq**               | HTTP 客户端（JDK 下载） |

---

## 📊 项目路线图

- [x] 核心推理循环与工具调度
- [x] 会话持久化与上下文管理
- [x] SSE 流式输出
- [x] HITL 人机协同
- [x] 子代理隔离执行
- [x] MCP 服务器管理
- [x] 技能市场与插件体系
- [x] Tauri 桌面端打包
- [x] OpenAPI 集成
- [x] Git 面板
- [ ] 更多 LLM 提供商支持
- [ ] 多模态（图片理解）
- [ ] 本地知识库 RAG
- [ ] 团队协作与共享会话
- [ ] 持续学习与偏好适应

---

## 🤝 贡献指南

欢迎贡献代码！请遵循以下步骤：

1. **Fork** 本仓库
2. 创建功能分支：`git checkout -b feature/amazing-feature`
3. 提交更改：`git commit -m 'Add amazing feature'`
4. 推送到分支：`git push origin feature/amazing-feature`
5. 创建 **Pull Request**

### 开发规范

- Java 代码遵循项目现有风格（Lombok、SOLID 原则）
- 前端代码遵循 Vue 3 Composition API + 命名规范
- 提交信息使用中文或英文，清晰描述变更内容
- 新功能需包含单元测试

---

## 📄 许可证

本项目基于 **MIT License** 开源。

```
MIT License

Copyright (c) 2026 Sorghum

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
```

---

## 📬 联系方式

- **项目主页**：https://gitee.com/ezdemo/agent4j
- **问题反馈**：https://gitee.com/ezdemo/agent4j/issues
- **作者**：Sorghum

---

<p align="center">
  <strong>Agent4j</strong> — 用 Java 构建你的 AI 编码代理
</p>
