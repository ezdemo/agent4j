package site.sorghum.loopra.integration.cutin.plugin.preflight;

import site.sorghum.cutin.core.context.LoopContext;
import site.sorghum.cutin.core.loop.StepResult;
import site.sorghum.cutin.core.plugin.AgentPlugin;
import site.sorghum.cutin.core.plugin.LoopPlugin;
import site.sorghum.cutin.core.plugin.LoopRegistrar;
import site.sorghum.loopra.bin.agent.model.UserMessage;
import site.sorghum.loopra.integration.cutin.CutinMessageBridge;

/** 把清洗后的用户消息追加到 Loopra 与 Cutin 主图上下文。 */
@AgentPlugin(id = "loopra-preflight-user-message", remark = "把用户消息同步到 Loopra 与 Cutin 的上下文。")
public final class LoopraUserMessagePlugin implements LoopPlugin {

    private final LoopraPreflightHost host;

    public LoopraUserMessagePlugin(LoopraPreflightHost host) {
        this.host = host;
    }

    @Override
    public String id() {
        return "loopra-preflight-user-message";
    }

    public StepResult execute(LoopContext context) {
        UserMessage message = LoopraPreflight.input(context);
        if (message != null && message.hasContent()) {
            host.appendPreflightUserMessage(message);
            context.appendMessage(CutinMessageBridge.toCutin(message));
            host.clearSuspendedCutinState();
        }
        return StepResult.Continue.INSTANCE;
    }

    @Override
    public void register(LoopRegistrar registrar) {
    }
}
