package site.sorghum.agent4j.tool.interact;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

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
    public String getName() { return "ask_choice"; }

    @Override
    public String getDescription() {
        return "Render an arrow-key picker with 2–6 alternatives.";
    }

    @Override
    public String toToolSpec() {
        return "### ask_choice\n\n"
                + "描述：向用户展示一个箭头键选择菜单（2-6 个选项），等待用户选择。\n\n"
                + "## 使用指南\n\n"
                + "1. **展示选项**：question 显示问题，options 列出可选项\n"
                + "2. **自定义输入**：allowCustom=true 允许用户输入自定义值\n"
                + "3. **适用场景**：当需要用户做出选择时使用，如确认操作方式\n\n"
                + "参数：\n"
                + "  - question (string, 必填): 问题\n"
                + "  - options (array, 必填): 选项列表\n"
                + "  - allowCustom (boolean, 可选): 是否允许自定义输入\n\n"
                + "只读：否\n"
                + "风暴豁免：否";
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
        List<Map<String, Object>> options = (List<Map<String, Object>>) ctx.getParams().get("options");
        return ToolResult.ok(interactionService.askChoice(ctx.getString("question"),
                options, ctx.getBool("allowCustom", false)));
    }
}
