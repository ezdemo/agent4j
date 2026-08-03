# Loopra 项目记忆

本文件由 Loopra 自动维护，记录跨会话沉淀的项目关键事实（架构决策、约定、踩坑、用户偏好等）。
会话启动时自动注入 AI 上下文。可手动删除过期条目，请勿手动修改条目格式。

---
## [2026-08-03 19:30] 会话折叠沉淀

桌面首页（DesktopHome.vue）左下角菜单最终布局（2026-08-03 二次调整）：第一行「技能」整行文字按钮；第二行「设置」文字按钮（flex:1 占满左侧）+ 右侧图标区（服务进程管理、工具图标 desktop-tools-button、主题切换 desktop-theme-button）。「子代理/数据面板」入口在设置页左侧导航底部「功能」区（仅 ?desktopShell=1 时显示），通过 emit open-sub-agents/open-dashboard 由 DesktopShell 切换视图。

## [2026-08-03 19:52] 会话折叠沉淀

桌面端标题栏（DesktopShell.vue .desktop-window-controls）有"检查更新"按钮：启动立即检查+每30分钟定时，通过 window.electronAPI.getElectronVersion()（app.getVersion()）对比后端 /api/version/check 的 latestVersion 判断桌面端是否有新版；有新版时高亮按钮点击跳 GitHub releases（releaseUrl 优先，兜底 RELEASE_LATEST_URL，openExternal 打开）。

## [2026-08-03 20:08] 会话折叠沉淀

桌面首页（DesktopHome.vue）左下角菜单最终布局（2026-08-03 第三次调整）：第一行「技能」整行文字按钮（sparkles 四角星图标）；第二行「设置」文字按钮（flex:1 占满左侧）+ 右侧图标区顺序：子代理（desktop-sub-agents-button）、服务进程管理、工具（desktop-tools-button，Lucide wrench）、主题切换（desktop-theme-button）。「数据面板」入口仍在设置页左侧导航底部「功能」区（仅 ?desktopShell=1 时显示）。

## [2026-08-03 20:17] 会话折叠沉淀

子代理角色配置已从硬编码枚举改为可持久化：新增 SubAgentProfileStore（loopra-bin，Solon 组件）以 ~/.loopra/sub-agents.json 按 id 覆盖内置默认（SubAgentProfile 枚举保留为默认值来源，含 name/description/readOnly/instructions/allowedTools 字段）；每次读取实时加载文件，改配置立即生效无需重启；allowedTools 显式配置时优先于 readOnly 过滤；SubAgentTool 与 SubAgentController 均走 Store，SubAgentInfoDTO 新增 name/description 字段，前端 SubAgents.vue 优先展示配置值。

## [2026-08-03 20:25] 会话折叠沉淀

子代理角色配置最终模型（2026-08-03）：~/.loopra/sub-agents.json 为权威存储，首次访问自动生成默认文件（内置 5 角色 enable=true，id 稳定不重复合并）；用户可改 name/description/instructions/readOnly/allowedTools 或 enable=false 禁用；读取实时加载，改文件立即生效；文件损坏回退内置默认且不覆盖用户文件；配置缺 enable 字段视为启用（Boolean 包装类）。相关测试：SubAgentProfileStoreTest（loopra-bin，临时 user.home 隔离）。

## [2026-08-03 20:38] 会话折叠沉淀

子代理前端编辑功能（2026-08-03）：GET /api/sub-agents 返回全部角色（含 enable=false，供重新启用），DTO 含 allowedTools（原始白名单配置）与 enable；PUT /api/sub-agents 全量保存（body {profiles:[...]}，参照 prompt-presets 模式）；SubAgentProfileStore.save 校验 id 非空/不重复、id 归一化小写、allowedTools 空列表归一化为 null；SubAgents.vue 支持编辑（名称/描述/提示词/只读/工具白名单/启用开关，取消时快照恢复）、新增角色（id 可编辑，保存后不可改）、禁用/启用、移除未保存的新角色；保存后重新加载。

## [2026-08-03 20:53] 会话折叠沉淀

子代理工具选择面板（2026-08-03）：GET /api/sub-agents/denied-tools 返回 SUB_AGENT_DENY 清单（sub_agent/checklist_*/goal_*/ask_choice/browser_request_user_action）；前端 chips 面板用 GET /api/tools 全量工具对象（readOnly = readOnlyOverride ?? readOnly，与 ToolMetadata.isReadOnly 一致），子代理不可用或已禁用的工具置灰不可选；「一键导入只读/写入」按实际只读性过滤并排除不可用工具，替换当前白名单。

## [2026-08-03 21:09] 会话折叠沉淀

子代理配置同步策略（2026-08-03，用户拍板"内置为准"）：Java 内置角色（SubAgentProfile 枚举）内容字段（name/description/instructions/readOnly/allowedTools）为权威，进程内首次访问 SubAgentProfileStore 时执行一次 mergeBuiltins：内容字段强制覆盖为内置值、enable 保留用户设置、被删的内置角色自动追加（enable=true）、自定义角色不动；merge 后写盘。DTO 新增 builtin 字段，前端内置角色不显示编辑按钮（仅启用/禁用），带"系统内置"标记。注意：全量保存（PUT）仍可提交内置角色内容，重启 merge 会纠正回内置值。

## [2026-08-03 21:34] 会话折叠沉淀

子代理独立渠道模型（2026-08-03）：SubAgentProfileConfig 新增 modelChannel（渠道 id）/model（渠道内模型名，可空=渠道默认模型=渠道第一个模型条目，空渠道用全局 model）；SubAgentTool.resolveSubClient 按角色渠道构建独立 HttpModelClient（含 apiKey/protocol/reasoningEffort），渠道不存在或未配置时回退 fork 父级 client；渠道配置视为用户偏好，mergeBuiltins 不覆盖（与 enable 一致）；前端卡片展示区（所有角色含内置）和编辑表单均有渠道/模型下拉（configAPI.getConfig().modelChannels 提供选项），变更即静默全量保存不重载；DTO 新增 modelChannel/model 字段。

## [2026-08-03 21:58] 会话折叠沉淀

踩坑：HttpModelClient 直接把传入字符串当请求 URL，不会补 /chat/completions 后缀。凡按渠道建立 ModelClient 必须用 LoopraConfig.ModelChannel.apiUrl()（toApiUrl 会按 apiProtocol 规范化并补 /chat/completions 或 /responses），不能用 baseUrl()——否则请求发到裸地址（如 POST /v1），OpenAI 兼容网关返回 404 "Invalid URL (POST /v1)"。已修复 SubAgentTool.resolveSubClient（原用 channel.baseUrl()）。

## [2026-08-03 22:19] 会话折叠沉淀

前端 AI 消息数学公式渲染（2026-08-03）：所有 Markdown 渲染统一走 loopra-front/src/utils/highlight.js 的共享 marked 实例 md（+全局 marked），已通过 marked-katex-extension 接入 KaTeX；关键约定：katex 必须用 output:'html'（纯 span 输出），因为消息 HTML 会过 sanitize()（DOMPurify 白名单只放行 span/class/style），默认的 htmlAndMathml 输出中 MathML 标签会被剥离并残留文本；throwOnError:false 保证坏公式降级显示不阻断消息。样式：main.js 引入 katex/dist/katex.min.css（Vite 打包字体，base:'./' 适配 Electron file://），main.css 有 .katex-display 横向滚动。多行块级 $$ 公式需前后空行（marked 块级扩展限制），单行 $$...$$ 与行内 $...$ 无此限制。katex 版本锁定 ^0.16（marked-katex-extension peer <0.18）。测试：src/utils/highlight.test.js。

## [2026-08-03 22:35] 会话折叠沉淀

browser_screenshot 图片能力门控（2026-08-03）：AiBrowserTool.postProcessScreenshot（包级静态，可单测）按 ImageReadTool.supportsImageInput(ctx)（同 read_image 判定：controller.getModelClient() + ModelModalityProvider，无法判断时放行）决定是否附带视口截图；明确不支持时去除 imageUrl/imageDetail、加 imageOmitted 说明字段后仅回传结构化快照，AgentLoop 因无 __LOOPRA_IMAGE_RESULT__ 前缀自动走纯文本 toolResult 回退。附带修复：SubAgentProfileStoreTest 曾 clearProperty("user.home") 污染同 JVM 后续测试，导致 LoopraConfigTest 恢复属性时 NPE，已改为保存原值并恢复。

## [2026-08-03 23:28] 会话折叠沉淀

项目已完成多模块化重构（2026-08-03）：loopra-bin 拆分为 loopra-model（内核：ChatModel 客户端/协议、AgentLoop/LoopraAgent/SubAgent、工具抽象 site.sorghum.loopra.tool、config/session/goal/checklist/workspace/command）、loopra-harness（内置工具 builtin、MCP/LSP/browser/schedule、tool.solon skills、tool.javasource、OpenApiV2Resolver 补丁类）、loopra-acp（bin.acp，仅依赖 model）。loopra-web 依赖 harness+acp（pom 中置顶保证 shade 时补丁类优先）。关键解耦约定：ToolScanUtil 通过 ToolScanProvider SPI 委托，harness 的 SolonToolScanProvider @Init 自动安装；图片结果协议在 ImageToolResult（bin.agent.model）；父输出传递用 ParentOutputHolder（bin.agent.output）；SessionFileChangeTracker 已移到 bin.session；SolonToTools 接口已移到 site.sorghum.loopra.tool。bump-version.ps1 需同步维护 model/harness/acp 三个 pom。

## [2026-08-04 00:49] 会话折叠沉淀

多模块化重构已全量验证（2026-08-04）：mvn clean compile/test/package 全绿（model 217、harness 50、web 31 测试），loopra-web.jar 冒烟 42 工具/健康检查正常；loopra-model 经正则扫描确认对 harness/acp 包零 import。后续修改内核时保持三条解耦红线：工具来源只走 ToolScanProvider SPI、图片结果只走 ImageToolResult 文本协议、父输出只走 ParentOutputHolder；若新增上层能力请在 harness 实现并由 web 聚合。

## [2026-08-04 00:51] 会话折叠沉淀

模块化重构全量验证通过（2026-08-04）：mvn clean compile/test/package 全绿（loopra-model 217、loopra-harness 50、loopra-web 31 个测试，0 失败），loopra-web fat jar 冒烟启动正常（42 个内置工具、/api/system/health=200）；loopra-model 对 harness/acp 包零 import。前端静态资产由 loopra-front 构建后同步到 loopra-web/src/main/resources/static（CI 脚本 .release/ci-sync-web-assets.sh；本地等价操作：pnpm build 后复制 dist/renderer 内容，保留 static/config.json）。loopra-bin 包名（site.sorghum.loopra.bin.*）保留未改，仅物理拆分为多 Maven 模块，故无 JPMS/包名迁移风险。

