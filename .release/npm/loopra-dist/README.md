# loopra-dist

Loopra 核心分发包（Java 运行时）的 npm 镜像资产。

包内**原样收纳** GitHub Releases 上的 [`loopra-dist.tar.gz`](https://github.com/ezdemo/loopra/releases)，安装脚本与自动化流程可直接从 npm registry（含 npmmirror 等国内镜像）拉取，作为 GitHub 直连/镜像之外的备用下载源。

## 用法

### 方式一：自动安装（默认，推荐）

`npm install` 即会解压分发包，并按当前平台自动执行包内自带的安装器（Windows → `install.ps1`，macOS/Linux → `install.sh`），等效于官方一键安装脚本：

```bash
# 全局安装并自动安装到 ~/.loopra
npm install -g loopra-dist

# 或作为依赖安装，同时触发自动安装
npm install loopra-dist
```

> 安装器会复用系统 Java 17+ 或已有捆绑 JRE，都没有时自动下载 JRE 25；可用 `LOOPRA_MIRROR` 指定镜像加速下载。
> 不需要自动安装时，用 npm 官方逃生门跳过 postinstall：`npm install --ignore-scripts`

### 方式二：作为下载源

```bash
# 下载并解包到当前目录
npm pack loopra-dist
tar -xzf loopra-dist-*.tgz
tar -xzf package/loopra-dist.tar.gz -C <安装目录>
```

也适合作为依赖安装：

```bash
npm install loopra-dist@<版本>
```

然后从 `node_modules/loopra-dist/loopra-dist.tar.gz` 取用分发包。

## 版本规则

版本号与 Loopra 主版本的前三段一致（如 `26.8.231`）；当主版本为四段（如 `26.8.231.1`）时，npm 侧取前三段 `26.8.231`（npm 要求 semver）。

## 内容

- `loopra-dist.tar.gz`：Loopra 核心 Java 运行时分发包，跨平台（win/mac/linux），解压即用（含各平台安装器）。
- `scripts/install.js`：postinstall 自动安装器（零依赖，npm install 时自动生效，`--ignore-scripts` 可跳过）。

## License

MIT