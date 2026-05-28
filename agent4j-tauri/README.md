# Agent4j Tauri - 桌面端 AI 代码助手

基于 [Tauri](https://tauri.app/) 构建的桌面端应用，复用 `agent4j-front` 前端项目。

## 项目结构

```
agent4j-tauri/
├── package.json              # Node.js 配置（Tauri CLI 依赖）
├── README.md                 # 本文件
│
└── src-tauri/                # Rust 后端
    ├── Cargo.toml            # Rust 依赖配置
    ├── tauri.conf.json       # Tauri 配置（窗口/构建/权限）
    ├── build.rs              # Rust 构建脚本
    ├── icons/                # 应用图标
    └── src/
        ├── main.rs           # Rust 入口
        └── lib.rs            # Rust 库（Tauri 命令）
```

## 前置条件

1. **Node.js** >= 18.0.0
2. **pnpm** >= 8.0.0
3. **Rust** >= 1.70.0（[安装 Rust](https://rustup.rs/)）
4. **系统依赖**（Linux）:
   ```bash
   sudo apt update
   sudo apt install -y libgtk-3-dev libwebkit2gtk-4.0-dev libappindicator3-dev librsvg2-dev patchelf
   ```

## 安装依赖

```bash
# 安装 Tauri CLI
cd agent4j-tauri
pnpm install
```

## 开发模式

```bash
# 启动开发服务器（自动启动 Vite + Tauri 窗口）
pnpm dev
```

此命令会：
1. 启动 `agent4j-front` 的 Vite 开发服务器（localhost:3000）
2. 启动 Tauri 窗口，加载 Vite 开发服务器

## 构建发布版

```bash
# 构建生产版本
pnpm build
```

构建产物位于 `src-tauri/target/release/bundle/`，包含：
- **Windows**: `.msi` 安装包
- **macOS**: `.dmg` 安装包
- **Linux**: `.deb` / `.AppImage`

## 配置说明

### tauri.conf.json

| 配置项 | 说明 |
|--------|------|
| `build.beforeDevCommand` | 开发模式启动前执行的命令 |
| `build.beforeBuildCommand` | 构建前执行的命令 |
| `build.devPath` | 开发服务器地址 |
| `build.distDir` | 前端构建产物目录 |
| `tauri.windows` | 窗口配置（尺寸/标题/装饰） |
| `tauri.bundle` | 打包配置（图标/标识符/目标格式） |

### API 代理

Tauri 桌面端默认连接后端 API：

- 开发模式：通过 Vite 代理 `http://localhost:8097`
- 生产模式：需要确保后端服务正在运行

## 自定义窗口

在 `tauri.conf.json` 中修改窗口配置：

```json
{
  "tauri": {
    "windows": [
      {
        "title": "Agent4j",
        "width": 1200,
        "height": 800,
        "minWidth": 800,
        "minHeight": 600,
        "resizable": true,
        "center": true
      }
    ]
  }
}
```

## 应用图标

在 `src-tauri/icons/` 目录下放置以下尺寸的图标：

| 文件名 | 尺寸 | 用途 |
|--------|------|------|
| `32x32.png` | 32x32 | Windows 任务栏 |
| `128x128.png` | 128x128 | Linux 图标 |
| `128x128@2x.png` | 256x256 | macOS Retina |
| `icon.icns` | - | macOS 应用图标 |
| `icon.ico` | - | Windows 应用图标 |

可以使用 [Tauri 图标生成工具](https://tauri.app/v1/guides/features/icons) 自动生成。

## 常见问题

### 1. Linux 编译失败

确保安装了所有系统依赖：

```bash
sudo apt install -y libgtk-3-dev libwebkit2gtk-4.0-dev libappindicator3-dev librsvg2-dev patchelf
```

### 2. 无法连接后端 API

确保 Agent4j 后端服务正在运行（默认端口 8097）：

```bash
cd ../
mvn compile -pl agent4j-bin
java -cp agent4j-bin/target/classes:agent4j-tool/target/classes \
  site.sorghum.agent4j.bin.Agent4jApp
```

### 3. 开发模式窗口空白

检查 Vite 开发服务器是否正常启动：

```bash
cd ../agent4j-front
pnpm dev
```

## 相关链接

- [Tauri 官方文档](https://tauri.app/v1/guides/)
- [Tauri GitHub](https://github.com/tauri-apps/tauri)
- [agent4j-front](../agent4j-front/) - 前端项目

## 许可证

MIT
