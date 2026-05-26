package site.sorghum.agent4j.tool.job;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.util.Arrays;
import java.util.List;

/**
 * 作业输出读取工具 —— 读取后台作业的最新输出。
 *
 * @author Sorghum
 */
@Component
public class JobOutputTool extends AgentTool {

    @Inject
    private JobService jobService;

    @Override
    public String getName() { return "job_output"; }

    @Override
    public String getDescription() {
        return "Read the latest output of a background job. Pass `since` for incremental polling.";
    }

    @Override
    public String toToolSpec() {
        return "### job_output\n\n"
                + "描述：读取后台作业的最新输出。配合 run_background 使用，轮询查看作业进展。\n\n"
                + "## 使用指南\n\n"
                + "1. **首次读取**：传入 jobId 获取最近 80 行输出\n"
                + "2. **增量读取**：用 since 参数指定字节偏移量，只返回新内容\n"
                + "3. **行数控制**：用 tailLines 控制返回行数\n\n"
                + "## 相关工具\n\n"
                + "- run_background — 启动后台作业\n"
                + "- wait_for_job — 等待作业完成\n"
                + "- stop_job — 停止作业\n"
                + "- list_jobs — 列出所有作业\n\n"
                + "参数：\n"
                + "  - jobId (int, 必填): 后台作业 ID\n"
                + "  - since (int, 可选): 字节偏移量，用于增量读取\n"
                + "  - tailLines (int, 可选): 返回最后 N 行（默认 80）\n\n"
                + "只读：是\n"
                + "风暴豁免：是";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Arrays.asList(
                new ToolParameter("jobId", "int", true, "Job id from run_background"),
                new ToolParameter("since", "int", false, "Byte offset for incremental read"),
                new ToolParameter("tailLines", "int", false, "Last N lines (default 80)")
        );
    }

    @Override
    public ToolResult execute(ToolContext ctx) {
        Integer since = ctx.has("since") ? ctx.getInt("since", 0) : null;
        Integer tailLines = ctx.has("tailLines") ? ctx.getInt("tailLines", 0) : null;
        return ToolResult.ok(jobService.jobOutput(ctx.getInt("jobId", 0), since, tailLines));
    }
}
