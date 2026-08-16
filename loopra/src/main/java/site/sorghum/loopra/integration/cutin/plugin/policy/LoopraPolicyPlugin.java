package site.sorghum.loopra.integration.cutin.plugin.policy;

import site.sorghum.cutin.core.plugin.AgentPlugin;
import site.sorghum.cutin.core.plugin.LoopPlugin;
import site.sorghum.cutin.core.plugin.LoopRegistrar;

/**
  * 向后兼容的门面：把拆分开的模型与工具策略插件作为一个整体注册。
 */
@AgentPlugin(id = "loopra-policy")
public final class LoopraPolicyPlugin implements LoopPlugin {

    private final LoopraPolicyHost host;

    public LoopraPolicyPlugin(LoopraPolicyHost host) {
        this.host = host;
    }

    @Override
    public String id() {
        return "loopra-policy";
    }

    @Override
    public void register(LoopRegistrar registrar) {
        new LoopraModelPolicyPlugin(host).register(registrar);
        new LoopraToolPolicyPlugin(host).register(registrar);
    }
}
