package site.sorghum.loopra.bin.model.special;

import org.noear.snack4.ONode;
import org.noear.solon.annotation.Component;
import site.sorghum.cutin.integrations.model.OpenAiChatCompletionsProvider;
import site.sorghum.cutin.integrations.model.ProviderInterceptContext;
import site.sorghum.cutin.integrations.model.ProviderInterceptor;

@Component
public class MimoProviderInterceptor implements ProviderInterceptor {

    public MimoProviderInterceptor() {
        INTERCEPT_LIST.add(this);
    }

    @Override
    public ONode intercept(ProviderInterceptContext context) {
        if (!context.modelId().contains("mimo")){
            return null;
        }
        ONode body = context.body();
        if (context.provider() instanceof OpenAiChatCompletionsProvider){
            if (body.hasKey("reasoning_effort")){
                body.set("thinking",new ONode().set("type","enabled"));
                body.set("reasoning_effort",null);
            }else {
                body.set("thinking",new ONode().set("type","disabled"));
            }

        }
        return context.body();
    }
}
