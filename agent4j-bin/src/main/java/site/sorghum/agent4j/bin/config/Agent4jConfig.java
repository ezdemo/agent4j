package site.sorghum.agent4j.bin.config;

import org.noear.snack4.ONode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 从 {@code ~/.agent4j/config.json} 读取 LLM 和工作区配置。
 *
 * @author Sorghum
 */
public class Agent4jConfig {

    private final ONode root;

    private Agent4jConfig(ONode root) {
        this.root = root;
    }

    /** 从默认路径加载：{@code ~/.agent4j/config.json}，首次启动自动创建 */
    public static Agent4jConfig load() throws IOException {
        Path configDir = Paths.get(System.getProperty("user.home"), ".agent4j");
        Path configPath = configDir.resolve("config.json");
        if (!Files.exists(configPath)) {
            Files.createDirectories(configDir);
            String defaultConfig = "{\n"
                    + "  \"baseUrl\": \"http://localhost:11434/v1\",\n"
                    + "  \"apiKey\": \"sk-your-api-key\",\n"
                    + "  \"workspaceDir\": \".\",\n"
                    + "  \"hitl\": false\n"
                    + "}";
            Files.write(configPath, defaultConfig.getBytes(StandardCharsets.UTF_8));
            System.err.println("[config] 已创建默认配置文件: " + configPath);
            System.err.println("[config] 请编辑 " + configPath + " 填入 apiKey 后重启");
            System.exit(1);
        }
        try {
            String json = String.join("\n", Files.readAllLines(configPath));
            return new Agent4jConfig(ONode.ofJson(json));
        } catch (Exception e) {
            throw new IllegalStateException("读取配置文件失败: " + configPath, e);
        }
    }

    /** 加载指定路径的配置。 */
    public static Agent4jConfig load(Path configPath) {
        try {
            String json = String.join("\n", Files.readAllLines(configPath));
            return new Agent4jConfig(ONode.ofJson(json));
        } catch (Exception e) {
            throw new IllegalStateException("读取配置文件失败: " + configPath, e);
        }
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
     * 未配置时默认返回当前目录。
     */
    public Path workspaceDir() {
        String dir = root.select("$.workspaceDir").getString();
        if (dir == null || dir.isEmpty()) {
            return Paths.get(".").toAbsolutePath();
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
     * 获取 HITL（Human-In-The-Loop）模式默认状态。
     * true = 启动时默认开启 HITL，执行非只读工具前需用户审批。
     * false = 默认关闭。未配置时默认 false。
     */
    public boolean hitl() {
        ONode n = root.select("$.hitl");
        if (n == null || n.isNull()) return false;
        return n.getBoolean();
    }

    /**
     * 获取被禁用的工具列表。
     * 从 config.json 的 disabledTools 数组读取，同时支持 AGENT4J_DISABLED_TOOLS 环境变量（逗号分隔）。
     * 这些工具不会出现在 LLM 的工具列表中，也无法被调用。
     */
    public Set<String> disabledTools() {
        // 环境变量优先级最高
        String env = System.getenv("AGENT4J_DISABLED_TOOLS");
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
     * 从 config.json 的 blockedPaths 数组读取，同时支持 AGENT4J_BLOCKED_PATHS 环境变量（逗号分隔）。
     * 所有文件操作工具（读/写/编辑/搜索）都会跳过这些目录。
     * 路径为相对路径，相对于工作区根目录。
     */
    public List<String> blockedPaths() {
        // 环境变量优先级最高
        String env = System.getenv("AGENT4J_BLOCKED_PATHS");
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
    @SuppressWarnings("unchecked")
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
            } else {
                root.set(key, value);
            }
        }
        
        // 保存到文件
        save();
    }

    /**
     * 保存配置到文件。
     */
    public void save() throws IOException {
        Path configPath = getConfigPath();
        String json = root.toJson();
        Files.write(configPath, json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 获取配置文件路径。
     */
    public static Path getConfigPath() {
        return Paths.get(System.getProperty("user.home"), ".agent4j", "config.json");
    }

    @Override
    public String toString() {
        return "Agent4jConfig{baseUrl=" + baseUrl() + ", model=" + model()
                + ", workspace=" + workspaceDir() + "}";
    }
}
