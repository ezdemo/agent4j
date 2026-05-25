package site.sorghum.agent4j.bin.builtin;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.bin.agent.AgentLoopListener;
import site.sorghum.agent4j.bin.model.ModelClient;
import site.sorghum.agent4j.bin.agent.SubAgent;
import site.sorghum.agent4j.bin.tool.ToolRegistry;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Task 工具 —— 创建隔离子代理处理复杂多步任务。
 * <p>
 * 使用 {@link SubAgent} 继承父工具集，排除递归 spawn 和用户交互工具。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class TaskTool extends AgentTool {

    @Inject
    private ModelClient modelClient;

    @Override
    public String getName() { return "task"; }

    @Override
    public String getDescription() {
        return "Spawn an isolated sub-agent to handle a complex, multi-step task autonomously. "
                + "The sub-agent inherits most tools and returns a single result. "
                + "Use when a task requires deep investigation across many files.";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Arrays.asList(
                new ToolParameter("name", "string", true, "技能名称"),
                new ToolParameter("arguments", "string", false, "技能参数描述")
        );
    }

    @Override
    public ToolResult execute(ToolContext ctx) {
        String name = ctx.getString("name");
        String arguments = ctx.getString("arguments");
        if (arguments == null) arguments = name;
        try {
            ToolRegistry registry = ctx.getToolRegistry();
            SubAgent sub = new SubAgent(modelClient, registry,
                    "你是一个子代理，专注于完成以下任务：" + arguments);
            String result = sub.run(arguments, new AgentLoopListener() {
                @Override public void onReasoning(String r) { System.err.println("[sub] " + r); }
                @Override public void onToolCall(String n, String a) { System.err.println("[sub] 🔧 " + n); }
                @Override public void onToolResult(String n, String r) {
                    String d = r != null && r.length() > 100 ? r.substring(0, 100) + "…" : r;
                    System.err.println("[sub] 📦 " + n + " → " + d);
                }
            });
            return ToolResult.ok(result);
        } catch (IOException e) {
            return ToolResult.fail("IO_ERROR", e.getMessage());
        }
    }
}
