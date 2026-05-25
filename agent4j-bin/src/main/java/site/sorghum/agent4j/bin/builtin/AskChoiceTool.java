package site.sorghum.agent4j.bin.builtin;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.bin.service.InteractionService;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
