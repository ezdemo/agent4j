# Tauri + Agent4j Web 集成指南

本文档说明如何将 Agent4j Web 后端集成到 Tauri 桌面应用中。

## 架构概览

```
┌─────────────────────────────────────────────────────────────┐
│                      Tauri 桌面应用                          │
│  ┌───────────────────────────────────────────────────────┐  │
│  │                    Vue 3 前端                          │  │
│  │                 (agent4j-front)                        │  │
│  └───────────────────────────────────────────────────────┘  │
│                          ↕ HTTP                             │
│  ┌───────────────────────────────────────────────────────┐  │
│  │              Agent4j Web 后端服务                       │  │
│  │            (localhost:8097)                            │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐  │
│  │                  Rust 后端 (Tauri)                     │  │
│  │              - 进程管理                                │  │
│  │              - 自动安装                                │  │
│  │              - 生命周期管理                             │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

## 功能特性

1. **自动安装**：首次运行时自动从资源中安装 Agent4j Web
2. **自动启动**：应用启动时自动启动 Agent4j Web 后端服务
3. **自动关闭**：应用关闭时自动停止 Agent4j Web 服务
4. **启动画面**：显示服务启动状态和进度
5. **健康检查**：等待服务就绪后再显示主界面

## 构建步骤

### 1. 编译 Agent4j Web

```bash
# Windows
.\agent4j-tauri\build-with-web.ps1

# Linux/macOS
./agent4j-tauri/build-with-web.sh
```

这会：
- 编译 agent4j-web 模块
- 生成分发包 (agent4j-web-dist.zip/tar.gz)
- 复制到 `agent4j-tauri/src-tauri/resources/`

### 2. 构建 Tauri 应用

```bash
cd agent4j-tauri

# 开发模式
pnpm tauri dev

# 构建生产版本
pnpm tauri build
```

## 目录结构

```
agent4j-tauri/
├── build-with-web.ps1          # Windows 构建脚本
├── build-with-web.sh           # Linux/macOS 构建脚本
├── src-tauri/
│   ├── resources/              # 资源目录（构建时生成）
│   │   ├── agent4j-web-dist.zip
│   │   └── agent4j-web-dist.tar.gz
│   ├── src/
│   │   ├── main.rs
│   │   └── lib.rs              # Rust 后端（进程管理）
│   ├── capabilities/
│   │   └── default.json        # Tauri 权限配置
│   ├── Cargo.toml              # Rust 依赖
│   └── tauri.conf.json         # Tauri 配置
└── package.json
```

## 工作原理

### Rust 后端 (lib.rs)

```rust
// Agent4jWebManager 管理器
struct Agent4jWebManager {
    child: Mutex<Option<Child>>,      // 子进程引用
    install_dir: Mutex<PathBuf>,      // 安装目录
}

// 生命周期
// 1. setup: 检查安装 → 自动安装 → 启动服务
// 2. on_window_event(Destroyed): 停止服务
```

### 前端 (SplashScreen.vue)

```vue
<SplashScreen 
  v-if="isTauri"
  @ready="onServiceReady"
  @error="onServiceError"
/>
```

启动画面会：
1. 检查服务是否已安装
2. 如果未安装，等待 Rust 端完成安装
3. 启动服务并轮询健康检查
4. 服务就绪后触发 `ready` 事件

### API 服务 (tauri.js)

```javascript
// Tauri 专用 API
export const agent4jWebService = {
  getStatus(),    // 获取状态
  start(),        // 启动服务
  stop(),         // 停止服务
  waitForReady(), // 等待就绪
  healthCheck(),  // 健康检查
}
```

## 安装位置

Agent4j Web 安装到用户的 home 目录：

```
~/.agent4j/
├── bin/
│   ├── agent4j-web.jar
│   ├── agent4j-web.ps1
│   ├── agent4j-web.bat
│   └── agent4j-web (Linux/macOS)
├── config.json
├── sessions/
├── memory/
└── skills/
```

## 配置

### Tauri 配置 (tauri.conf.json)

```json
{
  "bundle": {
    "resources": [
      "resources/agent4j-web-dist.zip",
      "resources/agent4j-web-dist.tar.gz"
    ]
  }
}
```

### 权限配置 (capabilities/default.json)

```json
{
  "permissions": [
    "core:path:default",
    "core:path:allow-resolve",
    "shell:allow-execute",
    "shell:allow-spawn",
    "shell:allow-kill"
  ]
}
```

## 故障排除

### 1. 服务启动失败

检查日志：
```bash
# Windows
%USERPROFILE%\.agent4j\logs\agent4j.log

# Linux/macOS
~/.agent4j/logs/agent4j.log
```

### 2. 端口被占用

如果 8097 端口被占用，修改 `~/.agent4j/config.json`：
```json
{
  "port": 8098
}
```

同时修改前端 API 地址 `agent4j-front/src/services/tauri.js`：
```javascript
getBaseUrl() {
  return 'http://localhost:8098'
}
```

### 3. Java 未安装

确保系统已安装 Java 17+：
```bash
java -version
```

## 开发调试

### 查看 Rust 日志

```bash
RUST_LOG=debug pnpm tauri dev
```

### 查看 Agent4j Web 日志

```bash
# 实时查看
tail -f ~/.agent4j/logs/agent4j.log
```

### 手动测试服务

```bash
# 启动服务
java -jar ~/.agent4j/bin/agent4j-web.jar

# 测试健康检查
curl http://localhost:8097/api/health
```

## 更新 Agent4j Web

当 Agent4j Web 有更新时：

1. 重新运行构建脚本
2. 删除旧安装：`rm -rf ~/.agent4j/bin`
3. 重新启动应用，会自动安装新版本

或者手动安装：
```bash
# 解压新版本
tar -xzf agent4j-web-dist.tar.gz -C ~/.agent4j
```

## 注意事项

1. **首次启动较慢**：需要解压和启动 Java 服务
2. **内存占用**：Java 服务会占用一定内存（约 200-500MB）
3. **端口冲突**：确保 8097 端口未被占用
4. **防火墙**：可能需要允许 Java 网络访问

## 许可证

MIT License
