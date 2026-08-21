package site.sorghum.cutin.plugins;

import site.sorghum.cutin.core.loop.InterceptDecision;
import site.sorghum.cutin.core.loop.InterceptPoint;
import site.sorghum.cutin.core.plugin.AgentPlugin;
import site.sorghum.cutin.core.plugin.LoopPlugin;
import site.sorghum.cutin.core.plugin.LoopRegistrar;

/**
 * 插件包示例二：与 {@link PackageSamplePlugin} 同包加载，用于验证多插件共享类加载器。
 */
@AgentPlugin(id = "package-sample-second")
public class SecondPackageSamplePlugin implements LoopPlugin {

    /** 无参构造，供 ServiceLoader 实例化。 */
    public SecondPackageSamplePlugin() {
    }

    /** {@inheritDoc} */
    @Override
    public String id() {
        return "package-sample-second";
    }

    /** 注册 BEFORE_STEP 拦截器，写入第二个包插件生效标记。 */
    @Override
    public void register(LoopRegistrar registrar) {
        registrar.addInterceptor(InterceptPoint.BEFORE_STEP, 401, context -> {
            context.context().putVariable("packageSecondApplied", true);
            return InterceptDecision.pass();
        });
    }
}
