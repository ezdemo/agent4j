# Agent4j Web + Tauri 集成完成

## 已完成的工作

### 1. Agent4j Web 安装脚本系统

创建了类似 soloncode-cli 的安装/卸载脚本系统：

**文件结构：**
```
agent4j-web/
├── pom.xml                              # 添加了 maven-shade-plugin
└── src/
    ├── assembly/
    │   └── dist.xml                     # Assembly 打包配置
    └── dist/
        ├── config.json                  # 默认配置
        ├── agent4j.md                   # 项目文档
        ├── install.ps1                  # Windows 安装脚本
        ├── install.sh                   # Linux/macOS 安装脚本
        └── bin/
            ├── uninstall.ps1            # Windows 卸载脚本
            └── uninstall.sh             # Linux/macOS 卸载脚本
```

**功能特性：**
- ✅ 检查 Java 17+ 安装
- ✅ 备份已有配置文件
- ✅ 自动配置 PATH 环境变量
- ✅ 创建多种启动器（.ps1、.bat、sh）
- ✅ Java 21+ 自动添加 `--enable-native-access`
- ✅ UTF-8 编码支持
- ✅ 软链接支持（Linux/macOS）

### 2. Tauri 集成

将 Agent4j Web 集成到 Tauri 桌面应用：

**Rust 后端 (lib.rs)：**
- ✅ Agent4jWebManager 进程管理器
- ✅ 自动从资源中安装 agent4j-web
- ✅ 应用启动时自动启动服务
- ✅ 应用关闭时自动停止服务
- ✅ 状态查询命令

**前端组件：**
- ✅ SplashScreen.vue 启动画面
- ✅ tauri.js API 服务
- ✅ 自动等待服务就绪

**配置文件：**
- ✅ tauri.conf.json - 添加 resources 配置
- ✅ capabilities/default.json - 添加权限配置
- ✅ Cargo.toml - 添加依赖

**构建脚本：**
- ✅ build-with-web.ps1 (Windows)
- ✅ build-with-web.sh (Linux/macOS)

## 使用说明

### 构建 Agent4j Web 分发包

```bash
# 在项目根目录执行
mvn clean package -pl agent4j-web -DskipTests
```

生成文件：
- `agent4j-web/target/agent4j-web.jar` - 可执行 fat jar
- `agent4j-web/target/agent4j-web-dist.zip` - Windows 分发包
- `agent4j-web/target/agent4j-web-dist.tar.gz` - Linux/macOS 分发包

### 构建 Tauri 应用

```bash
# 1. 先构建 agent4j-web 并复制到 Tauri 资源目录
cd agent4j-tauri

# Windows
.\build-with-web.ps1

# Linux/macOS
./build-with-web.sh

# 2. 构建 Tauri 应用
pnpm tauri build
```

### 安装和运行

**Windows 用户：**
1. 解压 `agent4j-web-dist.zip`
2. 运行 `install.ps1`
3. 运行 `agent4j-web` 命令启动服务

**Linux/macOS 用户：**
1. 解压 `agent4j-web-dist.tar.gz`
2. 运行 `./install.sh`
3. 运行 `agent4j-web` 命令启动服务

**Tauri 桌面应用：**
1. 直接运行 Tauri 应用
2. 首次运行会自动安装 agent4j-web
3. 应用启动时自动启动后端服务
4. 应用关闭时自动停止服务

## 目录结构

### 安装后目录

```
~/.agent4j/
├── config.json          # 配置文件（保留已有）
├── agent4j.md           # 项目文档（保留已有）
├── bin/
│   ├── agent4j-web.jar  # 可执行 JAR
│   ├── agent4j-web      # 启动脚本
│   ├── uninstall.*      # 卸载脚本
│   └── ...
├── sessions/            # 会话历史
├── memory/              # 持久化记忆
└── skills/              # 自定义技能
```

### Tauri 应用目录

```
agent4j-tauri/
├── build-with-web.ps1          # 构建脚本
├── build-with-web.sh
├── src-tauri/
│   ├── resources/              # 资源目录
│   │   ├── agent4j-web-dist.zip
│   │   └── agent4j-web-dist.tar.gz
│   ├── src/
│   │   └── lib.rs              # Rust 后端
│   └── ...
└── ...
```

## API 端点

Agent4j Web 服务默认运行在 `http://localhost:8097`

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/health` | GET | 健康检查 |
| `/api/chat` | POST | 聊天接口 |
| `/api/chat/stream` | POST | SSE 流式聊天 |
| `/api/sessions` | GET | 会话列表 |
| `/api/tools` | GET | 工具列表 |
| `/api/config` | GET | 配置查询 |

## 注意事项

1. **首次启动较慢**：需要解压和启动 Java 服务（约 5-10 秒）
2. **内存占用**：Java 服务会占用约 200-500MB 内存
3. **端口冲突**：确保 8097 端口未被占用
4. **Java 要求**：需要 Java 17 或更高版本
5. **防火墙**：可能需要允许 Java 网络访问

## 故障排除

### 服务启动失败

检查日志：
```bash
# Windows
type %USERPROFILE%\.agent4j\logs\agent4j.log

# Linux/macOS
cat ~/.agent4j/logs/agent4j.log
```

### 端口被占用

修改 `~/.agent4j/config.json`：
```json
{
  "port": 8098
}
```

### 手动测试

```bash
# 启动服务
java -jar ~/.agent4j/bin/agent4j-web.jar

# 测试健康检查
curl http://localhost:8097/api/health
```

## 相关文档

- [TAURI_WEB_INTEGRATION.md](./TAURI_WEB_INTEGRATION.md) - Tauri 集成详细文档
- [临时目录/soloncode-cli/](../临时目录/soloncode-cli/) - 参考的 soloncode-cli 设计

## 许可证

MIT License
