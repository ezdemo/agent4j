package site.sorghum.agent4j.tool.interact;

import site.sorghum.agent4j.tool.AgentLoopController;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.util.List;

/**
 * 对话结束工具 —— AI 认为可以结束当前对话并给出最终回答时调用，退出推理循环。
 * <p>
 * 调用此工具后，推理循环将在本轮工具执行完成后退出，
 * content 将作为最终回答返回给用户。
 * 无论是否存在显式的任务列表，只要 AI 认为对话可以结束，都应调用此工具。
 * </p>
 *
 * @author Sorghum
 */
public class FinishTool extends AgentTool {

    @Override
    public String getName() {
        return "finish";
    }

    @Override
    public String getDescription() {
        return """
                对话结束信号 —— 当你认为对话可以结束，准备给出最终回答时调用此工具。
                调用后推理循环将退出，content 将作为你的最终回答返回给用户。
                注意：纯文本回复不会退出循环，必须通过此工具显式宣告对话结束。
                即使没有显式的任务，只要你觉得回答已经完整，也应当调用此工具来结束对话。
                """;
    }

    @Override
    public String toToolSpec() {
        return """
                ### finish
                
                描述：AI 认为对话可以结束并准备给出最终回答时调用此工具退出推理循环。
                注意：推理循环不会因纯文本回复而退出，必须通过此工具显式宣告完成。
                即使没有任务列表，只要回答已完整，也应调用此工具结束对话。
                参数: content(必填, AI的最终回答内容)。
                调用后本轮对话即结束，content 即为最终回复。
                """;
    }

    @Override
    public List<ToolParameter> getParameters() {
        return List.of(
                new ToolParameter("content", "string", true, "AI 的最终回答内容，将作为本轮对话的最终输出返回给用户")
        );
    }

    @Override
    public ToolResult execute(ToolContext ctx) {
        String content = ctx.getString("content");
        if (content == null || content.isBlank()) {
            return ToolResult.fail("PARAM_MISSING", "缺少必填参数 'content'，请提供最终回答内容");
        }

        AgentLoopController ctrl = ctx.getLoopController();
        if (ctrl != null) {
            ctrl.finish(content);
        }

        return ToolResult.ok(content);
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public boolean isStormExempt() {
        return true;
    }
}