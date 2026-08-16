package site.sorghum.loopra.integration.cutin;

import site.sorghum.cutin.core.context.Message;
import site.sorghum.cutin.core.loop.*;
import site.sorghum.cutin.core.model.ModelCallRequest;
import site.sorghum.cutin.core.model.ModelResponse;
import site.sorghum.cutin.core.tool.ToolCall;
import site.sorghum.cutin.core.tool.ToolResult;

import java.util.List;
import java.util.Map;

/**
  * 重构后的 Loopra 循环使用的标准 cutin 产品步骤。
 * <p>
  * {@code model -> tool -> model -> output} 以普通 cutin {@link LoopProgram} 表达；
  * Loopra 公共 API 保持不变，但真正的推理循环由 cutin 状态机驱动。
 * </p>
 */
public final class CutinSteps {

    private CutinSteps() {
    }

    public static LoopProgram codingProgram(String modelId) {
        return LoopProgram.builder("loopra-coding-agent")
            .node("model", NodeType.MODEL, agentModel(modelId))
            .node("tool", NodeType.TOOL, dispatchToolCalls())
            .node("output", NodeType.OUTPUT, Steps.finish())
            .next("model", "tool")
            .next("tool", "model")
            .start("model")
            .build();
    }

    public static Step agentModel(String modelId) {
        return context -> {
            ModelCallRequest request = new ModelCallRequest(
                modelId,
                context.messages(),
                context.tools().definitions(),
                Map.of()
            );
            ModelResponse response = context.models().call(request, context);
            context.addUsage(response.usage());
            context.appendMessage(response.message());
            return response.message().hasToolCalls()
                ? new StepResult.Goto("tool")
                : StepResult.Exit.INSTANCE;
        };
    }

    public static Step dispatchToolCalls() {
        return context -> {
            List<Message> messages = context.messages();
            if (messages.isEmpty()) {
                return new StepResult.Goto("output");
            }
            Message last = messages.get(messages.size() - 1);
            if (!last.hasToolCalls()) {
                return new StepResult.Goto("output");
            }
            for (ToolCall call : last.toolCalls()) {
                ToolResult result = context.tools().call(call, context);
                String content = result.ok()
                    ? String.valueOf(result.content())
                    : "ERROR " + result.error();
                context.appendMessage(new Message("tool", content, call.id(), List.of()));
            }
            return new StepResult.Goto("model");
        };
    }
}
