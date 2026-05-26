package site.sorghum.agent4j.bin.builtin;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.bin.service.JobService;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.util.Arrays;
import java.util.List;

/**
 * 作业输出读取工具 —— 读取后台作业的最新输出。
 * <p>
 * 支持增量轮询（since 参数指定字节偏移）和尾部行数控制。
 * 只读工具，在计划模式下可用。
 * </p>
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
