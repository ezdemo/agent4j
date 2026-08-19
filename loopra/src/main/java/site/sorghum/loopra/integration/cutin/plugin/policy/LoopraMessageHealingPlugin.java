package site.sorghum.loopra.integration.cutin.plugin.policy;

import site.sorghum.cutin.core.context.Message;
import site.sorghum.cutin.core.loop.InterceptContext;
import site.sorghum.cutin.core.loop.InterceptDecision;
import site.sorghum.cutin.core.loop.InterceptPoint;
import site.sorghum.cutin.core.model.ModelCallRequest;
import site.sorghum.cutin.core.plugin.AgentPlugin;
import site.sorghum.cutin.core.plugin.LoopPlugin;
import site.sorghum.cutin.core.plugin.LoopRegistrar;

import java.util.ArrayList;
import java.util.List;

/** 在模型调用前修复不满足兼容 API 约束的消息。 */
@AgentPlugin(id = "loopra-message-healing")
public final class LoopraMessageHealingPlugin implements LoopPlugin {

    @Override
    public String id() {
        return "loopra-message-healing";
    }

    @Override
    public void register(LoopRegistrar registrar) {
        registrar.registerInterceptor(InterceptPoint.BEFORE_MODEL, 100, this::healRequest);
    }

    private InterceptDecision healRequest(InterceptContext context) {
        if (!(context.payload() instanceof ModelCallRequest request)) {
            return InterceptDecision.pass();
        }

        List<Message> healed = null;
        for (int index = 0; index < request.messages().size(); index++) {
            Message message = request.messages().get(index);
            if (!"assistant".equals(message.role())
                || message.content() != null
                || message.hasToolCalls()) {
                continue;
            }
            if (healed == null) {
                healed = new ArrayList<>(request.messages());
            }
            healed.set(index, new Message(
                message.role(),
                "",
                message.toolCallId(),
                message.toolCalls(),
                message.metadata()
            ));
        }

        if (healed == null) {
            return InterceptDecision.pass();
        }
        return InterceptDecision.replace(new ModelCallRequest(
            request.modelId(),
            healed,
            request.tools(),
            request.options()
        ));
    }
}
