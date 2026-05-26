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
    public String toToolSpec() {
        return "### task\n\n"
                + "描述：创建一个隔离子代理来处理复杂的多步任务。子代理继承父代理的大部分工具，\n"
                + "但排除递归 spawn 和用户交互工具。适用于需要深入调查多个文件的复杂场景。\n\n"
                + "## 使用指南\n\n"
                + "1. **任务分解**：当主任务涉及多个独立子任务时，可以用 task 委派子代理\n"
                + "2. **隔离执行**：子代理有独立的对话上下文，不会影响主代理的历史\n"
                + "3. **结果返回**：子代理完成后返回最终结果给主代理\n"
                + "4. **适用场景**：\n"
                + "   - 深入调查一个复杂问题（搜索多个文件、分析代码）\n"
                + "   - 执行独立的功能实现（如添加一个新类）\n"
                + "   - 并行探索多个可能性\n\n"
                + "参数：\n"
                + "  - name (string, 必填): 技能名称\n"
                + "  - arguments (string, 可选): 技能参数描述\n\n"
                + "只读：否\n"
                + "风暴豁免：否";
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
