package site.sorghum.loopra.bin.config;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.noear.snack4.Feature;
import org.noear.snack4.ONode;
import org.noear.snack4.Options;
import org.noear.snack4.json.JsonWriter;
import site.sorghum.loopra.bin.agent.spi.AgentConfig;

import java.io.IOException;
import java.nio.file.*;
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
public class LoopraConfig implements AgentConfig {

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
                  "apiKey": "",
                  "model": "deepseek-v4-flash",
                  "modelChannelId": "default",
                  "validationModel": "",
                  "validationModelChannelId": "",
                  "modelChannels": [
                    {
                      "id": "default",
                      "name": "默认渠道",
                      "baseUrl": "https://api.deepseek.com/v1",
                      "apiKey": "",
                      "apiProtocol": "chat_completions",
                      "models": [
                        { "name": "deepseek-v4-flash", "contextTokens": -1, "imageInput": false },
                        { "name": "deepseek-v4-pro", "contextTokens": -1, "imageInput": false },
                        { "name": "mimo-v2.5", "contextTokens": -1, "imageInput": false },
                        { "name": "mimo-v2.5-pro", "contextTokens": -1, "imageInput": false }
                      ]
                    }
                  ],
                  "editMode": "auto",
                  "reasoningEffort": "high",
                  "lang": "ZH",
                  "hitl": "free",
                  "autoWhitelist": [
                    "resolve-library-id", "query-docs", "skillrefresh", "skilllist",
                    "read", "glob", "write", "ls", "grep", "edit", "finish",
                    "java_source", "checklist_step", "workspace_read", "webfetch",
                    "codesearch", "ask_choice", "browser_request_user_action", "workspace_list", "workspace_write",
                    "call_api", "checklist_start", "sub_agent", "checklist_status",
                    "skillread", "codegraph_explore",
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
     * 命令行安装器会预先创建 {@code ~/.loopra} 并放入运行文件，
     * 因此迁移只使用标记文件判断是否已经执行。除 jre、bin 外，
     * 旧目录中的所有文件都会复制到新目录，并覆盖同名文件。
     */
    private static void migrateLegacyDataIfNeeded(Path configDir) throws IOException {
        Path legacyDir = Paths.get(System.getProperty("user.home"), ".agent4j");
        Path migrationMarker = configDir.resolve(".agent4j-migration-complete");
        if (Files.exists(migrationMarker)
                || !Files.isDirectory(legacyDir)
                || isDirectoryEmpty(legacyDir)) {
            return;
        }

        Files.createDirectories(configDir);
        int[] copiedFiles = {0};
        Files.walkFileTree(legacyDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) throws IOException {
                Path relativePath = legacyDir.relativize(dir);
                String topLevelName = relativePath.getNameCount() == 1
                        ? relativePath.toString().toLowerCase(Locale.ROOT)
                        : "";
                if (topLevelName.startsWith("jre") || "bin".equals(topLevelName)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(configDir.resolve(relativePath));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Path relativePath = legacyDir.relativize(file);
                Path target = configDir.resolve(relativePath);
                Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
                copiedFiles[0]++;
                return FileVisitResult.CONTINUE;
            }
        });
        Files.writeString(migrationMarker, "migrated from " + legacyDir);
        log.info("[config] 已从旧目录全量迁移数据: {} -> {}（覆盖复制 {} 个文件，已跳过 jre* 和 bin）",
                legacyDir, configDir, copiedFiles[0]);
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
            // 在补默认值前转写旧单渠道配置，避免把默认渠道覆盖用户的旧密钥和地址。
            boolean modelChannelsMigrated = migrateLegacyModelChannels(root);
            // 加载后用默认配置补充缺失字段（适配旧版本 config.json）
            mergeDefaults(root, defaultConfigNode());
            boolean defaultWorkspaceInitialized = initializeDefaultWorkspace(root, configDir);
            LoopraConfig config = new LoopraConfig(root);
            if (defaultWorkspaceInitialized || modelChannelsMigrated) {
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
     * 获取 API 基础地址，不含具体接口后缀。
     * 如 "https://api.deepseek.com/v1"。
     */
    public String baseUrl() {
        ModelChannel channel = activeModelChannel();
        return channel != null && !channel.baseUrl().isBlank()
                ? channel.baseUrl()
                : root.select("$.baseUrl").getString();
    }

    /** 当前渠道使用的 API 协议。 */
    public String apiProtocol() {
        ModelChannel channel = activeModelChannel();
        return channel != null ? channel.apiProtocol()
                : normalizeApiProtocol(root.select("$.apiProtocol").getString());
    }
    
    /** 获取当前渠道可直接调用的完整 API URL。 */
    public String apiUrl() {
        return toApiUrl(baseUrl(), apiProtocol());
    }
        
    /** 保留给旧调用方的 Chat Completions URL 构造方法。 */
    public String chatApiUrl() {
        return toApiUrl(baseUrl(), "chat_completions");
    }
    
    private static String toApiUrl(String baseUrl, String apiProtocol) {
        if (baseUrl == null) return null;
        String normalized = baseUrl.trim();
        int queryIndex = normalized.indexOf('?');
        int fragmentIndex = normalized.indexOf('#');
        int suffixIndex = queryIndex < 0 ? fragmentIndex
                : fragmentIndex < 0 ? queryIndex : Math.min(queryIndex, fragmentIndex);
        String suffix = suffixIndex < 0 ? "" : normalized.substring(suffixIndex);
        String endpoint = suffixIndex < 0 ? normalized : normalized.substring(0, suffixIndex);
        endpoint = endpoint.replaceAll("/+$", "")
                .replaceFirst("/(?:chat/completions|responses?|v1/messages|messages)$", "");
        if (endpoint.isEmpty()) return endpoint + suffix;
        String protocol = normalizeApiProtocol(apiProtocol);
        if ("anthropic".equals(protocol)) {
            // Anthropic 标准地址为 {base}/v1/messages；baseUrl 已带 /v1 时不再重复拼接。
            return endpoint + (endpoint.endsWith("/v1") ? "/messages" : "/v1/messages") + suffix;
        }
        return endpoint + ("responses".equals(protocol) ? "/responses" : "/chat/completions") + suffix;
    }
    
    private static String normalizeApiProtocol(String apiProtocol) {
        if (apiProtocol == null) return "chat_completions";
        String normalized = apiProtocol.trim().toLowerCase(Locale.ROOT);
        if ("response".equals(normalized) || "responses".equals(normalized)) {
            return "responses";
        }
        if ("anthropic".equals(normalized) || "claude".equals(normalized)) {
            return "anthropic";
        }
        return "chat_completions";
    }

    /**
     * 获取 API Key。
     */
    public String apiKey() {
        ModelChannel channel = activeModelChannel();
        return channel != null && !channel.apiKey().isBlank()
                ? channel.apiKey()
                : root.select("$.apiKey").getString();
    }

    /**
     * 获取模型名称。
     * config.json 中无此字段时返回默认值 "deepseek-v4-flash"。
     */
    public String model() {
        String m = root.select("$.model").getString();
        return m != null ? m : "deepseek-v4-flash";
    }

    /** 用于危险工具调用校验的独立模型名称；空值表示不启用。 */
    public String validationModel() {
        return trim(root.select("$.validationModel").getString());
    }

    /** 校验模型所属渠道 ID。 */
    public String validationModelChannelId() {
        return trim(root.select("$.validationModelChannelId").getString());
    }

    /** 查询校验模型所属渠道。 */
    public ModelChannel validationModelChannel() {
        return modelChannel(validationModelChannelId());
    }

    /** 当前选中模型所属的渠道 ID。 */
    public String modelChannelId() {
        ModelChannel channel = activeModelChannel();
        return channel != null ? channel.id() : "default";
    }

    /** 多渠道模型配置。每个渠道独立持有 API 地址、密钥和模型条目。 */
    public List<ModelChannel> modelChannels() {
        ONode channelsNode = root.select("$.modelChannels");
        List<ModelChannel> channels = new ArrayList<>();
        if (channelsNode == null || !channelsNode.isArray()) return channels;
        for (ONode item : channelsNode.getArray()) {
            String id = trim(item.get("id").getString());
            String name = trim(item.get("name").getString());
            String baseUrl = trim(item.get("baseUrl").getString());
            String apiKey = trim(item.get("apiKey").getString());
            String apiProtocol = normalizeApiProtocol(item.get("apiProtocol").getString());
            if (id.isEmpty()) id = "channel-" + (channels.size() + 1);
            if (name.isEmpty()) name = "渠道 " + (channels.size() + 1);
            channels.add(new ModelChannel(id, name, baseUrl, apiKey, apiProtocol, modelEntries(item.get("models"))));
        }
        return channels;
    }

    /** 按 ID 查询渠道。 */
    public ModelChannel modelChannel(String channelId) {
        if (channelId == null || channelId.isBlank()) return null;
        return modelChannels().stream().filter(channel -> channel.id().equals(channelId)).findFirst().orElse(null);
    }

    /** 按渠道和名称查询模型条目。 */
    public ModelEntry modelEntry(String channelId, String modelName) {
        ModelChannel channel = modelChannel(channelId);
        return channel == null ? null : channel.modelEntry(modelName);
    }

    /** 查询当前渠道中当前选中模型的条目。 */
    public ModelEntry activeModelEntry() {
        ModelChannel channel = activeModelChannel();
        return channel == null ? null : channel.modelEntry(model());
    }

    /** 当前渠道优先使用 modelChannelId；旧配置缺失时按当前模型归属兜底。 */
    public ModelChannel activeModelChannel() {
        List<ModelChannel> channels = modelChannels();
        if (channels.isEmpty()) return null;
        String selectedId = trim(root.select("$.modelChannelId").getString());
        for (ModelChannel channel : channels) {
            if (channel.id().equals(selectedId)) return channel;
        }
        String selectedModel = model();
        for (ModelChannel channel : channels) {
            if (channel.models().contains(selectedModel)) return channel;
        }
        return channels.get(0);
    }

    /** 配置文件中的单个模型渠道。models() 保持名称列表以兼容已有调用。 */
    public record ModelChannel(String id, String name, String baseUrl, String apiKey, String apiProtocol,
                               List<ModelEntry> modelEntries) implements AgentConfig.Channel {
        public ModelChannel(String id, String name, String baseUrl, String apiKey, List<ModelEntry> modelEntries) {
            this(id, name, baseUrl, apiKey, "chat_completions", modelEntries);
        }
            
        public ModelChannel {
            id = id == null ? "" : id;
            name = name == null ? "" : name;
            baseUrl = baseUrl == null ? "" : baseUrl;
            apiKey = apiKey == null ? "" : apiKey;
            apiProtocol = normalizeApiProtocol(apiProtocol);
            modelEntries = modelEntries == null ? List.of() : List.copyOf(modelEntries);
        }
        
        public List<String> models() {
            return modelEntries.stream().map(ModelEntry::name).toList();
        }
        
        /** 返回该渠道可直接调用的完整地址。 */
        public String apiUrl() {
            return toApiUrl(baseUrl, apiProtocol);
        }
        
        /** 返回该渠道的 Chat Completions 地址。 */
        public String chatApiUrl() {
            return toApiUrl(baseUrl, "chat_completions");
        }

        public ModelEntry modelEntry(String modelName) {
            if (modelName == null || modelName.isBlank()) return null;
            return modelEntries.stream().filter(entry -> entry.name().equals(modelName)).findFirst().orElse(null);
        }
    }

    /** 配置文件中的单个模型条目。价格单位为每百万 token 的人民币金额。 */
    public record ModelEntry(String name, int contextTokens, boolean imageInput, Map<String, Double> price)
            implements AgentConfig.Entry {
        public ModelEntry {
            name = trim(name);
            contextTokens = contextTokens > 0 ? contextTokens : -1;
            price = price == null ? Map.of() : Map.copyOf(price);
        }
    }

    private static List<ModelEntry> modelEntries(ONode modelsNode) {
        List<ModelEntry> entries = new ArrayList<>();
        if (modelsNode == null || !modelsNode.isArray()) return entries;
        for (ONode modelNode : modelsNode.getArray()) {
            if (modelNode.isObject()) {
                String name = trim(modelNode.get("name").getString());
                if (!name.isEmpty()) {
                    entries.add(new ModelEntry(name, modelNode.get("contextTokens").getInt(),
                            modelNode.get("imageInput").getBoolean(), priceMap(modelNode.get("price"))));
                }
            } else {
                String name = trim(modelNode.getString());
                if (!name.isEmpty()) entries.add(new ModelEntry(name, -1, false, Map.of()));
            }
        }
        return entries;
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
     * 获取工作树隔离模式的根目录。
     * 未配置时默认 {@code ~/.loopra/worktree}。
     */
    public String worktreeBaseDir() {
        String dir = root.select("$.worktreeBaseDir").getString();
        if (dir == null || dir.isBlank()) {
            return Paths.get(System.getProperty("user.home"), ".loopra", "worktree").toString();
        }
        return Paths.get(dir).toAbsolutePath().toString();
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
     * 获取用户设置的工具只读分类覆盖。
     * key 为工具名，value=true 表示只读，false 表示写入；未出现的工具沿用自身元数据或内置默认值。
     */
    public Map<String, Boolean> toolReadOnlyOverrides() {
        ONode object = root.select("$.toolReadOnlyOverrides");
        Map<String, Boolean> result = new LinkedHashMap<>();
        if (object != null && object.isObject()) {
            for (Map.Entry<String, ONode> entry : object.getObject().entrySet()) {
                if (entry.getValue().isBoolean()) {
                    result.put(entry.getKey(), entry.getValue().getBoolean());
                }
            }
        }
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
        if (priceNode != null && priceNode.isObject()) {
            for (Map.Entry<String, ONode> entry : priceNode.getObject().entrySet()) {
                Map<String, Double> rates = priceMap(entry.getValue());
                if (!rates.isEmpty()) result.put(entry.getKey(), rates);
            }
        }
        // 渠道内价格优先于旧根 price；仅当前渠道生效，避免同名模型跨渠道串价。
        ModelChannel activeChannel = activeModelChannel();
        if (activeChannel != null) {
            for (ModelEntry entry : activeChannel.modelEntries()) {
                if (!entry.price().isEmpty()) result.put(entry.name(), entry.price());
            }
        }
        return result;
    }

    private static Map<String, Double> priceMap(ONode node) {
        Map<String, Double> rates = new LinkedHashMap<>();
        if (node == null || node.isNull() || !node.isObject()) return rates;
        for (String field : new String[]{"input", "cache", "output"}) {
            ONode value = node.get(field);
            if (value != null && !value.isNull()) {
                try {
                    rates.put(field, Double.parseDouble(value.getString()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return rates;
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
     * 是否启用 OpenAI 快速模式（service_tier=fast）。
     * 仅对 OpenAI 协议（chat_completions / responses）生效，其他协议忽略。默认 false。
     */
    public boolean fastMode() {
        ONode n = root.select("$.fastMode");
        return n != null && !n.isNull() && n.getBoolean();
    }

    /**
     * 获取最大自愈尝试次数（每回合重置）
     */
    public int maxSelfCorrectionAttempts() {
        ONode n = root.select("$.maxSelfCorrectionAttempts");
        return n != null && !n.isNull() ? n.getInt() : 5;
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
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (ModelChannel channel : modelChannels()) result.addAll(channel.models());
        if (!result.isEmpty()) return new ArrayList<>(result);
        ONode arr = root.select("$.availableModels");
        if (arr != null && arr.isArray()) for (ONode item : arr.getArray()) {
            String value = trim(item.getString());
            if (!value.isEmpty()) result.add(value);
        }
        return new ArrayList<>(result);
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

            // 跳过空值；校验模型字段允许空字符串以关闭功能；前端回传脱敏密钥时保留现有真实值。
            if (value == null) continue;
            if (value instanceof String str
                    && ((str.isEmpty() && !"validationModel".equals(key) && !"validationModelChannelId".equals(key))
                    || ("apiKey".equals(key) && str.contains("****")))) continue;

            // 更新到 ONode
            if ("modelChannels".equals(key) && value instanceof List<?> list) {
                root.set(key, modelChannelsNode(mergeModelChannelUpdates(list)));
            } else if (value instanceof List<?> list) {
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
                syncLegacyModelChannelField(key, value);
            }
        }

        // 保存到文件
        save();
    }

    private static boolean migrateLegacyModelChannels(ONode config) {
        ONode channels = config.select("$.modelChannels");
        if (channels != null && channels.isArray() && !channels.getArray().isEmpty()) {
            boolean requiresNormalization = false;
            for (ONode channel : channels.getArray()) {
                if (channel.get("apiProtocol").isNull()) {
                    requiresNormalization = true;
                }
                ONode models = channel.get("models");
                if (models != null && models.isArray()) {
                    for (ONode model : models.getArray()) {
                        if (!model.isObject() || model.get("name") == null
                                || model.get("contextTokens") == null || model.get("imageInput") == null) {
                            requiresNormalization = true;
                            break;
                        }
                    }
                }
                if (requiresNormalization) break;
            }
            if (!requiresNormalization) return false;

            List<Map<String, Object>> normalized = new ArrayList<>();
            for (ONode channel : channels.getArray()) {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("id", trim(channel.get("id").getString()));
                value.put("name", trim(channel.get("name").getString()));
                value.put("baseUrl", trim(channel.get("baseUrl").getString()));
                value.put("apiKey", trim(channel.get("apiKey").getString()));
                value.put("apiProtocol", normalizeApiProtocol(channel.get("apiProtocol").getString()));
                value.put("models", modelEntries(channel.get("models")));
                normalized.add(value);
            }
            config.set("modelChannels", modelChannelsNode(normalized));
            return true;
        }
        String baseUrl = trim(config.select("$.baseUrl").getString());
        String apiKey = trim(config.select("$.apiKey").getString());
        String model = trim(config.select("$.model").getString());
        List<ModelEntry> models = new ArrayList<>();
        ONode legacyModels = config.select("$.availableModels");
        if (legacyModels != null && legacyModels.isArray()) for (ONode item : legacyModels.getArray()) {
            String value = trim(item.getString());
            if (!value.isEmpty()) models.add(new ModelEntry(value, -1, false, Map.of()));
        }
        if (!model.isEmpty() && models.stream().noneMatch(entry -> entry.name().equals(model))) {
            models.add(0, new ModelEntry(model, -1, false, Map.of()));
        }
        if (models.isEmpty()) models.add(new ModelEntry("deepseek-v4-flash", -1, false, Map.of()));
        Map<String, Object> channel = new LinkedHashMap<>();
        channel.put("id", "default");
        channel.put("name", "默认渠道");
        channel.put("baseUrl", baseUrl);
        channel.put("apiKey", apiKey);
        channel.put("apiProtocol", normalizeApiProtocol(config.select("$.apiProtocol").getString()));
        channel.put("models", models);
        config.set("modelChannels", modelChannelsNode(List.of(channel)));
        config.set("modelChannelId", "default");
        return true;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private List<Map<String, Object>> mergeModelChannelUpdates(List<?> submittedChannels) {
        Map<String, ModelChannel> existing = modelChannels().stream()
                .collect(Collectors.toMap(ModelChannel::id, channel -> channel, (left, right) -> left, LinkedHashMap::new));
        List<Map<String, Object>> result = new ArrayList<>();
        int index = 0;
        for (Object raw : submittedChannels) {
            if (!(raw instanceof Map<?, ?> input)) continue;
            index++;
            String id = trim(Objects.toString(input.get("id"), ""));
            if (id.isEmpty()) id = "channel-" + index;
            ModelChannel previous = existing.get(id);
            String name = trim(Objects.toString(input.get("name"), ""));
            String baseUrl = trim(Objects.toString(input.get("baseUrl"), ""));
            String apiKey = trim(Objects.toString(input.get("apiKey"), ""));
            String apiProtocol = input.containsKey("apiProtocol")
                    ? normalizeApiProtocol(Objects.toString(input.get("apiProtocol"), ""))
                    : previous != null ? previous.apiProtocol() : "chat_completions";
            if ((apiKey.isEmpty() || apiKey.contains("****")) && previous != null) apiKey = previous.apiKey();
            List<ModelEntry> models = modelEntries(input.get("models"));
            Map<String, Object> channel = new LinkedHashMap<>();
            channel.put("id", id);
            channel.put("name", name.isEmpty() ? "渠道 " + index : name);
            channel.put("baseUrl", baseUrl.isEmpty() && previous != null ? previous.baseUrl() : baseUrl);
            channel.put("apiKey", apiKey);
            channel.put("apiProtocol", apiProtocol);
            channel.put("models", models);
            result.add(channel);
        }
        return result;
    }

    private void syncLegacyModelChannelField(String key, Object value) {
        if (!("baseUrl".equals(key) || "apiKey".equals(key) || "availableModels".equals(key))) return;
        ONode channels = root.select("$.modelChannels");
        if (channels == null || !channels.isArray()) return;
        String activeId = modelChannelId();
        for (ONode channel : channels.getArray()) {
            if (!activeId.equals(channel.get("id").getString())) continue;
            if ("availableModels".equals(key) && value instanceof List<?> models) {
                channel.set("models", modelEntriesNode(modelEntries(models)));
            } else {
                channel.set(key, value == null ? "" : value.toString());
            }
            return;
        }
    }

    private static ONode modelChannelsNode(List<? extends Map<String, Object>> channels) {
        ONode array = ONode.ofJson("[]");
        for (Map<String, Object> channel : channels) {
            ONode node = ONode.ofJson("{}");
            node.set("id", Objects.toString(channel.get("id"), ""));
            node.set("name", Objects.toString(channel.get("name"), ""));
            node.set("baseUrl", Objects.toString(channel.get("baseUrl"), ""));
            node.set("apiKey", Objects.toString(channel.get("apiKey"), ""));
            node.set("apiProtocol", normalizeApiProtocol(Objects.toString(channel.get("apiProtocol"), "")));
            node.set("models", modelEntriesNode(modelEntries(channel.get("models"))));
            array.add(node);
        }
        return array;
    }

    private static ONode modelEntriesNode(Collection<ModelEntry> entries) {
        ONode array = ONode.ofJson("[]");
        for (ModelEntry entry : entries) {
            if (entry.name().isEmpty()) continue;
            ONode node = ONode.ofJson("{}");
            node.set("name", entry.name());
            node.set("contextTokens", entry.contextTokens());
            node.set("imageInput", entry.imageInput());
            if (!entry.price().isEmpty()) {
                ONode price = node.getOrNew("price").asObject();
                entry.price().forEach(price::set);
            }
            array.add(node);
        }
        return array;
    }

    private static List<ModelEntry> modelEntries(Object rawModels) {
        List<ModelEntry> entries = new ArrayList<>();
        if (!(rawModels instanceof Collection<?> models)) return entries;
        for (Object raw : models) {
            if (raw instanceof ModelEntry entry) {
                entries.add(entry);
            } else if (raw instanceof Map<?, ?> value) {
                String name = trim(Objects.toString(value.get("name"), ""));
                if (!name.isEmpty()) {
                    entries.add(new ModelEntry(name, integerValue(value.get("contextTokens")),
                            booleanValue(value.get("imageInput")), priceMap(value.get("price"))));
                }
            } else {
                String name = trim(Objects.toString(raw, ""));
                if (!name.isEmpty()) entries.add(new ModelEntry(name, -1, false, Map.of()));
            }
        }
        return entries.stream().collect(Collectors.toMap(ModelEntry::name, entry -> entry,
                (left, right) -> right, LinkedHashMap::new)).values().stream().toList();
    }

    private static int integerValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(Objects.toString(value, ""));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static boolean booleanValue(Object value) {
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(Objects.toString(value, "false"));
    }

    private static Map<String, Double> priceMap(Object value) {
        if (!(value instanceof Map<?, ?> input)) return Map.of();
        Map<String, Double> result = new LinkedHashMap<>();
        for (String field : new String[]{"input", "cache", "output"}) {
            Object raw = input.get(field);
            if (raw == null) continue;
            try {
                result.put(field, raw instanceof Number number ? number.doubleValue() : Double.parseDouble(raw.toString()));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
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
