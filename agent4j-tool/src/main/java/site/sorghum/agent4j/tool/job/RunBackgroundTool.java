package site.sorghum.agent4j.tool.job;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.tool.AgentTool;
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
    public String getName() { return "run_background"; }

    @Override
    public String getDescription() {
        return "Spawn a long-running process and detach. Returns job id + startup preview. "
                + "Use for dev servers / watchers / long installs.";
    }

    @Override
    public String toToolSpec() {
        return "### run_background\n\n"
                + "描述：启动一个长时间运行的 shell 进程并分离。适用于开发服务器、文件监视器、\n"
                + "长时间安装等场景。返回 job id 和启动预览输出。\n\n"
                + "## 使用指南\n\n"
                + "1. **启动服务**：如 `npm run dev`、`mvn spring-boot:run`\n"
                + "2. **启动监视器**：如 `npx tsc --watch`、`nodemon`\n"
                + "3. **查看输出**：使用 job_output 工具读取作业输出\n"
                + "4. **等待完成**：使用 wait_for_job 工具等待作业结束\n"
                + "5. **停止作业**：使用 stop_job 工具停止作业\n\n"
                + "参数：\n"
                + "  - command (string, 必填): shell 命令\n"
                + "  - cwd (string, 可选): 工作目录（相对于项目根）\n"
                + "  - waitSec (int, 可选): 等待启动秒数（默认 0）\n\n"
                + "只读：否\n"
                + "风暴豁免：否";
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
            return ToolResult.fail("IO_ERROR", e.getMessage());
        }
    }
}
