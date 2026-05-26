package site.sorghum.agent4j.bin.builtin;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.bin.service.PlanService;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 计划提交工具 —— 提交一份计划供用户审查。
 * <p>
 * 在计划模式下使用，包含摘要、Markdown 计划和步骤列表。
 * 用户审批后或输入 /execute 后开始执行。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class SubmitPlanTool extends AgentTool {

    @Inject
    private PlanService planService;

    @Override
    public String getName() { return "submit_plan"; }

    @Override
    public String getDescription() {
        return "Submit one concrete plan for review.";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Arrays.asList(
                new ToolParameter("summary", "string", false, "计划标题"),
                new ToolParameter("plan", "string", true, "Markdown 计划内容"),
                new ToolParameter("steps", "array", true, "步骤列表")
        );
    }

    @Override
    public ToolResult execute(ToolContext ctx) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) ctx.getParams().get("steps");
        return ToolResult.ok(planService.submitPlan(ctx.getString("summary"),
                ctx.getString("plan"), steps));
    }
}
