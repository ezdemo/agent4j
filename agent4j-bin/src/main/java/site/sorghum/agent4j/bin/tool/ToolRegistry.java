package site.sorghum.agent4j.bin.tool;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.noear.snack4.ONode;
import org.noear.solon.ai.chat.tool.FunctionTool;
import site.sorghum.agent4j.bin.config.ConfigService;

import java.nio.file.Path;
import java.util.*;

/**
 * 工具注册表 —— 管理工具定义的注册和查询。
 * <p>
 * 职责：工具注册、查询、生成 OpenAI function-calling 格式的工具列表。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
public class ToolRegistry {

    private final LinkedHashMap<String, FunctionTool> functionToolMap = new LinkedHashMap<>();

    /** 所有扫描到的工具（包括禁用的），用于前端展示 */
    private final LinkedHashMap<String, FunctionTool> allScannedTools = new LinkedHashMap<>();

    /** 缓存的 OpenAI tools 格式（refresh 时失效） */
    private ONode cachedOpenAiTools = null;

    /** 静态快照（setDisabledTools 方式设置时使用，兼容 CLI/测试） */
    private Set<String> disabledToolsSnapshot = Collections.emptySet();
    /** true=使用静态快照，false=使用 ConfigService 实时读取 */
    private boolean useSnapshot = false;

    /**
     * 强制禁止的工具名集合 — 独立于用户配置的 disabledTools。
     * 由子代理等场景设置，用于在注册表层面硬性排除某些工具（如禁止递归 spawn）。
     * register() 和 refresh() 都会检查此集合。
     */
    @Getter
    private Set<String> forceDenyTools = Collections.emptySet();

    // ==================== 动态刷新上下文 ====================
    @Getter
    private Path workspace;
    private String apiUrl;
    private String apiKey;
    private List<String> blockedPaths = Collections.emptyList();

    /**
     * 设置被禁用的工具名称集合（静态快照，用于 CLI 模式 / 测试）。
     */
    public ToolRegistry setDisabledTools(Set<String> disabledTools) {
        this.disabledToolsSnapshot = disabledTools != null ? new HashSet<>(disabledTools) : Collections.emptySet();
        this.useSnapshot = true;
        return this;
    }

    /**
     * 获取当前生效的禁用工具列表。
     * 优先使用 ConfigService 实时读取，否则回退到静态快照。
     */
    private Set<String> getCurrentDisabledTools() {
        if (!useSnapshot) {
            return ConfigService.getDisabledTools();
        }
        return disabledToolsSnapshot;
    }

    /**
     * 设置强制禁止的工具名称集合。
     * 与 {@link #setDisabledTools} 不同，此集合不由用户配置控制，
     * 而是由代码逻辑（如子代理）硬性指定，独立于用户配置始终生效。
     *
     * @param denyTools 强制禁止的工具名称集合，传 null 视为空集
     */
    public void setForceDenyTools(Set<String> denyTools) {
        this.forceDenyTools = denyTools != null ? new HashSet<>(denyTools) : Collections.emptySet();
    }


    /**
     * 设置动态刷新的上下文参数。
     * 调用 {@link #refresh()} 时会使用这些参数重新扫描并注册工具。
     */
    public void setRefreshContext(Path workspace, String apiUrl, String apiKey, List<String> blockedPaths) {
        this.workspace = workspace;
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.blockedPaths = blockedPaths != null ? blockedPaths : Collections.emptyList();
    }

    /**
     * 动态刷新工具列表 —— 使用 {@link ToolScanUtil} 统一重新扫描，
     * 将最新发现的工具重新注册到注册表中。
     * <p>
     * 此方法不改变系统提示词，只影响后续 API 请求中的工具挂载列表。
     * </p>
     */
    public void refresh() {
        Set<String> disabled = getCurrentDisabledTools();
        functionToolMap.clear();
        allScannedTools.clear();
        cachedOpenAiTools = null; // 失效缓存

        // 使用 ToolScanUtil 统一扫描（Solon IoC + Skill 文件系统）
        List<FunctionTool> functionToolsList = ToolScanUtil.scanTools(workspace);

        for (FunctionTool tool : functionToolsList) {
            // 保存所有扫描到的工具（包括禁用的）
            allScannedTools.put(tool.name(), tool);

            if (disabled.contains(tool.name())) {
                continue; // 跳过禁用工具
            }
            if (forceDenyTools.contains(tool.name())) {
                continue; // 跳过强制禁止工具
            }

            functionToolMap.put(tool.name(), tool);
        }
    }



    public FunctionTool get(String name) {
        return functionToolMap.getOrDefault(name, null);
    }

    public boolean has(String name) {
        return functionToolMap.containsKey(name);
    }

    /**
     * 复制当前注册表的全部配置及工具列表到新实例。
     * <p>
     * 继承父级的所有设置：configService、disabledTools、
     * refreshContext、blockedPaths 以及全部已注册的工具。
     * 调用方可在返回后自行调用 {@link #setForceDenyTools} 设置强制禁止名单。
     * </p>
     *
     * @return 新的 ToolRegistry 实例，包含全部配置和工具
     */
    public ToolRegistry copy() {
        ToolRegistry copy = new ToolRegistry();
        // 复制禁用工具快照
        copy.disabledToolsSnapshot = this.disabledToolsSnapshot.isEmpty()
                ? Collections.emptySet()
                : new HashSet<>(this.disabledToolsSnapshot);
        copy.useSnapshot = this.useSnapshot;
        // 复制刷新上下文
        copy.workspace = this.workspace;
        copy.apiUrl = this.apiUrl;
        copy.apiKey = this.apiKey;
        copy.blockedPaths = this.blockedPaths.isEmpty()
                ? Collections.emptyList()
                : new ArrayList<>(this.blockedPaths);
        // 注册所有工具
        for (FunctionTool def : this.functionToolMap.values()) {
            copy.functionToolMap.put(def.name(), def);
        }
        for (FunctionTool def : this.allScannedTools.values()) {
            copy.allScannedTools.put(def.name(), def);
        }
        return copy;
    }

    /**
     * 返回所有已启用工具的不可变视图。
     */
    public Map<String, FunctionTool> all() {
        return Collections.unmodifiableMap(functionToolMap);
    }

    /**
     * 返回所有扫描到的工具（包括已禁用的）的不可变视图。
     */
    public Map<String, FunctionTool> allScanned() {
        return Collections.unmodifiableMap(allScannedTools);
    }

    /**
     * 判断指定工具当前是否已启用。
     *
     * @param toolName 工具名称
     * @return true 表示已启用，false 表示已禁用
     */
    public boolean isEnabled(String toolName) {
        Set<String> disabled = getCurrentDisabledTools();
        return !disabled.contains(toolName) && !forceDenyTools.contains(toolName);
    }

    /**
     * 生成 OpenAI 格式的 tools 数组（带缓存，refresh 时自动失效）。
     */
    public ONode toOpenAiTools() {
        if (cachedOpenAiTools != null) {
            return cachedOpenAiTools;
        }
        List<FunctionTool> functionTools = functionToolMap.values().stream().toList();
        ONode tools = new ONode();
        for (FunctionTool func : functionTools) {
            tools.addNew().then(n2 -> {
                n2.set("type", "function");
                n2.getOrNew("function").then(toolNode -> {
                    toolNode.set("name", func.name());
                    toolNode.set("description", func.descriptionAndMeta());
                    toolNode.set("parameters", ONode.ofJson(func.inputSchema()));
                });
            });
        }
        cachedOpenAiTools = tools;
        return tools;
    }
}
