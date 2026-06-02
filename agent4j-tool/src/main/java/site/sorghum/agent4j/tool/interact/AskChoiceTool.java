package site.sorghum.agent4j.tool.interact;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 用户选择菜单工具 —— 向用户展示多选菜单。
 *
 * @author Sorghum
 */
@Component
public class AskChoiceTool extends AgentTool {

    @Inject
    private InteractionService interactionService;

    @Override
    public String getName() {
        return "ask_choice";
    }

    @Override
    public String getDescription() {
        return "Render an arrow-key picker with 2–6 alternatives.";
    }

    @Override
    public String toToolSpec() {
        return """
                ### ask_choice
                
                描述：向用户展示选择菜单（2-6 个选项），等待用户选择。allowCustom=true 允许自定义输入。
                参数: question(必填), options(必填，支持字符串数组或对象数组), allowCustom(可选)。可写。
                """;
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Arrays.asList(
                new ToolParameter("question", "string", true, "问题"),
                new ToolParameter("options", "array", true, "选项列表"),
                new ToolParameter("allowCustom", "boolean", false, "是否允许自定义输入")
        );
    }

    @Override
    public ToolResult execute(ToolContext ctx) {
        @SuppressWarnings("unchecked")
        List<?> rawOptions = (List<?>) ctx.getParams().get("options");
        List<Map<String, Object>> options = normalizeOptions(rawOptions);
        return ToolResult.ok(interactionService.askChoice(ctx.getString("question"),
                options, ctx.getBool("allowCustom", false)));
    }

    /**
     * 将 options 统一转换为 List<Map<String, Object>> 格式。
     * 兼容两种输入：
     * 1. 字符串数组 ["选项1", "选项2"] → [{"title": "选项1"}, {"title": "选项2"}]
     * 2. 对象数组 [{"title": "选项1", "summary": "说明"}] → 保持不变
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeOptions(List<?> raw) {
        if (raw == null || raw.isEmpty()) return new ArrayList<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : raw) {
            if (item instanceof Map) {
                result.add((Map<String, Object>) item);
            } else if (item instanceof String) {
                Map<String, Object> opt = new java.util.HashMap<>();
                opt.put("title", item);
                result.add(opt);
            } else {
                // 其他类型转为字符串处理
                Map<String, Object> opt = new java.util.HashMap<>();
                opt.put("title", String.valueOf(item));
                result.add(opt);
            }
        }
        return result;
    }
}
