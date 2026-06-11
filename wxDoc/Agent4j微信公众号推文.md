
# Agent4j：纯 Java 的 AI 编码代理，来了！

> 推理循环 · 工具调用 · 流式输出 · 会话管理 · 子代理隔离 —— 用 Java 写，像 Claude Code 一样干活。

---

## 🤔 又一个 AI 编码代理…凭什么？

Claude Code、Codex、OpenCode、Reasonix——AI 编码赛道已经人头攒动了。但它们有一个共性：**没有一个是 Java 生态的**。

- Claude Code → TypeScript
- Codex → TypeScript
- OpenCode → Go
- Reasonix → Rust

**Agent4j** 用纯 Java 17 填补了这个缺口。如果你在 Java 技术栈上做开发，终于有一个能和你的项目栈天然融合的 AI 编码代理了。

---

## 🧠 核心：推理循环

Agent4j 的心脏是一个**推理循环**：

```
用户说 → LLM 思考 → 调工具 → 看结果 → LLM 再想 → 再调工具 → … → 干完
```

AI 不是一次生成完事，而是像人类工程师一样：读代码、写代码、跑命令、看日志、调整、再试——一整轮一整轮地迭代直到把事情做完。

配备的「黑科技」：
- **流式输出**：思考过程实时推送，打字机般丝滑
- **上下文折叠**：旧消息智能摘要，不会一刀切地截断
- **消息自愈**：LLM 输出的 JSON 断了？自动修复
- **风暴断路器**：同一条命令死循环调用？检测到就止损
- **推理模型支持**：原生展示「思考过程」

---

## 🛠️ 武装到牙齿的工具系统

30+ 内置工具，覆盖编码代理的方方面面：

| 类别 | 工具 |
|------|------|
| 文件操作 | `read` `write` `edit` |
| 代码搜索 | `glob` `grep` `ls` |
| Shell 执行 | `bash` `bash_start/wait/stdin/stop` |
| 子代理派发 | `task` `multi_task` |
| 工作区协作 | `workspace_read/write/list/watch` |
| Web & API | `webfetch` `codesearch` `call_api` |
| 记忆持久化 | `remember` `recall_memory` `forget` |
| 计划管理 | `submit_plan` `revise_plan` `mark_step_complete` |
| 交互审批 | `ask_choice` `todo_write` |

**声明式写工具**：继承 `AgentTool` 基类，Solon `@Component` 自动注册。还能接入 **MCP 协议**和 **OpenAPI 规范**，生态扩展不受限。

---

## 🌐 Web · Desktop 双端覆盖

| 形态 | 怎么用 |
|------|--------|
| **Web** | 浏览器打开，Vue 3 现代化可视化界面 |
| **Desktop** | Tauri 2.0 原生桌面应用，Windows/macOS/Linux 全平台 |

Web 端支持四套主题（深色·浅色·复古绿·复古黄）、SSE 流式打字机、工具调用可视化、Git 面板，企业级设计，毛玻璃 UI 加持。

---

## 📊 前缀缓存：输入 Token 成本压到 3%

Agent4j 深度适配 DeepSeek 和小米 Mimo 的**前缀缓存**机制：

| 模型 | 缓存命中率 | 输入 Token 费用 |
|------|-----------|---------------|
| DeepSeek (v4-flash/v4-pro) | **≥ 97%** | 降至 3% |
| 小米 Mimo (v2.5/v2.5-pro) | **≥ 98%** | 降至 2% |

每次对话，系统提示词、工具定义、项目文档这些「重复开头」直接命中 KV cache——**白嫖级别的省钱**。

---

## 🆕 近期更新亮点（v26.6.11.1）

以下是最近几个版本的干货：

### 🎨 UI 焕新
- **毛玻璃视觉效果**全面铺开，侧边栏、标题栏、状态栏、输入框、卡片通通配上
- Diff 预览改为**左右对比**，代码差异一目了然
- 思考和工具卡片加了 **Spinner 动画**，推理卡片默认展开
- 用量栏合并到输入框底部，界面更清爽

### 🎯 目标系统
- 新增 **Goal 引擎**，LLM 可以自主追踪步骤完成进度
- 30s 定时巡逻守护线程，失败步骤**自动重新注入上下文**
- `/goal` 命令直接操作目标，AI 自己标记 `goal_mark_step`

### 🔧 Git 集成
- AI 自动生成 Git 提交消息
- 提交历史查看
- 作者配置弹窗与持久化

### 🤖 推理引擎
- **FinishTool**：AI 觉得活干完了，显式结束循环
- 无工具调用降级终止 + 最大迭代限制 → 后来干脆改成**无限循环**
- AgentLoop 提示语全部英文化，兼容更好

### 📐 LSP 集成
- LSP 服务器启动/停止管理
- 一键禁用开关，全局配置

---

## ⚡ 十秒上手

**Windows：**
```powershell
irm https://raw.giteeusercontent.com/ezdemo/agent4j/raw/main/.release/setup.ps1 | iex
```

**macOS / Linux：**
```bash
curl -fsSL https://raw.giteeusercontent.com/ezdemo/agent4j/raw/main/.release/setup.sh | bash
```

启动 Web 服务：
```bash
agent4j web 0
```

编辑 `~/.agent4j/config.json` 填上 API Key：
```json
{
  "baseUrl": "https://api.deepseek.com/v1",
  "apiKey": "sk-your-api-key",
  "model": "deepseek-v4-flash",
  "workspaceDir": "/your/project"
}
```

重启，开干。

---

## 🏗️ 技术栈一览

| 层 | 选型 |
|----|------|
| 语言 | Java 17 |
| 后端框架 | Solon 4.0.0-M3 + Snack4 + OkHttp |
| 前端 | Vue 3.4 + Vite 5 + Ant Design Vue 4 |
| 桌面 | Tauri 2.0 + Rust |
| 持久化 | JSON Lines |
| 许可证 | MIT |

---

## 🗺️ 路线图

- ✅ 推理循环 / 工具调用 / 流式输出 / 会话管理
- ✅ 子代理 / 人工审批 / MCP 协议
- ✅ 技能市场 / Tauri 桌面端 / OpenAPI 集成
- ✅ 前缀缓存 / Git 面板 / LSP 集成
- 🔲 多模态支持
- 🔲 本地知识库
- 🔲 团队协作

---

## 🔗 一键直达

- 项目地址：**https://gitee.com/ezdemo/agent4j**
- 官网：**http://agent4j.sorghum.site**
- 更新日志：[CHANGELOG.md](https://gitee.com/ezdemo/agent4j/blob/main/CHANGELOG.md)

---

> **Agent4j** —— 纯 Java 的 AI 编码代理。  
> 像 Claude Code 一样干活，用 Java 写。  
> 快上车，让 AI 帮你写代码。

---

*本文基于 Agent4j v26.6.11.1 撰写 | MIT 开源 | 作者 Sorghum*
