<h1 align="center">Loopra</h1>

<p align="center">
  <img src="img/logo.png" alt="Loopra logo" width="160"/>
</p>

<p align="center">
  <strong>纯 Java 的 AI 编码代理</strong><br/>
  面向本地代码库的推理循环、工具调用、会话协作与桌面工作台。
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-blue?logo=openjdk" alt="Java 17"/>
  <img src="https://img.shields.io/badge/Solon-4.0.3-important?logo=java" alt="Solon 4.0.3"/>
  <img src="https://img.shields.io/badge/Vue-3.4-4FC08D?logo=vue.js" alt="Vue 3"/>
  <img src="https://img.shields.io/badge/Electron-42.4-47848F?logo=electron" alt="Electron"/>
  <img src="https://img.shields.io/badge/version-26.7.24.1-lightgrey" alt="Version 26.7.24.1"/>
  <img src="https://img.shields.io/badge/license-MIT-green" alt="MIT License"/>
</p>

<p align="center">
  <a href="#快速开始">快速开始</a> ·
  <a href="#核心能力">核心能力</a> ·
  <a href="#配置参考">配置参考</a> ·
  <a href="#从源码开发">从源码开发</a>
</p>

> 当前版本：`26.7.24.1`。完整变更记录见 [CHANGELOG.md](CHANGELOG.md)。

## 概览

Loopra 将用户任务、模型推理与受控工具调用组织成持续执行循环：它读取项目上下文，搜索、编辑或构建代码，消费执行结果后继续推理，直至完成任务或需要用户决策。

提供 Web 与 Electron Desktop 工作台，适合在本地代码库中完成开发、排障、测试、文档整理和多步骤协作。

```text
任务 -> 模型推理 -> 工具调用 -> 结果反馈 -> 模型推理 -> ... -> 完成
```

| 面向的工作 | Loopra 提供的能力 |
|---|---|
| 处理代码库任务 | 工作区内文件读写、代码检索、命令执行与 API 调用 |
| 保持任务连续性 | JSONL 会话、上下文折叠、Goal、Checklist 与项目记忆 |
| 安全地协作 | 工具权限分类、HITL 审批、路径边界与独立校验模型 |
| 使用合适的界面 | Web 管理界面与包含本地进程、文件、Git、浏览器的 Desktop 工作台 |

## 快速开始

> 建议手动运行下面命令再安装桌面端

桌面端下载地址：[Releases](https://gitcode.com/Sorghum/loopra/releases/latest)。Desktop 首次启动会安装独立运行时到 `~/.loopra-gui`；CLI 仍安装在 `~/.loopra`，两者共用配置目录但不会复用或终止对方的服务进程。

### 1. 安装

Windows PowerShell：

```powershell
irm https://raw.giteeusercontent.com/ezdemo/loopra/raw/main/.release/setup.ps1 | iex
```

macOS / Linux：

```bash
curl -fsSL https://raw.giteeusercontent.com/ezdemo/loopra/raw/main/.release/setup.sh | bash
```

### 2. 启动服务

使用 `0` 让服务自动选择可用端口：

```bash
loopra web 0
```

控制台会输出本地访问地址。首次启动会在 `~/.loopra/config.json` 创建默认配置。

### 3. 配置模型渠道

在 Web 设置页维护模型渠道，或直接编辑 `~/.loopra/config.json`。配置 API Key 后重启服务。每个渠道独立维护 API 地址、密钥、协议与模型能力；`apiProtocol` 支持 `chat_completions` 和 `responses`。

```json
{
  "model": "deepseek-v4-flash",
  "modelChannelId": "default",
  "modelChannels": [
    {
      "id": "default",
      "name": "Default",
      "baseUrl": "https://api.deepseek.com/v1",
      "apiKey": "sk-your-api-key",
      "apiProtocol": "chat_completions",
      "models": [
        {
          "name": "deepseek-v4-flash",
          "contextTokens": -1,
          "imageInput": false
        }
      ]
    }
  ],
  "workspaceDir": "/path/to/your/project"
}
```

`models` 兼容旧版字符串数组。每个模型条目还可配置 `contextTokens`、`imageInput` 和 `price`；未配置时由系统使用默认或可用的模型元数据。

## 核心能力

| 能力 | 说明 |
|---|---|
| 自主推理循环 | 流式输出推理、工具调用和结果，持续处理多轮任务。 |
| 上下文管理 | JSONL 会话持久化、自动摘要折叠、消息自愈和 token 用量统计。 |
| 多模型渠道 | 支持多渠道、Chat Completions API、OpenAI Responses API、推理强度和模型能力配置。 |
| 可扩展工具系统 | 通过 Solon `@ToolMapping` 声明式注册，支持内置工具、MCP、OpenAPI、技能和 REST API。 |
| 代码库操作 | 在工作区边界内读取、搜索、编辑文件，运行一次性或交互式命令。 |
| 子代理协作 | 内置 `explore`、`implement`、`test`、`review`、`plan` 五种角色，支持隔离上下文、权限约束和超时控制。 |
| 持久协作状态 | Checklist、会话级 Goal、项目记忆和共享工作区支持长任务及父子代理协作。 |
| 审批与边界 | 三态 HITL、工具白名单、路径边界保护，以及可选的独立校验模型。 |
| 桌面工作台 | Electron Desktop 提供多聊天标签、Git/文件面板、元素检查、服务进程管理和 AI 浏览器。 |

### 工具与扩展

工具由 Solon 自动发现，可在工具管理界面启用、禁用，并为自定义工具指定只读或写入分类。

| 分类 | 覆盖场景 |
|---|---|
| 文件与代码检索 | `read`、`write`、`edit`、`glob`、`grep`、`ls`、`java_source`、`codesearch` |
| 命令与网络 | `bash`、交互式命令会话、`webfetch`、`call_api` |
| 任务协作 | `sub_agent`、`checklist_*`、`goal_*`、`workspace_*` |
| 项目状态 | `memory` 将跨会话事实保存到 `.loopra/loopra-memory.md`；共享工作区保存到 `.loopra/workspace/` |
| 浏览器 | 桌面端可见 AI 浏览器的 `browser_*` 工具 |

MCP、OpenAPI 和技能可为 Agent 注入额外工具。浏览器工具只操作可见的 Desktop 浏览器；遇到登录、验证码或安全验证时，Agent 会请求用户接管，不会代填或读取敏感凭据。

### 子代理与长期协作

子代理通过 `sub_agent` 派生。`explore`、`review` 和 `plan` 为只读角色；`implement` 与 `test` 可执行经过授权的写操作。每个子代理拥有独立推理上下文，并可经由共享工作区传递结构化结果。

项目记忆只保存稳定、可复用的项目事实，例如架构约定、已知限制和用户偏好。复杂任务可使用会话级 Goal 记录步骤、证据、阻塞原因与验证结果；Checklist 用于展示有序执行进度。

### 审批与访问边界

`hitl` 支持三种执行模式：

| 模式 | 行为 |
|---|---|
| `free` | 所有工具直接执行。 |
| `approval` | 非只读工具执行前等待用户批准。 |
| `auto` | 白名单工具自动放行，其余调用等待批准。 |

可配置 `validationModel` 与 `validationModelChannelId`，让独立模型在人工审批前评估高风险工具调用。无论当前模式或校验结果如何，访问工作区边界外的路径仍需要人工确认。

## 配置参考

主要配置文件为 `~/.loopra/config.json`：

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `modelChannels` | array | 默认渠道 | API 地址、密钥、协议与模型条目。 |
| `modelChannelId` | string | `default` | 当前模型所属渠道 ID。 |
| `model` | string | `deepseek-v4-flash` | 当前模型名称。 |
| `validationModel` | string | `""` | 可选的工具调用校验模型。 |
| `validationModelChannelId` | string | `""` | 校验模型所在渠道 ID。 |
| `workspaceDir` | string | 自动创建默认工作区 | 当前工作区目录。 |
| `reasoningEffort` | string | `high` | `low`、`medium`、`high` 或 `max`。 |
| `hitl` | string | `free` | `free`、`approval` 或 `auto`。 |
| `editMode` | string | `auto` | 编辑模式。 |
| `maxContextChars` | int | `200000` | 上下文字符预算。 |
| `keepTailChars` | int | `80000` | 折叠后保留的最近上下文预算。 |
| `toolTimeoutSec` | int | `360` | 单工具超时秒数。 |
| `subAgentTimeoutSec` | int | `3600` | 子代理完整任务超时秒数。 |
| `disabledTools` | string[] | `[]` | 禁用的工具名称。 |
| `blockedPaths` | string[] | `[]` | 受限路径列表。 |

首次启动未指定 `workspaceDir` 时，Loopra 会在配置目录旁创建默认工作区，避免将启动目录意外作为项目根目录。

## 使用方式

### Web 与 Desktop

`loopra web [port]` 启动 Web 服务，控制台会输出本地访问地址。Web 界面支持会话、工作区、工具、模型渠道、Git 和配置管理；Electron Desktop 在此基础上增加本地进程和浏览器能力。

### 聊天命令

在聊天输入框中使用以下命令：

| 命令 | 用途 |
|---|---|
| `/help` | 显示可用命令。 |
| `/new` | 创建会话。 |
| `/sessions` / `/load N` | 查看和加载历史会话。 |
| `/plan` / `/execute` | 切换只读计划模式与执行模式。 |
| `/compact` | 手动折叠历史上下文。 |
| `/goal ...` | 创建、查看、暂停、恢复、阻塞或完成会话目标。 |
| `/hitl` / `/agree` / `/deny` | 切换审批模式或处理待审批工具调用。 |
| `/retry` / `/rewind N` / `/continue` | 管理当前回复的重试、回退和继续。 |
| `/init` | 分析项目并生成项目文档。 |

### ACP

Web 进程可通过启动参数启用 Agent Client Protocol：

```bash
# stdio 模式
loopra web 0 --loopra.acp=true

# WebSocket 模式
loopra web 0 --loopra.acp=true --loopra.acp.ws.port=8765
```

## 从源码开发

### 前置条件

- JDK 17
- Maven 3.9+
- Node.js 18+
- pnpm 8+

### 构建与测试

```bash
# 构建后端模块
mvn clean package

# 运行全部后端测试
mvn test

# 仅验证 Web 模块及其依赖模块
mvn -pl loopra-web -am test
```

### 前端与 Desktop

```bash
cd loopra-front
pnpm install

# 启动前端开发服务
pnpm dev

# 运行前端测试并构建
pnpm test -- --run
pnpm build

# 启动 Electron 开发模式
pnpm dev:electron
```

## 项目结构

```text
loopra/
├── loopra-bin/     # Agent 核心：推理循环、工具、模型、会话、配置与协作状态
├── loopra-web/     # Solon Web 服务、REST/SSE 接口和打包配置
├── loopra-front/   # Vue 前端与 Electron Desktop
├── intro/          # 官网内容
├── docs/           # 项目文档
├── .release/       # 安装与发布脚本
└── .workflow/      # CI 流水线配置
```

## 技术栈

| 层 | 技术 |
|---|---|
| 核心 | Java 17、Solon 4.0.3、Snack4、OkHttp |
| Web | Solon Web、Jetty、SSE、Knife4j |
| 前端 | Vue 3、Vite、Pinia、Ant Design Vue |
| 桌面 | Electron、electron-builder |
| 协议 | MCP、OpenAPI、ACP、Chat Completions API、Responses API |
| 持久化 | JSONL 会话、工作区本地 JSON、项目 Markdown 记忆 |

## 许可证

[MIT License](LICENSE) © 2026 Sorghum
