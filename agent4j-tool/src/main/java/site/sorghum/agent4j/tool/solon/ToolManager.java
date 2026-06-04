package site.sorghum.agent4j.tool.solon;

import lombok.AllArgsConstructor;
import org.noear.snack4.ONode;
import org.noear.solon.ai.chat.skill.Skill;
import org.noear.solon.ai.chat.tool.FunctionTool;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 工具管理基类——持有 Skill 列表，并提供统一的 AgentTool 视图。
 *
 * @author Sorghum
 */
@AllArgsConstructor
public class ToolManager {

    /**
     * 【你要完善的方法】
     * 将所有 Skill 中的 FunctionTool 统一适配为 AgentTool。
     */
    public static List<AgentTool> getToolsFromSKill(Collection<Skill> skills) {
        return skills.stream()
                .map(skill -> skill.getTools(null))          // Skill → Collection<FunctionTool>
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)                  // 扁平化为 FunctionTool 流
                .map(FunctionToolAdapter::new)                // 适配为 AgentTool
                .collect(Collectors.toList());
    }

    /**
     * 【你要完善的方法】
     * 将所有 Skill 中的 FunctionTool 统一适配为 AgentTool。
     */
    @SafeVarargs
    public static List<AgentTool> getToolsFromTools(Collection<FunctionTool> ...tools) {
        return Arrays.stream(tools).flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(FunctionToolAdapter::new)                // 适配为 AgentTool
                .collect(Collectors.toList());
    }


    // =========================================================
    //  内部类：FunctionTool → AgentTool 适配器
    // =========================================================

    /**
     * 将 Solon AI 的 {@link FunctionTool} 适配为 {@link AgentTool}。
     * <p>
     * 桥接两个体系的接口：
     * <pre>
     *   FunctionTool.name()               → AgentTool.getName()
     *   FunctionTool.descriptionAndMeta() → AgentTool.getDescription()
     *   FunctionTool.inputSchema()        → AgentTool.getParameters() （解析 JSON Schema）
     *   FunctionTool.call(Map)            → AgentTool.execute(ToolContext)
     * </pre>
     * </p>
     */
    private static class FunctionToolAdapter extends AgentTool {
        private final FunctionTool functionTool;
        private List<ToolParameter> parameters;        // 缓存解析结果

        FunctionToolAdapter(FunctionTool functionTool) {
            this.functionTool = functionTool;
        }

        @Override
        public String getName() {
            return functionTool.name();
        }

        @Override
        public String getDescription() {
            return functionTool.descriptionAndMeta();
        }

        @Override
        public List<ToolParameter> getParameters() {
            if (parameters == null) {
                parameters = parseInputSchema(functionTool.inputSchema(), functionTool.name());
            }
            return parameters;
        }

        @Override
        public ToolResult execute(ToolContext ctx) {
            try {
                // 调用 Solon 的 FunctionTool，得到 Solon 的 ToolResult
                org.noear.solon.ai.chat.tool.ToolResult solonResult
                        = functionTool.call(ctx.getParams());     // ← 用了 getParams()
                if (solonResult.isError()){
                    return ToolResult.fail(
                            "SOLON_TOOL_EXEC_ERROR",
                            solonResult.getContent()
                    );
                }
                // 提取文本内容（Contents.toString() 拼接所有文本块）
                String text = solonResult.getContent();
                if (text == null || text.isBlank()) {
                    text = "(工具返回空结果)";
                }

                return ToolResult.ok(text);                       // ← 你的 ToolResult
            } catch (Throwable e) {
                return ToolResult.fail("TOOL_EXEC_ERROR",         // ← 你的 ToolResult
                        "工具执行失败 [" + getName() + "]: " + e.getMessage());
            }
        }

        @Override
        public boolean isReadOnly() {
            // 从 meta 中判断：如果标记了 readonly=true 则为只读
            Object readonly = functionTool.meta() == null ? null
                    : functionTool.meta().get("readonly");
            return readonly instanceof Boolean && (Boolean) readonly;
        }

        @Override
        public String toToolSpec() {
            // 利用 descriptionAndMeta() 作为工具规约
            return "### " + functionTool.name() + "\n" + functionTool.descriptionAndMeta();
        }
    }


    // =========================================================
    //  JSON Schema 解析：inputSchema → List<ToolParameter>
    // =========================================================

    /**
     * 解析 FunctionTool.inputSchema() 返回的 JSON Schema，
     * 提取参数定义列表。
     * <p>
     * 输入 JSON 格式示例：
     * <pre>{@code
     * {
     *   "type": "object",
     *   "properties": {
     *     "city":    { "type": "string",  "description": "城市名称" },
     *     "days":    { "type": "integer", "description": "天数" }
     *   },
     *   "required": ["city"]
     * }
     * }</pre>
     * </p>
     */
    @SuppressWarnings("unchecked")
    private static List<ToolParameter> parseInputSchema(String inputSchemaJson, String toolName) {
        if (inputSchemaJson == null || inputSchemaJson.isBlank()) {
            return Collections.emptyList();
        }

        List<ToolParameter> result = new ArrayList<>();

        try {
            // 转为 Map 操作，避免 ONode 特定 API 的兼容问题
            Map<String, Object> schema = ONode.ofJson(inputSchemaJson).toBean(Map.class);

            // 1. 提取 required 列表
            List<String> requiredList = (List<String>) schema.getOrDefault("required", Collections.emptyList());
            Set<String> requiredSet = new HashSet<>(requiredList);

            // 2. 遍历 properties
            Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
            if (properties != null) {
                for (Map.Entry<String, Object> entry : properties.entrySet()) {
                    String paramName = entry.getKey();
                    Map<String, Object> paramSchema = (Map<String, Object>) entry.getValue();

                    String type = (String) paramSchema.getOrDefault("type", "string");
                    String description = (String) paramSchema.get("description");
                    boolean required = requiredSet.contains(paramName);

                    result.add(new ToolParameter(paramName, type, required, description));
                }
            }
        } catch (Exception e) {
            System.err.println("[WARN] 解析 Tool 参数 Schema 失败: " + toolName + " - " + e.getMessage());
        }

        return result;
    }
}
