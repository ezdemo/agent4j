package site.sorghum.cutin.core.loop;

import site.sorghum.cutin.core.context.LoopContext;
import site.sorghum.cutin.core.context.Message;
import site.sorghum.cutin.core.context.Usage;
import site.sorghum.cutin.core.model.ModelCallRequest;
import site.sorghum.cutin.core.model.StreamChunk;
import site.sorghum.cutin.core.tool.ToolCall;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * 常用 Step 工厂，提供模型调用、流式调用、工具调用、消息输出与退出等基础节点。
 */
public final class Steps {

    /** 工具类不可实例化。 */
    private Steps() {
    }

    /** 从指定变量取提示词，追加为 user 消息后同步调用模型。 */
    public static Step model(String modelId, String promptVariable) {
        return context -> {
            Object prompt = context.variables().get(promptVariable);
            if (prompt == null) {
                return new StepResult.Fail("missing prompt variable: " + promptVariable);
            }
            List<Message> messages = new ArrayList<>(context.messages());
            messages.add(new Message("user", String.valueOf(prompt)));
            ModelCallRequest request = new ModelCallRequest(
                modelId,
                messages,
                List.of(),
                Map.of()
            );
            var response = context.models().call(request, context);
            context.addUsage(response.usage());
            context.appendMessage(response.message());
            return StepResult.Continue.INSTANCE;
        };
    }

    /** 使用当前上下文全部消息与工具定义同步调用模型。 */
    public static Step modelFromContext(String modelId) {
        return context -> {
            ModelCallRequest request = new ModelCallRequest(
                modelId,
                context.messages(),
                context.tools().definitions(),
                Map.of()
            );
            var response = context.models().call(request, context);
            context.addUsage(response.usage());
            context.appendMessage(response.message());
            return StepResult.Continue.INSTANCE;
        };
    }

    /** 使用当前上下文全部消息与工具定义流式调用模型，并拼接为 assistant 消息。 */
    public static Step streamModelFromContext(String modelId) {
        return context -> {
            ModelCallRequest request = new ModelCallRequest(
                modelId,
                context.messages(),
                context.tools().definitions(),
                Map.of()
            );
            StringBuilder content = new StringBuilder();
            Usage[] totalUsage = {Usage.ZERO};
            try (Stream<StreamChunk> chunks = context.models().stream(request, context)) {
                chunks.forEach(chunk -> {
                    content.append(chunk.content());
                    totalUsage[0] = totalUsage[0].add(chunk.usage());
                });
            }
            context.addUsage(totalUsage[0]);
            Message assistant = new Message("assistant", content.toString());
            context.appendMessage(assistant);
            return StepResult.Continue.INSTANCE;
        };
    }

    /** 调用指定工具并把结果写入 {@code lastToolResult} 变量。 */
    public static Step tool(String toolId, Function<LoopContext, Map<String, Object>> arguments) {
        return context -> {
            String callId = UUID.randomUUID().toString();
            var result = context.tools().call(
                new ToolCall(callId, toolId, arguments.apply(context), callId),
                context
            );
            context.putVariable("lastToolResult", result);
            return StepResult.Continue.INSTANCE;
        };
    }

    /** 向上下文追加一条指定角色与内容的消息。 */
    public static Step emit(String role, String content) {
        return context -> {
            context.appendMessage(new Message(role, content));
            return StepResult.Continue.INSTANCE;
        };
    }

    /** 直接正常退出循环。 */
    public static Step finish() {
        return context -> StepResult.Exit.INSTANCE;
    }
}
