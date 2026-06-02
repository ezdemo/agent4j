package site.sorghum.agent4j.tool.plan;

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
 * 计划提交工具 —— 提交一份计划供用户审查。
 *
 * @author Sorghum
 */
@Component
public class SubmitPlanTool extends AgentTool {

    @Inject
    private PlanService planService;

    @Override
    public String getName() {
        return "submit_plan";
    }

    @Override
    public String getDescription() {
        return "Submit one concrete plan for review.";
    }

    @Override
    public String toToolSpec() {
        return """
                ### submit_plan
                
                描述：提交一份具体的执行计划供用户审查。在计划模式下（Plan Mode）使用，
                先用只读工具探索代码库，然后提交计划。
                
                ## 使用指南
                
                1. **探索阶段**：先用 read_file / glob / grep / tree 了解代码结构
                2. **制定计划**：确定修改内容，拆分为可执行的步骤
                3. **提交计划**：使用 submit_plan 提交计划，用户审批后执行
                4. **执行阶段**：用户输入 /execute 退出计划模式，开始执行
                
                参数：
                  - summary (string, 可选): 计划标题/摘要
                  - plan (string, 必填): Markdown 格式的计划内容
                  - steps (array, 必填): 步骤列表，每项包含 id/title/action
                
                只读：是
                风暴豁免：否""";
    }

    @Override
    public boolean isReadOnly() {
        return true; // 计划模式下可用——submit_plan 是计划模式的核心工具
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
