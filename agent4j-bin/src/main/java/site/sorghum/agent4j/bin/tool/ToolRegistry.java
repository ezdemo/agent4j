package site.sorghum.agent4j.bin.tool;

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
    private Set<String> disabledTools = Collections.emptySet();

    // ==================== 动态刷新上下文 ====================
    private Path workspace;
    private String apiUrl;
    private String apiKey;
    private List<String> blockedPaths = Collections.emptyList();

    /**
     * 设置被禁用的工具名称集合。
     * 被禁用的工具在 register 时会被跳过，不会出现在 LLM 的工具列表中。
     */
    public ToolRegistry setDisabledTools(Set<String> disabledTools) {
        this.disabledTools = disabledTools != null ? disabledTools : Collections.emptySet();
        return this;
    }

    /**
     * 判断指定工具是否已禁用。
     */
    public boolean isToolEnabled(String name) {
        return !disabledTools.contains(name);
    }

    /**
     * 设置动态刷新的上下文参数。
     * 调用 {@link #refresh()} 时会使用这些参数重新扫描并注册工具。
     */
    public ToolRegistry setRefreshContext(Path workspace, String apiUrl, String apiKey, List<String> blockedPaths) {
        this.workspace = workspace;
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.blockedPaths = blockedPaths != null ? blockedPaths : Collections.emptyList();
        return this;
    }

    /**
     * 动态刷新工具列表 —— 使用 {@link ToolScanUtil} 统一重新扫描，
     * 将最新发现的工具重新注册到注册表中。
     * <p>
     * 此方法不改变系统提示词，只影响后续 API 请求中的工具挂载列表。
     * </p>
     */
    public void refresh() {
        tools.clear();

        // 使用 ToolScanUtil 统一扫描（Solon IoC + Skill 文件系统）
        List<AgentTool> agentTools = ToolScanUtil.scanTools(workspace);

        for (AgentTool tool : agentTools) {
            if (disabledTools.contains(tool.getName())) {
                continue; // 跳过禁用工具
            }
            String toolSpec = tool.toToolSpec();
            ToolDef def = new ToolDef(
                    tool.getName(),
                    tool.getDescription(),
                    ToolDefHelper.toParamDefs(tool.getParameters()),
                    args -> {
                        String sessionId = args != null ? (String) args.remove("__sessionId__") : null;
                        return ToolDefHelper.formatResult(tool.execute(
                                new ToolContext(args, workspace, apiUrl, apiKey, this, blockedPaths, sessionId)));
                    },
                    tool.isReadOnly(),
                    tool.isStormExempt(),
                    toolSpec
            );
            tools.put(def.name(), def);
        }
    }

    public ToolRegistry register(ToolDef def) {
        if (disabledTools.contains(def.name())) {
            System.err.println("[registry] 工具已禁用，跳过注册: " + def.name());
            return this;
        }
        tools.put(def.name(), def);
        return this;
    }

    public ToolDef get(String name) {
        return tools.get(name);
    }

    public boolean has(String name) {
        return tools.containsKey(name);
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
