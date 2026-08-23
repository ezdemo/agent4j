# loopra-dist

Loopra 核心分发包（Java 运行时）的 npm 镜像资产。

包内**原样收纳** GitHub Releases 上的 [`loopra-dist.tar.gz`](https://github.com/ezdemo/loopra/releases)，安装脚本与自动化流程可直接从 npm registry（含 npmmirror 等国内镜像）拉取，作为 GitHub 直连/镜像之外的备用下载源。

## 用法

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

- `loopra-dist.tar.gz`：Loopra 核心 Java 运行时分发包，跨平台（win/mac/linux），解压即用。

## License

MIT