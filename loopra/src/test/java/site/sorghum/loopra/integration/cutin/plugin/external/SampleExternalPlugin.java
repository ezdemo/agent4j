package site.sorghum.loopra.integration.cutin.plugin.external;

import site.sorghum.cutin.core.plugin.LoopPlugin;
import site.sorghum.cutin.core.plugin.LoopRegistrar;
import site.sorghum.loopra.integration.cutin.plugin.external.ExternalPluginStore.InstalledPlugin;

import java.util.List;

/**
 * 外置插件示例：供 {@link ExternalPluginStoreTest} 动态打包进 jar 并通过 ServiceLoader 发现。
 */
public class SampleExternalPlugin implements LoopPlugin {

    /** 无参构造，供 ServiceLoader 实例化。 */
    public SampleExternalPlugin() {
    }

    /** {@inheritDoc} */
    @Override
    public String id() {
        return "sample";
    }

    /** 注册阶段登记一个无操作 Hook，验证注册链路可达即可。 */
    @Override
    public void register(LoopRegistrar registrar) {
        registrar.addHook(new site.sorghum.cutin.core.event.Hook() {
            @Override
            public String id() {
                return "sample-hook";
            }

            @Override
            public void run(site.sorghum.cutin.core.event.LoopEvent event) {
                // 无操作
            }
        });
    }

    /** 供测试断言的标记方法。 */
    public List<String> markers() {
        return List.of("sample");
    }
}
