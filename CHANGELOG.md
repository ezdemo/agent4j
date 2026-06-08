# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [26.6.8] — 2026-06-08

### 🎉 Initial Release

First public release of Agent4j, a pure Java 17 AI coding agent framework.

#### Core Features

- **Reasoning Loop** — Multi-turn autonomous think → tool → observe → repeat cycle
- **Tool System** — 40+ built-in tools spanning file ops, terminal, background jobs, memory, plans, workspace, sub-agents, and web
- **Model Agnostic** — OpenAI-compatible API; works with DeepSeek, Ollama, OpenAI, Claude, Gemini, and more
- **Plan Mode** — Read-only exploration → submit plan → approve → execute, safe and controllable
- **Safety Mechanisms** — StormBreaker (loop detection), path traversal prevention, atomic edit rollback, HITL human approval
- **Session Management** — JSONL persistence, async buffered writes, semantic context folding, token usage tracking
- **Sub-Agents** — Complex task delegation with isolated execution contexts (single & parallel)
- **Shared Workspace** — Blackboard architecture for inter-agent communication (KV + Document + EventBus)
- **Skill System** — Markdown+YAML skills with inline/subagent run modes, auto-discovery from filesystem
- **Plugin System** — `~/.agent4j/plugin/` auto-scan and registration
- **MCP Protocol** — Model Context Protocol server integration
- **OpenAPI Integration** — Auto-generate tools from OpenAPI specs
- **Memory Service** — Cross-session persistent key-value memory (global/project scope)
- **Code Analysis** — Glob, grep, ls, read, write, edit with atomicity
- **Web Fetching & Code Search** — Internet-connected capabilities

#### Interfaces

- **CLI Console** (`agent4j-bin`) — Full-featured terminal interface with chat commands
- **Web UI** (`agent4j-web` + `agent4j-front`) — Vue 3 SPA with SSE streaming
- **Desktop App** (`agent4j-tauri`) — Tauri v2 cross-platform desktop application

#### Tech Stack

- Java 17 + Maven multi-module
- Solon 4.0.0-M1 IoC container
- Vue 3.4 + Vite 5 + Pinia + Ant Design Vue
- Tauri 2.0 + Rust 2021 edition

---

[26.6.8]: https://github.com/agent4j/agent4j/releases/tag/v26.6.8
