package site.sorghum.agent4j.bin.tool;

import lombok.Getter;
import site.sorghum.agent4j.bin.config.ConfigService;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;

import java.nio.file.Path;
import java.util.*;

/**
 * 工具注册表 —— 管理工具定义的注册和查询。
 * <p>
 * 职责：工具注册、查询、生成 OpenAI function-calling 格式的工具列表。
 * 调度逻辑已移至 {@link ToolDispatcher}，schema 展平已移至 {@link ToolSchemaFlattener}。
 * </p>
 *
 * @author Sorghum
 */
public class ToolRegistry {

    private final Map<String, ToolDef> tools = new LinkedHashMap<>();
    private final ToolSchemaFlattener flattener = new ToolSchemaFlattener();

    /** ConfigService 引用（同模块，运行时实时读取禁用列表） */
    private ConfigService configService;
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
    private Path workspace;
    private String apiUrl;
    private String apiKey;
    private List<String> blockedPaths = Collections.emptyList();

    /**
     * 设置 ConfigService 引用（运行时实时读取禁用列表）。
     */
    public void setConfigService(ConfigService configService) {
        this.configService = configService;
        this.useSnapshot = false;
    }

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
        if (!useSnapshot && configService != null) {
            return configService.getDisabledTools();
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
        tools.clear();

        // 使用 ToolScanUtil 统一扫描（Solon IoC + Skill 文件系统）
        List<AgentTool> agentTools = ToolScanUtil.scanTools(workspace);

        for (AgentTool tool : agentTools) {
            if (disabled.contains(tool.getName())) {
                continue; // 跳过禁用工具
            }
            if (forceDenyTools.contains(tool.getName())) {
                continue; // 跳过强制禁止工具
            }
            String toolSpec = tool.toToolSpec();
            ToolDef def = new ToolDef(
                    tool.getName(),
                    tool.getDescription(),
                    ToolDefHelper.toParamDefs(tool.getParameters()),
                    args -> {
                        String sessionId = args != null ? (String) args.remove("__sessionId__") : null;
                        return ToolDefHelper.formatResult(tool.execute(
                                new ToolContext(args, workspace, apiUrl, apiKey,
                                        this, blockedPaths, sessionId, false)));
                    },
                    tool.isReadOnly(),
                    tool.isStormExempt(),
                    toolSpec
            );
            tools.put(def.name(), def);
        }
    }

    public void register(ToolDef def) {
        if (getCurrentDisabledTools().contains(def.name())) {
            System.err.println("[registry] 工具已禁用，跳过注册: " + def.name());
            return;
        }
        if (forceDenyTools.contains(def.name())) {
            System.err.println("[registry] 工具被强制禁止，跳过注册: " + def.name());
            return;
        }
        tools.put(def.name(), def);
    }

    public ToolDef get(String name) {
        return tools.get(name);
    }

    public boolean has(String name) {
        return tools.containsKey(name);
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
        // 复制 ConfigService 引用（同一实例，线程安全）
        copy.configService = this.configService;
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
        for (ToolDef def : this.tools.values()) {
            copy.tools.put(def.name(), def);
        }
        return copy;
    }

    /**
     * 返回所有工具的不可变视图。
     */
    public Map<String, ToolDef> all() {
        return Collections.unmodifiableMap(tools);
    }

    /**
     * 生成 OpenAI 格式的 tools 数组（按名称排序，保证 prompt prefix 稳定可缓存）。
     * Schema 展平委托给 {@link ToolSchemaFlattener}。
     */
    public List<Map<String, Object>> toOpenAiTools() {
        List<Map<String, Object>> list = new ArrayList<>();
        List<ToolDef> sorted = new ArrayList<>(tools.values());
        sorted.sort(Comparator.comparing(ToolDef::name));
        for (ToolDef t : sorted) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", "function");
            Map<String, Object> func = new LinkedHashMap<>();
            func.put("name", t.name());
            func.put("description", t.description());
            Map<String, Object> schema = flattener.maybeFlattenSchema(t.toParametersSchema());
            func.put("parameters", schema);
            entry.put("function", func);
            list.add(entry);
        }
        return list;
    }
}
