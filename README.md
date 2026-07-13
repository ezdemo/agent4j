# Agent4j — 纯 Java 的 AI 编码代理

<p align="center">
  <img src="icon.png" width="120" alt="Agent4j Logo"/>
</p>

<p align="center">
  <strong>类似 Claude Code / Codex / OpenCode / Reasonix，但纯 Java 实现</strong><br>
  推理循环 · 工具调用 · 流式输出 · 会话管理 · 子代理隔离<br>
  CLI / Web / Desktop 三端覆盖，让 AI 自主读写代码、跑命令、调 API。
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-blue?logo=openjdk"/>
  <img src="https://img.shields.io/badge/Solon-4.0.2-important?logo=java"/>
  <img src="https://img.shields.io/badge/Vue-3.4-4FC08D?logo=vue.js"/>
  <img src="https://img.shields.io/badge/Electron-42.4-47848F?logo=electron"/>
  <img src="https://img.shields.io/badge/license-MIT-green"/>
  <img src="https://img.shields.io/badge/version-26.6.29-lightgrey"/>
  <a href="CHANGELOG.md"><img src="https://img.shields.io/badge/changelog-📋-brightgreen"/></a>
</p>

---

## 🚀 快速开始

> 📋 [更新日志](CHANGELOG.md) — 查看各版本变更记录

### 一键安装

**Windows**（PowerShell）：

```powershell
irm https://raw.giteeusercontent.com/ezdemo/agent4j/raw/main/.release/setup.ps1 | iex
```

**macOS / Linux**：

```bash
curl -fsSL https://raw.giteeusercontent.com/ezdemo/agent4j/raw/main/.release/setup.sh | bash
```

### 直接运行

```bash
# 启动 Web 服务（随机端口，首次自动生成配置）
agent4j web 0
```

首次启动会自动创建 `~/.agent4j/config.json`，填入 API Key 和模型后重启：

```json
{
  "baseUrl": "https://api.deepseek.com/v1",
  "apiKey": "sk-your-api-key",
  "model": "deepseek-v4-flash",
  "workspaceDir": "/path/to/your/project"
}
```

---

## 📖 这是什么？

**Agent4j** 是一个纯 Java 17 的 AI 编码代理。和 Claude Code、Codex、OpenCode、Reasonix 一样——你给它一个任务，它能自己读代码、写代码、跑命令、调
API，一步步把事情干完。

核心是一个 **推理循环（Reasoning Loop）**：

```
用户说 → LLM 想 → 调工具 → 看结果 → LLM 再想 → 再调工具 → …… → 干完
```

| 对标产品            | 实现语言          |
|-----------------|---------------|
| **Claude Code** | TypeScript    |
| **Codex**       | TypeScript    |
| **OpenCode**    | Go            |
| **Reasonix**    | Rust          |
| **Agent4j**     | **Java 17** ✅ |

如果你在用 Java 技术栈，又想有一个 AI 编码代理来帮忙写代码、改代码、跑构建、查日志——Agent4j 是你的选择。

---

## 📸 界面预览

### 主界面

<p align="center">
  <img src="img/main_app.png" width="90%" alt="Agent4j 主界面"/>
</p>

### 数据面板

<p align="center">
  <img src="img/data_dashboard_1.png" width="45%" alt="数据面板 1"/>
  &nbsp;&nbsp;
  <img src="img/data_dashboard_2.png" width="45%" alt="数据面板 2"/>
</p>

---

## 🆕 最新更新（v26.6.29）

### 🐾 桌面宠物精灵
- 新增桌面宠物精灵组件 (PetSprite)，支持精灵图动画与拖拽移动
- 多宠物选择与切换，设置页面管理和切换不同宠物
- 宠物初始化与快捷安装提示，首次使用引导安装

### 🛠️ 技能选择与权限切换
- 聊天输入框内可搜索并多选技能，自动拼接到消息中
- 自由模式/审批模式一键切换，无需进入设置

### 🔒 HITL 免审批工具
- `finish` / `ask_choice` 等纯交互控制流工具直接放行，无需用户确认

### 🧠 模型适配
- HttpModelClient 添加 `thinking` 字段，兼容更多推理模型配置

> 📋 完整变更记录请查看 [CHANGELOG.md](CHANGELOG.md)

---

## ⚡ 前缀缓存命中率

Agent4j 充分利用 DeepSeek 和 Mimo 的**前缀缓存（Prefix Caching）** 能力——系统提示词、工具定义、项目文档等每次都在 prompt
开头的重复内容，直接命中 KV cache：

| 模型                                                | 缓存命中率     | 效果               |
|---------------------------------------------------|-----------|------------------|
| **DeepSeek**（deepseek-v4-flash / deepseek-v4-pro） | **≥ 97%** | 输入 token 费用降至 3% |
| **小米 Mimo**（mimo-v2.5 / mimo-v2.5-pro）            | **≥ 98%** | 输入 token 费用降至 2% |

实际编码会话中，消息列表头部的大量系统指令和工具描述每次都一样，前缀缓存命中后这部分 token 几乎免费。

---

## ✨ 功能

### 🤖 推理循环

| 能力         | 说明                                    |
|------------|---------------------------------------|
| **自动循环**   | Prompt → LLM → 工具 → 结果 → LLM，自动迭代直到完成 |
| **流式输出**   | 实时推送思考过程、工具调用和结果                      |
| **上下文折叠**  | 旧消息自动摘要，不机械截断                         |
| **消息自愈**   | 自动修复被截断的 JSON 和 tool_calls            |
| **风暴断路器**  | 检测重复工具调用，防止死循环                        |
| **推理模型支持** | 原生支持展示思考过程                            |

### 🛠️ 工具系统

- **声明式工具**：继承 `AgentTool` 基类，定义名称、参数、执行逻辑即可
- **自动注册**：Solon `@Component` 自动发现
- **MCP 支持**：接入 Model Context Protocol 服务器

<p align="center">
  <img src="img/mcp_setting.png" width="60%" alt="MCP 设置"/>
</p>

- **OpenAPI 集成**：任意 OpenAPI 规范自动转成工具
- **技能市场**：在线装/卸社区技能

<p align="center">
  <img src="img/skill_setting.png" width="60%" alt="技能设置"/>
</p>

- **Plan Mode**：只读模式，安全规划

### 🔄 子代理

- **隔离执行**：每个子代理有独立上下文和推理循环
- **继承工具**：复制父代理的工具集，排除递归 spawn
- **独立通道**：子代理输出通过独立事件流推送
- **用量统计**：按模型统计 token 消耗

### 👤 人工审批（HITL）

- 执行写操作前等你批准或拒绝
- `/agree` / `/deny` 快速决策
- `todo_write` 维护任务清单

### 💬 会话

- 多会话创建、切换、搜索、删除
- JSONL 格式持久化，工作区隔离
- 自动生成会话标题
- 记录每次的 token 用量
- 消息快照系统（基于 Git 的检查点与撤回）

### 🌐 三种界面

| 形态          | 怎么用          |
|-------------|--------------|
| **CLI**     | 终端里直接干       |
| **Web**     | 浏览器打开可视化界面   |
| **Desktop** | Electron 原生桌面应用 |

### 🎨 前端

- 深色、浅色、复古绿、复古黄四套主题
- SSE 流式打字机效果
- 工具调用可视化
- Git 面板
- 工作区管理
- 毛玻璃视觉效果
- 代码语法高亮（Shiki/Shikiji）
- 消息时间戳与流式加载动画

---

## 🏗️ 项目结构

```
agent4j/
├── agent4j-bin/               # 核心引擎（含工具抽象层）
│   ├── agent/                 # 推理循环
│   │   ├── AgentLoop.java      # 推理循环
│   │   ├── Agent4jAgent.java   # Agent 工厂
│   │   ├── SubAgent.java       # 子代理
│   │   ├── context/            # 对话上下文 / 上下文折叠
│   │   ├── model/              # LLM 客户端
│   │   └── hitl/               # 人工审批
│   ├── tool/                  # 工具系统（原 agent4j-tool 已合并）
│   │   ├── AgentTool.java      # 工具基类
│   │   ├── ToolContext.java     # 执行上下文
│   │   ├── builtin/            # 内置工具
│   │   ├── executor/           # Shell / 交互式命令执行
│   │   ├── solon/              # Solon 集成（技能/MCP/OpenAPI/插件）
│   │   └── vision/             # 视觉识别工具
│   ├── session/               # 会话持久化
│   ├── workspace/             # 共享工作区
│   ├── workflow/              # 工作流引擎
│   ├── command/               # 聊天命令
│   └── config/                # ~/.agent4j/config.json
│
├── agent4j-web/               # Web 后端
│   ├── controller/            # REST 接口
│   ├── service/               # Agent 管理 / SSE 推送
│   └── market/                # 技能市场
│
├── agent4j-front/             # Vue 3 前端 + Electron 桌面端
│   ├── src/
│   │   ├── components/
│   │   ├── views/
│   │   ├── services/
│   │   │   ├── api.js
│   │   │   └── platform.js    # 平台适配层（Electron/Web）
│   │   └── stores/app.js
│   ├── electron/
│   │   ├── main.cjs           # Electron 主进程
│   │   └── preload.cjs        # 预加载脚本
│   ├── electron-builder.json  # 打包配置
│   └── vite.config.js
│
├── agent4j-tui/               # TUI 终端界面
├── intro/                     # 官网
├── docs/superpowers/          # 文档
├── pom.xml                    # Maven 父 POM
└── LICENSE                    # MIT
```

---

## ⚙️ 配置

配置文件 `~/.agent4j/config.json`：

<p align="center">
  <img src="img/base_setting.png" width="60%" alt="基础设置"/>
  &nbsp;&nbsp;
  <img src="img/lsp_setting.png" width="60%" alt="LSP 设置"/>
</p>

| 字段                | 类型       | 默认值                         | 说明                               |
|-------------------|----------|-----------------------------|----------------------------------|
| `baseUrl`         | string   | `http://localhost:11434/v1` | API 地址                           |
| `apiKey`          | string   | —                           | API 密钥                           |
| `model`           | string   | `deepseek-v4-flash`         | 模型名                              |
| `workspaceDir`    | string   | `""`                        | 工作区目录                            |
| `reasoningEffort` | string   | `high`                      | 推理强度：`low`/`medium`/`high`/`max` |
| `lang`            | string   | `ZH`                        | 语言                               |
| `hitl`            | bool     | `false`                     | 人工审批默认开关                         |
| `editMode`        | string   | `auto`                      | `auto`（需确认）/ `yolo`（直接干）         |
| `maxContextChars` | int      | `200000`                    | 上下文上限                            |
| `keepTailChars`   | int      | `80000`                     | 保留尾部预算                           |
| `toolTimeoutSec`  | int      | `360`                       | 工具超时                             |
| `disabledTools`   | string[] | `[]`                        | 禁用工具                             |
| `blockedPaths`    | string[] | `[]`                        | 路径拦截                             |

---

## 🎯 命令

| 命令          | 功能       |
|-------------|----------|
| `/help`     | 帮助       |
| `/new`      | 新会话      |
| `/plan`     | 进入计划模式   |
| `/execute`  | 退出计划模式   |
| `/compact`  | 折叠历史     |
| `/retry`    | 撤回重试     |
| `/rewind N` | 回退到第 N 轮 |
| `/sessions` | 列出会话     |
| `/load N`   | 加载会话     |
| `/init`     | 分析项目生成文档 |
| `/hitl`     | 切换审批模式   |
| `/agree`    | 批准       |
| `/deny`     | 拒绝       |
| `/continue` | 继续生成     |
| `/exit`     | 退出       |

---

## 🧩 内置工具

| 工具                                                                          | 用途         |
|-----------------------------------------------------------------------------|------------|
| `read` / `write` / `edit`                                                   | 读/写/改文件    |
| `glob` / `grep` / `ls`                                                      | 搜索文件与内容    |
| `bash`                                                                      | 跑命令        |
| `bash_start` / `bash_wait` / `bash_stdin` / `bash_stop`                     | 交互式命令会话    |
| `task` / `multi_task`                                                       | 派生子代理      |
| `workspace_read` / `workspace_write` / `workspace_list` | 工作区操作      |
| `webfetch`                                                                  | 抓网页        |
| `codesearch`                                                                | 搜索代码       |
| `java_source`                                                               | Java源码查找   |
| `call_api`                                                                  | 调 REST API |
| `remember` / `recall_memory` / `forget`                                     | 持久记忆       |
| `vision_recognize`                                                           | 图片识别       |
| `submit_plan` / `revise_plan` / `mark_step_complete`                        | 计划管理       |
| `ask_choice` / `todo_write`                                                 | 用户交互       |
| `run_background` / `stop_job` / `wait_for_job` / `job_output` / `list_jobs` | 后台作业       |

---

## 🛠️ 技术栈

| 层       | 技术                                  |
|---------|-------------------------------------|
| **语言**  | Java 17                             |
| **后端**  | Solon 4.0.2 + Snack4 + OkHttp        |
| **前端**  | Vue 3.4 + Vite 5 + Ant Design Vue 4 |
| **桌面**  | Electron + Node.js                    |
| **持久化** | JSON Lines                          |
| **文档**  | Knife4j                             |

---

## 📊 性能

| 指标               | 数据            |
|------------------|---------------|
| DeepSeek 前缀缓存命中率 | **≥ 97%**     |
| 小米 Mimo 前缀缓存命中率  | **≥ 98%**     |
| 输入成本压缩           | **至原始 2%~3%** |
| 最大上下文字符          | 200,000       |
| 会话并发             | 50（LRU）       |
| 工具超时             | 360 秒可配       |

---

## 🗺️ 路线图

- [x] 推理循环
- [x] 工具调用
- [x] 流式输出
- [x] 会话管理
- [x] 子代理
- [x] 人工审批
- [x] 前缀缓存（DeepSeek 97%+ / Mimo 98%+）
- [x] MCP 协议
- [x] 技能市场
- [x] Electron 桌面端
- [x] OpenAPI 集成
- [x] Git 面板
- [x] 多模态
- [ ] 本地知识库
- [ ] 团队协作

---

## 📄 许可证

MIT License © 2026 Sorghum

---

<p align="center">
  <strong>Agent4j</strong> — 纯 Java 的 AI 编码代理<br>
  像 Claude Code 一样干活，用 Java 写
</p>