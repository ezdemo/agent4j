package site.sorghum.agent4j.tool.interact;

import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.tool.AgentLoopController;
import site.sorghum.agent4j.tool.AgentOutput;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ErrorCodes;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 用户选择菜单工具 —— 通过 {@link AgentOutput#ask} 向用户展示多选菜单。
 *
 * @author Sorghum
 */
@Component
public class AskChoiceTool extends AgentTool {

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
        return List.of(
                new ToolParameter("question", "string", true, "问题"),
                new ToolParameter("options", "array", true, "选项列表"),
                new ToolParameter("allowCustom", "boolean", false, "是否允许自定义输入")
        );
    }

    @Override
    public ToolResult execute(ToolContext ctx) {
        // 通过 AgentLoopController 获取输出通道
        AgentLoopController ctrl = ctx.getLoopController();
        if (ctrl == null) {
            return ToolResult.fail(ErrorCodes.NO_CONTROLLER, "没有可用的 AgentLoop 控制器，无法展示选择菜单");
        }
        AgentOutput output = ctrl.getOutput();
        if (output == null) {
            return ToolResult.fail(ErrorCodes.NO_OUTPUT, "没有可用的输出通道，无法展示选择菜单");
        }

        List<?> rawOptions = (List<?>) ctx.getParams().get("options");
        List<Map<String, Object>> options = normalizeOptions(rawOptions);
        String result = output.ask(
                ctx.getString("question"),
                options,
                ctx.getBool("allowCustom", false)
        );
        ctrl.requestStop();
        return ToolResult.ok(result);
    }

    /**
     * 将 options 统一转换为 List&lt;Map&lt;String, Object&gt;&gt; 格式。
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
                Map<String, Object> opt = new java.util.HashMap<>();
                opt.put("title", String.valueOf(item));
                result.add(opt);
            }
        }
        return result;
    }
}
