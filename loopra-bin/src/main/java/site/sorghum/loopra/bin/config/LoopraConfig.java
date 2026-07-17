package site.sorghum.loopra.bin.config;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.noear.snack4.Feature;
import org.noear.snack4.ONode;
import org.noear.snack4.Options;
import org.noear.snack4.json.JsonWriter;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 从 {@code ~/.loopra/config.json} 读取 LLM 和工作区配置。
 * 提供静态单例 {@link #getInstance()} 供各处访问。
 *
 * @author Sorghum
 */
@Slf4j
public class LoopraConfig {

    private static volatile LoopraConfig INSTANCE;

    private final ONode root;

    private LoopraConfig(ONode root) {
        this.root = root;
        migrateRenamedTool(root, "task", "sub_agent");
        migrateRenamedTool(root, "goal_mark_step", "goal_update_step");
    }

    /** 将历史工具名迁移到当前名称，保留用户原有的启用或禁用语义。 */
    private static void migrateRenamedTool(ONode config, String previousName, String currentName) {
        if (config == null || !config.isObject()) return;
        for (String key : new String[]{"autoWhitelist", "disabledTools"}) {
            ONode tools = config.get(key);
            if (tools == null || !tools.isArray()) continue;

            LinkedHashSet<String> migrated = new LinkedHashSet<>();
            boolean changed = false;
            for (ONode item : tools.getArray()) {
                String toolName = item.getString();
                if (previousName.equals(toolName)) {
                    toolName = currentName;
                    changed = true;
                }
                if (toolName != null && !toolName.isEmpty()) {
                    migrated.add(toolName);
                }
            }
            if (changed) {
                tools.clear();
                for (String toolName : migrated) {
                    tools.add(toolName);
                }
            }
        }
    }

    /**
     * 获取配置单例，首次加载会从文件读取。
     */
    public static LoopraConfig getInstance() {
        if (INSTANCE == null) {
            synchronized (LoopraConfig.class) {
                if (INSTANCE == null) {
                    INSTANCE = load();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 强制重新加载配置（热更新场景使用）
     */
    public static synchronized LoopraConfig reload() {
        INSTANCE = load();
        return INSTANCE;
    }

    /**
     * 生成完整的默认配置 JSON
     */
    private static String defaultConfigJson() {
        return """
                {
                  "baseUrl": "https://api.deepseek.com/v1",
                  "apiKey": "sk-your-api-key",
                  "model": "deepseek-v4-flash",
                  "editMode": "auto",
                  "reasoningEffort": "high",
                  "lang": "ZH",
                  "hitl": "free",
                  "autoWhitelist": [
                    "resolve-library-id", "query-docs", "skillrefresh", "skilllist",
                    "read", "glob", "write", "ls", "grep", "edit", "finish",
                    "java_source", "checklist_step", "workspace_read", "webfetch",
                    "codesearch", "ask_choice", "workspace_list", "workspace_write",
                    "call_api", "checklist_start", "sub_agent", "checklist_status",
                    "vision_recognize", "skillread", "codegraph_explore",
                    "goal_create", "goal_status", "goal_update_step", "goal_complete", "goal_block", "goal_resume"
                  ],
                  "maxContextChars": 200000,
                  "keepTailChars": 80000,
                   "toolTimeoutSec": 360,
                   "subAgentTimeoutSec": 3600,
                   "terminateOnNoToolCall": true,
                   "maxSelfCorrectionAttempts": 5,
                  "maxStreamErrorRetries": 10,
                  "flushIntervalSec": 30,
                  "foldHeadCharsLimit": 60000,
                  "stormWindowSize": 6,
                  "stormThreshold": 3,
                  "toolResultTruncateChars": 16000,
                  "toolResultKeepChars": 12000,
                  "vision": {
                    "baseUrl": "https://api.siliconflow.cn/v1/chat/completions",
                    "apiKey": "sk-your-vision-api-key",
                    "model": "Qwen/Qwen3.5-4B"
                  },
                  "price": {
                    "mimo-v2.5": { "input": "1", "cache": "0.02", "output": "2" },
                    "mimo-v2.5-pro": { "input": "3", "cache": "0.025", "output": "6" },
                    "deepseek-v4-flash": { "input": "1", "cache": "0.02", "output": "2" },
                    "deepseek-v4-pro": { "input": "3", "cache": "0.025", "output": "6" }
                  },
                  "disabledTools": ["lsp"],
                  "blockedPaths": [],
                  "availableModels": ["deepseek-v4-flash", "deepseek-v4-pro", "mimo-v2.5", "mimo-v2.5-pro"]
                }""";
    }

    /**
     * 返回默认配置的 ONode
     */
    private static ONode defaultConfigNode() {
        return ONode.ofJson(defaultConfigJson());
    }

    /**
     * 递归合并默认配置到目标节点。
     * 只补充目标中缺失的字段，不覆盖已有的值。
     */
    private static void mergeDefaults(ONode target, ONode defaults) {
        if (defaults == null || !defaults.isObject()) return;
        if (target == null || !target.isObject()) return;

        for (Map.Entry<String, ONode> entry : defaults.getObject().entrySet()) {
            String key = entry.getKey();
            ONode defaultVal = entry.getValue();
            ONode targetVal = target.get(key);

            if (targetVal == null || targetVal.isNull()) {
                // 目标缺失该字段 → 用默认值填充（深度拷贝）
                target.set(key, ONode.ofJson(defaultVal.toJson()));
            } else if (defaultVal.isObject() && !defaultVal.isArray()
                    && targetVal.isObject() && !targetVal.isArray()) {
                // 双方都是对象 → 递归合并
                mergeDefaults(targetVal, defaultVal);
            }
            // 其他情况：保留目标值
        }
    }

    /**
     * 为未配置工作区的用户初始化默认工作区。
     * 默认工作区与配置文件同级，避免使用应用启动目录或用户主目录作为工作区。
     *
     * @return 是否已写入默认工作区配置
     */
    private static boolean initializeDefaultWorkspace(ONode root, Path configDir) throws IOException {
        String workspaceDir = root.select("$.workspaceDir").getString();
        if (workspaceDir != null && !workspaceDir.trim().isEmpty() && !".".equals(workspaceDir.trim())) {
            return false;
        }

        Path defaultWorkspace = configDir.resolve("defaultWorkSpace").toAbsolutePath().normalize();
        Files.createDirectories(defaultWorkspace);
        root.set("workspaceDir", defaultWorkspace.toString());
        log.info("[config] 未配置工作区，已使用默认工作区: {}", defaultWorkspace);
        return true;
    }

    /**
     * 首次启动时迁移旧版 {@code ~/.agent4j} 数据。
     * 仅在目标目录不存在或为空时执行，避免覆盖现有 Loopra 数据。
     */
    private static void migrateLegacyDataIfNeeded(Path configDir) throws IOException {
        Path legacyDir = Paths.get(System.getProperty("user.home"), ".agent4j");
        if (!isDirectoryEmpty(configDir) || !Files.isDirectory(legacyDir) || isDirectoryEmpty(legacyDir)) {
            return;
        }

        Files.createDirectories(configDir);
        Files.walkFileTree(legacyDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) throws IOException {
                Path relativePath = legacyDir.relativize(dir);
                if (relativePath.getNameCount() == 1
                        && ("jre".equals(relativePath.toString()) || "bin".equals(relativePath.toString()))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(configDir.resolve(relativePath));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Path target = configDir.resolve(legacyDir.relativize(file));
                Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
        log.info("[config] 已从旧目录迁移数据: {} -> {}（已跳过 jre 和 bin）", legacyDir, configDir);
    }

    private static boolean isDirectoryEmpty(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return true;
        }
        if (!Files.isDirectory(directory)) {
            return false;
        }
        try (var entries = Files.list(directory)) {
            return entries.findAny().isEmpty();
        }
    }

    /**
     * 从默认路径加载：{@code ~/.loopra/config.json}，首次启动自动创建
     */
    @SneakyThrows
    public static LoopraConfig load(){
        Path configDir = Paths.get(System.getProperty("user.home"), ".loopra");
        migrateLegacyDataIfNeeded(configDir);
        Path configPath = configDir.resolve("config.json");
        if (!Files.exists(configPath)) {
            Files.createDirectories(configDir);
            String defaultConfig = defaultConfigJson();
            Files.writeString(configPath, defaultConfig);
            log.info("[config] 已创建默认配置文件: {}", configPath);
            log.warn("[config] 尚未配置 apiKey，可编辑 {} 或在 Web 设置页配置", configPath);
        }
        try {
            String json = String.join("\n", Files.readAllLines(configPath));
            ONode root = ONode.ofJson(json);
            // 加载后用默认配置补充缺失字段（适配旧版本 config.json）
            mergeDefaults(root, defaultConfigNode());
            boolean defaultWorkspaceInitialized = initializeDefaultWorkspace(root, configDir);
            LoopraConfig config = new LoopraConfig(root);
            if (defaultWorkspaceInitialized) {
                config.save();
            }
            return config;
        } catch (Exception e) {
            throw new IllegalStateException("读取配置文件失败: " + configPath, e);
        }
    }

    /**
     * 获取配置文件路径。
     */
    public static Path getConfigPath() {
        return Paths.get(System.getProperty("user.home"), ".loopra", "config.json");
    }

    /**
     * 获取 API 基础地址，不含 /chat/completions 后缀。
     * 如 "https://api.deepseek.com/v1"。
     */
    public String baseUrl() {
        return root.select("$.baseUrl").getString();
    }

    /**
     * 获取完整的 Chat Completions API URL。
     * 在 baseUrl 后追加 /chat/completions。
     */
    public String chatApiUrl() {
        String base = baseUrl();
        if (base == null) return null;
        return base.endsWith("/") ? base + "chat/completions" : base + "/chat/completions";
    }

    /**
     * 获取 API Key。
     */
    public String apiKey() {
        return root.select("$.apiKey").getString();
    }

    /**
     * 获取模型名称。
     * config.json 中无此字段时返回默认值 "deepseek-v4-flash"。
     */
    public String model() {
        String m = root.select("$.model").getString();
        return m != null ? m : "deepseek-v4-flash";
    }

    /**
     * 获取工作区目录路径。
     * 未配置时返回 null（工作区为空）。
     */
    public Path workspaceDir() {
        String dir = root.select("$.workspaceDir").getString();
        if (dir == null || dir.isEmpty()) {
            return null;
        }
        // 忽略 "."，避免启动时在 CWD 创建工作目录
        if (".".equals(dir.trim())) {
            return null;
        }
        return Paths.get(dir).toAbsolutePath();
    }

    /**
     * 获取编辑模式。
     * auto = 需要用户确认，yolo = 直接执行。默认为 auto。
     */
    public String editMode() {
        String m = root.select("$.editMode").getString();
        return m != null ? m : "auto";
    }

    /**
     * 获取推理力度。
     * 可选：low / medium / high / max。默认为 max。
     */
    public String reasoningEffort() {
        String r = root.select("$.reasoningEffort").getString();
        return r != null ? r : "high";
    }

    /**
     * 获取界面语言。
     * ZH = 中文，EN = 英文。默认为 EN。
     */
    public String lang() {
        String l = root.select("$.lang").getString();
        return l != null ? l : "EN";
    }

    /**
     * 获取 HITL（Human-In-The-Loop）模式。
     * <ul>
     *   <li>{@code "free"} — 自由模式，所有工具直接执行，无需审批</li>
     *   <li>{@code "approval"} — 审批模式，非只读工具执行前需用户审批</li>
     *   <li>{@code "auto"} — 自动模式，自动批准所有工具调用</li>
     * </ul>
     * <p>向后兼容旧的 boolean 配置：{@code true → "approval"}，{@code false → "free"}。</p>
     * 未配置时默认 {@code "free"}。
     */
    public String hitl() {
        ONode n = root.select("$.hitl");
        if (n == null || n.isNull()) return "free";
        // 向后兼容：旧版 config.json 中 hitl 为 boolean 值
        if (n.isBoolean()) {
            return n.getBoolean() ? "approval" : "free";
        }
        String val = n.getString();
        return val != null ? val : "free";
    }

    /**
     * 获取 HITL 自动模式的工具白名单。
     * <p>
     * 仅在 hitl 模式为 {@code "auto"} 时生效。
     * 工具名称匹配白名单中的任一规则时自动放行，否则需走审批流程。
     * 支持 glob 通配符：{@code *} 匹配任意字符序列。
     * </p>
     * <ul>
     *   <li>{@code "*"} — 匹配所有工具（等同于旧版 auto 行为）</li>
     *   <li>{@code "read_*"} — 匹配以 "read_" 开头的工具</li>
     *   <li>{@code "finish"} — 精确匹配 "finish" 工具</li>
     * </ul>
     * <p>未配置或为空时返回空列表，auto 模式下所有非免审工具需走审批。</p>
     */
    public List<String> autoWhitelist() {
        ONode arr = root.select("$.autoWhitelist");
        List<String> result = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            for (ONode item : arr.getArray()) {
                String val = item.getString();
                if (val != null && !val.isEmpty()) {
                    result.add(val);
                }
            }
        }
        // 未配置或为空时返回空列表，auto 模式下等同于全部需审批
        return result;
    }

    /**
     * 获取被禁用的工具列表。
     * 从 config.json 的 disabledTools 数组读取，同时支持 LOOPRA_DISABLED_TOOLS 环境变量（逗号分隔）。
     * 这些工具不会出现在 LLM 的工具列表中，也无法被调用。
     */
    public Set<String> disabledTools() {
        // 环境变量优先级最高
        String env = System.getenv("LOOPRA_DISABLED_TOOLS");
        if (env != null && !env.isEmpty()) {
            return Arrays.stream(env.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
        }
        // 从配置读取
        ONode arr = root.select("$.disabledTools");
        Set<String> result = new LinkedHashSet<>();
        if (arr != null && arr.isArray()) {
            for (ONode item : arr.getArray()) {
                String val = item.getString();
                if (val != null && !val.isEmpty()) {
                    result.add(val);
                }
            }
        }
        return result;
    }

    /**
     * 获取屏蔽目录列表。
     * 从 config.json 的 blockedPaths 数组读取，同时支持 LOOPRA_BLOCKED_PATHS 环境变量（逗号分隔）。
     * 所有文件操作工具（读/写/编辑/搜索）都会跳过这些目录。
     * 路径为相对路径，相对于工作区根目录。
     */
    public List<String> blockedPaths() {
        // 环境变量优先级最高
        String env = System.getenv("LOOPRA_BLOCKED_PATHS");
        if (env != null && !env.isEmpty()) {
            return Arrays.stream(env.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> s.replace('\\', '/'))
                    .collect(Collectors.toList());
        }
        // 从配置读取
        ONode arr = root.select("$.blockedPaths");
        List<String> result = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            for (ONode item : arr.getArray()) {
                String val = item.getString();
                if (val != null && !val.isEmpty()) {
                    result.add(val.replace('\\', '/'));
                }
            }
        }
        return result;
    }

    /**
     * 获取模型价格配置。
     * 从 config.json 的 price 对象读取，格式：
     * { "model-name": { "input": "0.02", "cache": "1", "output": "2" } }
     * 价格单位：每百万 token 的人民币金额。
     */
    public Map<String, Map<String, Double>> price() {
        Map<String, Map<String, Double>> result = new LinkedHashMap<>();
        ONode priceNode = root.select("$.price");
        if (priceNode == null || priceNode.isNull() || !priceNode.isObject()) {
            return result;
        }
        for (Map.Entry<String, ONode> entry : priceNode.getObject().entrySet()) {
            String modelName = entry.getKey();
            ONode val = entry.getValue();
            Map<String, Double> rates = new LinkedHashMap<>();
            for (String field : new String[]{"input", "cache", "output"}) {
                ONode fn = val.get(field);
                if (fn != null && !fn.isNull()) {
                    try {
                        rates.put(field, Double.parseDouble(fn.getString()));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            if (!rates.isEmpty()) {
                result.put(modelName, rates);
            }
        }
        return result;
    }

    // ==================== 可调优参数（原硬编码常量） ====================

    /**
     * 获取消息总字符数阈值（超出时触发折叠），约 200KB
     */
    public int maxContextChars() {
        ONode n = root.select("$.maxContextChars");
        return n != null && !n.isNull() ? n.getInt() : 200_000;
    }

    /**
     * 获取折叠时保留的尾部预算（字符数），约 80KB
     */
    public int keepTailChars() {
        ONode n = root.select("$.keepTailChars");
        return n != null && !n.isNull() ? n.getInt() : 80_000;
    }

    /**
     * 获取工具执行超时（秒），单个工具调用最长等待时间
     */
    public int toolTimeoutSec() {
        ONode n = root.select("$.toolTimeoutSec");
        return n != null && !n.isNull() ? n.getInt() : 360;
    }

    /**
     * 获取子代理完整执行超时（秒）。子代理包含多轮模型请求和工具调用，
     * 因此不使用普通工具的短超时。
     */
    public int subAgentTimeoutSec() {
        ONode n = root.select("$.subAgentTimeoutSec");
        return n != null && !n.isNull() ? n.getInt() : 3600;
    }

    /**
     * 无工具调用时是否将模型文本作为最终回答。
     * false 时追加 FinishTool.TIPS 并要求模型继续调用 finish（连续三次无工具调用后兜底结束）。
     */
    public boolean terminateOnNoToolCall() {
        ONode n = root.select("$.terminateOnNoToolCall");
        return n == null || n.isNull() || n.getBoolean();
    }

    /**
     * 获取最大自愈尝试次数（每回合重置）
     */
    public int maxSelfCorrectionAttempts() {
        ONode n = root.select("$.maxSelfCorrectionAttempts");
        return n != null && !n.isNull() ? n.getInt() : 5;
    }

    /**
     * 获取流式错误最大重试次数
     */
    public int maxStreamErrorRetries() {
        ONode n = root.select("$.maxStreamErrorRetries");
        return n != null && !n.isNull() ? n.getInt() : 10;
    }

    /**
     * 获取定时刷入间隔（秒）
     */
    public int flushIntervalSec() {
        ONode n = root.select("$.flushIntervalSec");
        return n != null && !n.isNull() ? n.getInt() : 30;
    }

    /**
     * 获取折叠头部字符限制
     */
    public int foldHeadCharsLimit() {
        ONode n = root.select("$.foldHeadCharsLimit");
        return n != null && !n.isNull() ? n.getInt() : 60_000;
    }

    /**
     * 获取风暴断路器滑动窗口大小
     */
    public int stormWindowSize() {
        ONode n = root.select("$.stormWindowSize");
        return n != null && !n.isNull() ? n.getInt() : 6;
    }

    /**
     * 获取风暴断路器触发阈值
     */
    public int stormThreshold() {
        ONode n = root.select("$.stormThreshold");
        return n != null && !n.isNull() ? n.getInt() : 3;
    }

    /**
     * 获取 tool 结果截断字符数
     */
    public int toolResultTruncateChars() {
        ONode n = root.select("$.toolResultTruncateChars");
        return n != null && !n.isNull() ? n.getInt() : 16_000;
    }

    /**
     * 获取 tool 结果保留字符数（截断后保留长度）
     */
    public int toolResultKeepChars() {
        ONode n = root.select("$.toolResultKeepChars");
        return n != null && !n.isNull() ? n.getInt() : 12_000;
    }

    /**
     * 获取图片识别服务的 API 基础地址。
     * 从 config.json 的 vision.baseUrl 读取。
     * 未配置时返回 null。
     */
    public String visionBaseUrl() {
        return root.select("$.vision.baseUrl").getString();
    }

    /**
     * 获取图片识别服务的 API Key。
     * 从 config.json 的 vision.apiKey 读取。
     * 未配置时返回 null。
     */
    public String visionApiKey() {
        return root.select("$.vision.apiKey").getString();
    }

    /**
     * 获取图片识别服务的模型名称。
     * 从 config.json 的 vision.model 读取。
     * 未配置时返回 null。
     */
    public String visionModel() {
        return root.select("$.vision.model").getString();
    }

    /**
     * 获取当前活跃宠物名称。
     * 从 config.json 的 activePet 字段读取。
     * 未配置时返回 null。
     */
    public String activePet() {
        return root.select("$.activePet").getString();
    }

    /**
     * 获取可用模型列表。
     * 从 config.json 的 availableModels 数组读取。
     */
    public List<String> availableModels() {
        ONode arr = root.select("$.availableModels");
        List<String> result = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            for (ONode item : arr.getArray()) {
                String val = item.getString();
                if (val != null && !val.isEmpty()) {
                    result.add(val);
                }
            }
        }
        return result;
    }

    /**
     * 合并更新配置（只更新非空字段）。
     * 更新后自动保存到 config.json。
     */
    public void updateAndSave(Map<String, Object> updates) throws IOException {
        if (updates == null || updates.isEmpty()) return;

        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // 跳过空值
            if (value == null) continue;
            if (value instanceof String str && str.isEmpty()) continue;

            // 更新到 ONode
            if (value instanceof List<?> list) {
                ONode arr = root.getOrNew(key).asArray();
                arr.clear();
                for (Object item : list) {
                    if (item != null) {
                        arr.add(item.toString());
                    }
                }
            } else if (value instanceof Map<?, ?> map) {
                // 嵌套对象（如 price）：递归创建子 ONode
                ONode sub = root.getOrNew(key).asObject();
                sub.clear();
                for (Map.Entry<?, ?> me : map.entrySet()) {
                    String subKey = me.getKey() != null ? me.getKey().toString() : "";
                    Object subVal = me.getValue();
                    if (subVal instanceof Map<?, ?> nested) {
                        ONode nestedNode = sub.getOrNew(subKey).asObject();
                        for (Map.Entry<?, ?> ne : nested.entrySet()) {
                            String nk = ne.getKey() != null ? ne.getKey().toString() : "";
                            Object nv = ne.getValue();
                            if (nv instanceof Number num) {
                                nestedNode.set(nk, num.doubleValue());
                            } else {
                                nestedNode.set(nk, nv != null ? nv.toString() : "");
                            }
                        }
                    } else if (subVal instanceof Number num) {
                        sub.set(subKey, num.doubleValue());
                    } else {
                        sub.set(subKey, subVal != null ? subVal.toString() : "");
                    }
                }
            } else {
                root.set(key, value);
            }
        }

        // 保存到文件
        save();
    }

    /**
     * 移除指定配置项并保存。
     *
     * @param key 要移除的配置键
     */
    public void removeAndSave(String key) throws IOException {
        if (key == null || key.isEmpty()) return;
        root.remove(key);
        save();
    }

    /**
     * 保存配置到文件。
     * 保存前自动补充默认配置中缺失的字段，确保新版本新增的配置项自动出现。
     */
    public void save() throws IOException {
        // 保存前补充默认配置中缺失的字段
        mergeDefaults(root, defaultConfigNode());
        migrateRenamedTool(root, "task", "sub_agent");
        migrateRenamedTool(root, "goal_mark_step", "goal_update_step");
        Path configPath = getConfigPath();
        String json = JsonWriter.write(root, Options.of(Feature.Write_PrettyFormat));
        Files.writeString(configPath, json);
    }

    @Override
    public String toString() {
        return "LoopraConfig{baseUrl=" + baseUrl() + ", model=" + model()
                + ", workspace=" + workspaceDir() + "}";
    }
}
