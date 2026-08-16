package site.sorghum.cutin.plugins;

import site.sorghum.cutin.core.loop.InterceptDecision;
import site.sorghum.cutin.core.loop.InterceptPoint;
import site.sorghum.cutin.core.plugin.AgentPlugin;
import site.sorghum.cutin.core.plugin.LoopPlugin;
import site.sorghum.cutin.core.plugin.LoopRegistrar;

/**
 * 插件包示例：通过 ServiceLoader 发现时，在 BEFORE_STEP 中写入标记变量。
 */
@AgentPlugin(id = "package-sample")
public class PackageSamplePlugin implements LoopPlugin {

    /** 无参构造，供 ServiceLoader 实例化。 */
    public PackageSamplePlugin() {
    }

    /** {@inheritDoc} */
    @Override
    public String id() {
        return "package-sample";
    }

    /** 注册 BEFORE_STEP 拦截器，写入包插件生效标记。 */
    @Override
    public void register(LoopRegistrar registrar) {
        registrar.addInterceptor(InterceptPoint.BEFORE_STEP, 400, context -> {
            context.context().putVariable("packagePluginApplied", true);
            return InterceptDecision.pass();
        });
    }
}
