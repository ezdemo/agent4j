package site.sorghum.agent4j.bin.config;

import org.noear.snack4.ONode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
                    + "  \"workspaceDir\": \".\"\n"
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
        return r != null ? r : "max";
    }

    /**
     * 获取界面语言。
     * ZH = 中文，EN = 英文。默认为 EN。
     */
    public String lang() {
        String l = root.select("$.lang").getString();
        return l != null ? l : "EN";
    }

    @Override
    public String toString() {
        return "Agent4jConfig{baseUrl=" + baseUrl() + ", model=" + model()
                + ", workspace=" + workspaceDir() + "}";
    }
}
