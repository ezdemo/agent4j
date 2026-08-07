
# Loopra v26.8 来了：纯 Java 的 AI 编码代理，长大了！

> 模块化内核 · 计划模式 · 多模态视觉 · 桌面端大改 · 官网上线 —— 一个多月的进化，一次讲清楚。

---

## 🤔 还记得 Loopra 吗？

上次见面，它还是个"用纯 Java 17 填补 AI 编码代理生态缺口"的新人。一个多月过去，版本号从 26.6 跑到了 **26.8.40**，变化大到值得重新介绍一遍：

- 内核**拆成了三块乐高**，想嵌多深嵌多深
- AI 学会**先写方案再动手**，人类审批后才开工
- AI 长出了**眼睛**，能看图、能看网页
- 桌面端**安装更新全流程重做**，国内网络也有了加速通道

下面挨个说。

---

## 🧩 最大变化：内核拆成了"三块乐高"

以前 Loopra 是一整个应用，想用它的推理内核？得把整个项目搬走。现在拆成了三个**可独立发布**的 Maven 模块：

| 模块 | 定位 | 什么时候用 |
|------|------|-----------|
| `loopra-model` | 纯内核 | ChatModel 客户端/协议 + AgentLoop 推理循环，只要心脏的自己装配 |
| `loopra-harness` | 工具装备 + 编排 | 开箱即用的 LoopraAgent、内置工具、Goal/工作区/MCP/Skill |
| `loopra-acp` | 协议支持 | 把 Agent 暴露为 ACP Agent（stdio / WebSocket） |

依赖关系一句话：`loopra-model ← loopra-harness ← loopra-acp`，按需取用。

内核和上层之间用 SPI 倒置解耦（`ToolScanProvider`、`AgentConfig`、`GoalGuard` 等注入点），`loopra-model` 不依赖任何上层模块——**想在自己的 Java 服务里嵌一个推理循环，现在只需要一个依赖**。

---

## 🗺️ 计划模式：先探索、再审查、批准后开工

AI 直接改代码心里没底？计划模式就是为这个场景设计的：

```
输入框一键进入 → AI 只读探索（不改任何文件） → 提交计划 → 你审查 → 批准 → 按计划执行
```

- 探索阶段**严格只读**，随便它翻代码
- 计划以步骤化面板展示，含概述和风险说明
- 批准之前，一行代码都不会动

老用户注意：`/plan`、`/execute` 斜杠命令已退役，现在全部走输入框按钮 + 审查面板，交互更顺。

---

## 👀 AI 长出了"眼睛"

| 能力 | 说明 |
|------|------|
| `read_image` | 读工作区图片、绝对路径、Base64/data URI、HTTP(S) URL，单张最大 5 MiB |
| 浏览器截图增强 | 视口截图 + 结构化页面快照 + 可操作元素清单，`snapshotId` 防过期状态 |
| 能力自适应 | 当前模型不支持图片输入时明确提示，不装懂 |

配合桌面端可见的 AI 浏览器：登录、验证码这类环节，Agent 会**请你接管**，绝不代填密码——边界感这块拿捏得很到位。

顺带一提，前端现在支持 **KaTeX 数学公式**，`$...$` 行内、`$$...$$` 块级都能渲染，跟 AI 聊算法推导舒服多了。

---

## 🖥️ 桌面端：安装更省心，更新不折腾

- **启动窗口 + 更新窗口**独立成窗，更新流程不再糊在聊天界面上
- **下载源二选一**：GitHub 直连 或 镜像加速，选择会被记住
- 更新面板拆开"更新核心服务"和"更新桌面端"，谁有新版本一目了然
- 工作区（项目）支持**拖拽排序**，常用项目放最前面
- Electron 打包流水线跑通，**macOS / Windows 双平台安装包**直接下

国内网络的朋友，安装也有加速通道了：

```powershell
# Windows（Gitee 镜像）
irm https://raw.giteeusercontent.com/ezdemo/loopra/raw/main/.release/setup-mirror.ps1 | iex
```

```bash
# macOS / Linux（Gitee 镜像）
curl -fsSL https://raw.giteeusercontent.com/ezdemo/loopra/raw/main/.release/setup-mirror.sh | bash
```

镜像脚本与安装包仍从 GitHub 拉取，只是走加速线路，速度起飞。

---

## ✨ 更多用得到的细节

- **Goal 系统强化**：步骤状态流转、暂停/恢复/阻塞/完成，聊天区实时看进度；快照损坏可 `/goal reset` 自救
- **消息队列**：AI 生成到一半想补充要求？消息自动排队，可移除、可引导插队
- **常用要求预设**：个人高频提示词存成预设，点一下直接进输入框
- **OpenAPI V2 支持**：Swagger 2.0 规范也能解析成 API 工具，循环引用自动熔断
- **子代理配置 UI 化**：五种角色（explore / implement / test / review / plan）的提示词、模型渠道，前端编辑、持久化
- **工具校验模型故障不再一票否决**：改为回退人工审批，决定权在你手里

---

## 🌐 官网上线：loopra.cn

官网重新设计并正式上线：**https://loopra.cn**

- 轻量设计系统，与产品端风格统一（中性灰 + 毛玻璃 + 品牌蓝渐变）
- 快速开始重做：时间线式三步引导，直连源 / 镜像加速命令一键复制
- **右上角支持中文 / English 切换**，欢迎转发给海外同事

---

## ⚡ 十秒上手（老规矩）

**Windows：**
```powershell
irm https://raw.giteeusercontent.com/ezdemo/loopra/raw/main/.release/setup.ps1 | iex
```

**macOS / Linux：**
```bash
curl -fsSL https://raw.giteeusercontent.com/ezdemo/loopra/raw/main/.release/setup.sh | bash
```

启动 Web 服务：
```bash
loopra web 0
```

在 Web 设置页填上 API Key，重启，开干。桌面端直接去 Releases 下载安装包。

---

## 💬 技术交流群

一个人折腾太孤单？扫码添加作者个人微信，**备注「loopra」**，拉你进技术交流群：

> 二维码图片见仓库 `img/wx.png`（蓝色渐变圆环边框那张）

群里聊什么：AI 编码代理玩法、Loopra 问题反馈、功能建议、Java × AI 的一切。

---

## 🔗 一键直达

- 官网：**https://loopra.cn**
- GitHub：**https://github.com/ezdemo/loopra**
- Gitee：**https://gitee.com/ezdemo/loopra**
- 桌面端下载：[Releases](https://github.com/ezdemo/loopra/releases/latest)
- 更新日志：[CHANGELOG.md](https://github.com/ezdemo/loopra/blob/main/CHANGELOG.md)

---

> **Loopra** —— 纯 Java 的 AI 编码代理。
> 像同事一样在你的代码库里干活，这次它更懂规矩了。

---

*本文基于 Loopra v26.8.40 撰写 | MIT 开源 | 作者 Sorghum*
