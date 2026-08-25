package site.sorghum.loopra.integration.cutin.plugin.preflight;

import site.sorghum.cutin.core.context.LoopContext;
import site.sorghum.cutin.core.loop.StepResult;
import site.sorghum.cutin.core.plugin.AgentPlugin;
import site.sorghum.cutin.core.plugin.LoopPlugin;
import site.sorghum.cutin.core.plugin.LoopRegistrar;
import site.sorghum.loopra.bin.agent.model.UserMessage;

/** 根据当前模型能力清洗主图前置节点中的多模态用户消息。 */
@AgentPlugin(id = "loopra-preflight-message-sanitizer", remark = "按模型能力清洗多模态消息，避免请求格式不兼容。")
public final class LoopraMessageSanitizerPlugin implements LoopPlugin {

    private final LoopraPreflightHost host;

    public LoopraMessageSanitizerPlugin(LoopraPreflightHost host) {
        this.host = host;
    }

    @Override
    public String id() {
        return "loopra-preflight-message-sanitizer";
    }

    public StepResult execute(LoopContext context) {
        UserMessage message = LoopraPreflight.input(context);
        if (message == null) {
            context.putVariable("loopraUserMessage", "");
            return StepResult.Continue.INSTANCE;
        }
        UserMessage sanitized = host.sanitizePreflightMessage(message);
        context.putArtifact(LoopraPreflight.INPUT_ARTIFACT, sanitized);
        String text = sanitized != null && sanitized.hasContent() ? sanitized.getText() : "";
        context.putVariable("loopraUserMessage", text);
        if (message.hasContent() && (sanitized == null || !sanitized.hasContent())) {
            // 原始消息有内容，但清洗后被完全移除（例如纯图片消息被不支持图片的模型剥掉），
            // 没有可发送的内容：直接结束本轮并提示，避免以空消息调用模型网关
            // 触发上游 400（如 input must be non-empty）并击溃整个循环。
            context.putVariable("loopraExitReason", "empty_input");
            context.putVariable(LoopraPreflight.RESULT_VARIABLE,
                "没有可供模型处理的消息内容（图片可能因当前模型不支持图片输入而被移除）。请用文字描述你的需求后再试。");
            return new StepResult.Goto(LoopraPreflight.OUTPUT_NODE);
        }
        return StepResult.Continue.INSTANCE;
    }

    @Override
    public void register(LoopRegistrar registrar) {
    }
}
