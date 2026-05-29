# Agent4j Web

> AI 编码代理 Web 服务 — 通过 REST API 暴露全部 Agent 功能

## 快速开始

1. **配置 API Key**
   编辑 `~/.agent4j/config.json`，填入你的 LLM API Key：
   ```json
   {
     "baseUrl": "https://api.deepseek.com/v1",
     "apiKey": "sk-your-api-key",
     "model": "deepseek-v4-flash"
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

## API 接口

### 聊天接口

**POST /api/chat**
```json
{
  "message": "帮我分析这个项目",
  "sessionId": "optional-session-id"
}
```

### SSE 流式输出

**GET /api/chat/stream?message=xxx**

返回 Server-Sent Events 流：
```
data: {"type":"content","content":"正在分析..."}
data: {"type":"tool_call","name":"read_file","args":{...}}
data: {"type":"tool_result","result":"..."}
data: {"type":"done"}
```

### 会话管理

- `GET /api/sessions` — 列出所有会话
- `POST /api/sessions` — 创建新会话
- `GET /api/sessions/{id}` — 获取会话详情
- `DELETE /api/sessions/{id}` — 删除会话

### 工具管理

- `GET /api/tools` — 列出所有可用工具
- `POST /api/tools/{name}/execute` — 执行工具

## 配置说明

| 字段 | 说明 | 默认值 |
|------|------|--------|
| baseUrl | LLM API 地址 | - |
| apiKey | API 密钥 | - |
| model | 模型名称 | - |
| workspaceDir | 工作区目录 | `.` |
| lang | 语言 | `ZH` |
| hitl | HITL 模式 | `false` |
| disabledTools | 禁用的工具 | `[]` |
| blockedPaths | 禁止访问的路径 | `[]` |

## 环境变量

支持通过环境变量覆盖配置：

- `OPENAI_BASE_URL` — LLM API 地址
- `OPENAI_API_KEY` — API 密钥
- `MODEL` — 模型名称
- `AGENT4J_DISABLED_TOOLS` — 禁用的工具（逗号分隔）
- `AGENT4J_BLOCKED_PATHS` — 禁止访问的路径（逗号分隔）

## 目录结构

```
~/.agent4j/
├── config.json          # 配置文件
├── sessions/            # 会话历史
│   ├── *.jsonl          # 会话消息
│   ├── *.usage          # Token 用量
│   └── *.meta           # 会话元信息
├── memory/              # 持久化记忆
└── skills/              # 自定义技能
```

## 命令行参数

```bash
agent4j-web [options]

Options:
  --port=8097           指定服务端口
  --workspace=/path     指定工作区目录
  --config=/path        指定配置文件路径
```

## 开发说明

- **框架**: Solon 3.9.6 (轻量级 Java Web 框架)
- **Java**: 17+
- **构建**: Maven

```bash
# 编译
mvn clean package -pl agent4j-web

# 运行
java -jar agent4j-web/target/agent4j-web.jar

# 或使用 Maven
mvn exec:java -pl agent4j-web -Dexec.mainClass="site.sorghum.agent4j.web.Agent4jWebApp"
```

## 许可证

MIT License
