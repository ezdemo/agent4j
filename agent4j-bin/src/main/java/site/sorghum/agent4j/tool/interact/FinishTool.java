package site.sorghum.agent4j.tool.interact;

import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.ai.chat.tool.AbsToolProvider;
import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Param;
import site.sorghum.agent4j.tool.AgentLoopController;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.solon.SolonToTools;

import java.util.Collection;

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
@Component
public class FinishTool extends AbsToolProvider implements SolonToTools {
    public static final String TIPS = "[系统提示] 如果有足够信息，请调用 `finish` 提交结果；否则请继续调用工具。";

    @ToolMapping(description = """
                对话结束信号 —— 当你认为对话可以结束，准备给出最终回答时调用此工具。
                调用后推理循环将退出，content 将作为你的最终回答返回给用户。
                注意：纯文本回复不会退出循环，必须通过此工具显式宣告对话结束。
                即使没有显式的任务，只要你觉得回答已经完整，也应当调用此工具来结束对话。
                """)
    public String finish(@Param(name = "content", description = "AI 的最终回答内容",required = false) String content,
                         ToolContext ctx) {
        AgentLoopController ctrl = ToolContext.getCurrentController();
        if (ctrl != null) {
            ctrl.finish(content);
        }

        // 如果 finish 方法处理了空 content（从上下文回填），则使用处理后的值
        // 否则使用原始 content（可能为 null，由上游兜底）
        return content != null ? content : "__FINISH_NO_CONTENT__";
    }

    @Override
    public Collection<FunctionTool> getSolonTools() {
        return this.getTools();
    }

    @Override
    public String getSystemPrompt() {
        return """
                ## 结束标志
                
                AI 认为对话可以结束并准备给出最终回答时调用finish工具退出推理循环
                """;
    }
}