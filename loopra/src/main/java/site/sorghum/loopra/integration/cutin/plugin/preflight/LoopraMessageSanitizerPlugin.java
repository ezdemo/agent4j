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
        return StepResult.Continue.INSTANCE;
    }

    @Override
    public void register(LoopRegistrar registrar) {
    }
}
