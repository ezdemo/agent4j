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

**关于安装进度**：npm 出于安全默认**隐藏**生命周期脚本的输出（安装是正常跑完的）。想看实时进度：

```bash
npm install -g loopra-dist --foreground-scripts
```

无论哪种方式，每一步进度（校验包 → 镜像选择 → 解压 → 定位安装器 → 执行安装 → 完成）都会记录到 `~/.loopra/install.log`；安装出错时 npm 会自动打印捕获到的脚本输出。

**镜像选择（GitHub 代理镜像）**：未设置 `LOOPRA_MIRROR` 时，postinstall 会**自动测速**：并发 HEAD 探测各候选代理（`gh-proxy.org` / `ghfast.top` / `gh-proxy.com` / `ghproxy.net`），每候选 3 轮取中位延迟，自动选用最快者（GitHub 直连的 HEAD 延迟不代表实际下载可用性，不参与比拼）；全部探测失败则回退 GitHub 直连。测速结果示例：

```bash
[loopra-dist] 测速结果（中位延迟）:
  gh-proxy.org: FAIL
  ghfast.top: 652 ms
  gh-proxy.com: 437 ms
  ghproxy.net: 736 ms
[loopra-dist] [2/7] 自动选择: gh-proxy.com (437 ms)
```

已设置 `LOOPRA_MIRROR` 时直接使用、跳过测速（也支持自定义前缀，如 `https://ghfast.top`）；npm 默认模式 / CI / 管道下同样自动测速，不会弹询问、也不会隐形挂起。

**注意（npm 的安全闸门）**：npm 11.16+ 会提示 allow-scripts 警告（此时脚本仍会执行）；**npm 12 起安装脚本默认被拦截**，首次安装会看到类似提示：

```
npm warn allow-scripts  loopra-dist@x.y.z (postinstall: node scripts/install.js)
```

此时 postinstall 尚未执行，需要先批准一次（写入项目的 `package.json` 的 `allowScripts` 字段）：

```bash
npm approve-scripts loopra-dist
```

批准后重新安装（或 `npm rebuild loopra-dist`）即可触发自动安装。

**关于重复安装**：npm 对已安装且版本未变的包直接跳过（"up to date"），不会重新解压、也不会重跑 postinstall。需要重跑安装器时：

```bash
npm rebuild loopra-dist        # 重跑已装包的 preinstall/install/postinstall
# 或卸载后重装：
npm uninstall -g loopra-dist && npm install -g loopra-dist
# 或升级到新版本（版本变化会自动触发）：
npm install -g loopra-dist@latest
```

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