package site.sorghum.agent4j.tool.solon;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.noear.snack4.ONode;
import org.noear.solon.ai.chat.talent.Talent;
import org.noear.solon.ai.chat.tool.FunctionTool;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ErrorCodes;
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
@Slf4j
@AllArgsConstructor
public class ToolManager {

    /**
     * 将所有 Skill 中的 FunctionTool 统一适配为 AgentTool。
     */
    public static List<AgentTool> getToolsFromSKill(Collection<Talent> skills) {
        return skills.stream()
                .map(skill -> skill.getTools(null))          // Skill → Collection<FunctionTool>
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)                  // 扁平化为 FunctionTool 流
                .map(FunctionToolAdapter::new)                // 适配为 AgentTool
                .collect(Collectors.toList());
    }

    /**
     * 将多个 FunctionTool 集合统一适配为 AgentTool 列表。
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
                            ErrorCodes.SOLON_TOOL_EXEC_ERROR,
                            solonResult.getContent()
                    );
                }
                // 提取文本内容（Contents.toString() 拼接所有文本块）
                String text = solonResult.getContent();
                if (text == null || text.isBlank()) {
                    text = "(工具返回空结果)";
                }

                return ToolResult.ok(text);                       // ← 你的 ToolResult
            } catch (Exception e) {
                log.warn("工具执行失败 [{}]: {}", getName(), e.getMessage(), e);
                return ToolResult.fail(ErrorCodes.TOOL_EXEC_ERROR,
                        "工具执行失败 [" + getName() + "]: " + e.getMessage());
            } catch (Throwable t) {
                log.error("工具执行致命错误 [{}]: {}", getName(), t.getMessage(), t);
                return ToolResult.fail(ErrorCodes.TOOL_EXEC_ERROR,
                        "工具执行致命错误 [" + getName() + "]: " + t.getMessage());
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

        try {
            // 转为 Map 操作，避免 ONode 特定 API 的兼容问题
            Map<String, Object> schema = ONode.ofJson(inputSchemaJson).toBean(Map.class);

            // 提取 required 列表
            List<String> requiredList = (List<String>) schema.getOrDefault("required", Collections.emptyList());
            Set<String> requiredSet = new HashSet<>(requiredList);

            // 遍历 properties 并递归解析
            Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
            if (properties != null) {
                return parseProperties(properties, requiredSet);
            }
        } catch (Exception e) {
            log.warn("解析 Tool 参数 Schema 失败: {} - {}", toolName, e.getMessage(), e);
        }

        return Collections.emptyList();
    }

    /**
     * 递归解析 properties 对象，支持 object/array 嵌套结构。
     *
     * @param properties  JSON Schema 的 properties 映射
     * @param requiredSet 当前层级的必填字段集合
     * @return 解析后的 ToolParameter 列表
     */
    @SuppressWarnings("unchecked")
    private static List<ToolParameter> parseProperties(Map<String, Object> properties, Set<String> requiredSet) {
        List<ToolParameter> result = new ArrayList<>();

        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String paramName = entry.getKey();
            Map<String, Object> paramSchema = (Map<String, Object>) entry.getValue();

            String type = (String) paramSchema.getOrDefault("type", "string");
            String description = (String) paramSchema.get("description");
            boolean required = requiredSet.contains(paramName);

            // 处理 object 类型：递归解析嵌套 properties
            if ("object".equals(type) && paramSchema.containsKey("properties")) {
                List<String> nestedRequired = (List<String>) paramSchema
                        .getOrDefault("required", Collections.emptyList());
                Map<String, Object> nestedProps = (Map<String, Object>) paramSchema.get("properties");
                List<ToolParameter> nested = parseProperties(nestedProps, new HashSet<>(nestedRequired));
                result.add(ToolParameter.objectParam(paramName, required, description, nested));
                continue;
            }

            // 处理 array 类型：递归解析 items
            if ("array".equals(type) && paramSchema.containsKey("items")) {
                Map<String, Object> itemSchema = (Map<String, Object>) paramSchema.get("items");
                ToolParameter item = parseSingleParam("", itemSchema);
                result.add(ToolParameter.arrayParam(paramName, required, description, item));
                continue;
            }

            // 扁平参数
            result.add(new ToolParameter(paramName, type, required, description));
        }

        return result;
    }

    /**
     * 解析单个参数 schema（用于 array 的 items 定义）。
     */
    @SuppressWarnings("unchecked")
    private static ToolParameter parseSingleParam(String name, Map<String, Object> schema) {
        String type = (String) schema.getOrDefault("type", "string");
        String description = (String) schema.get("description");

        if ("object".equals(type) && schema.containsKey("properties")) {
            List<String> nestedRequired = (List<String>) schema.getOrDefault("required", Collections.emptyList());
            Map<String, Object> nestedProps = (Map<String, Object>) schema.get("properties");
            List<ToolParameter> nested = parseProperties(nestedProps, new HashSet<>(nestedRequired));
            return ToolParameter.objectParam(name, false, description, nested);
        }

        if ("array".equals(type) && schema.containsKey("items")) {
            Map<String, Object> itemSchema = (Map<String, Object>) schema.get("items");
            ToolParameter item = parseSingleParam("", itemSchema);
            return ToolParameter.arrayParam(name, false, description, item);
        }

        return new ToolParameter(name, type, false, description);
    }
}
