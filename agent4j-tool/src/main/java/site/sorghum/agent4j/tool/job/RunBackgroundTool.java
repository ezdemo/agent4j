package site.sorghum.agent4j.tool.job;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ErrorCodes;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * 后台作业启动工具 —— 启动一个长时间运行的 shell 进程并分离。
 *
 * @author Sorghum
 */
@Component
public class RunBackgroundTool extends AgentTool {

    @Inject
    private JobService jobService;

    @Override
    public String getName() {
        return "run_background";
    }

    @Override
    public String getDescription() {
        return "Spawn a long-running process and detach. Returns job id + startup preview. "
                + "Use for dev servers / watchers / long installs.";
    }

    @Override
    public String toToolSpec() {
        return """
                ### run_background
                
                描述：启动后台 shell 进程并分离，适用于开发服务器/监视器/长时间安装。返回 job id 和启动输出。
                参数: command(必填), cwd(可选), waitSec(可选，默认0)。可写。
                """;
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Arrays.asList(
                new ToolParameter("command", "string", true, "shell 命令"),
                new ToolParameter("cwd", "string", false, "工作目录（相对于项目根）"),
                new ToolParameter("waitSec", "int", false, "等待启动秒数（默认 0）")
        );
    }

    @Override
    public ToolResult execute(ToolContext ctx) {
        try {
            Integer waitSec = ctx.has("waitSec") ? ctx.getInt("waitSec", 0) : null;
            return ToolResult.ok(jobService.runBackground(ctx.getRootDir(), ctx.getString("command"),
                    ctx.getString("cwd"), waitSec));
        } catch (IOException e) {
            return ToolResult.fail(ErrorCodes.IO_ERROR, e.getMessage());
        }
    }
}
