package site.sorghum.loopra.integration.extpack;

import org.noear.solon.core.AppContext;
import org.noear.solon.core.Plugin;

/**
 * 拓展包 Solon 插件示例：通过 {@code META-INF/solon/extpack.properties}
 * 声明，验证 Solon 容器生命周期（start/prestop/stop）与桥接发现并存。
 */
public class SampleExtPackPlugin implements Plugin {

    /** 无参构造，供 Solon SPI 实例化。 */
    public SampleExtPackPlugin() {
    }

    @Override
    public void start(AppContext context) {
        // 容器已可用：可在此 beanScan 自己的组件、注册路由等
    }

    @Override
    public void stop() {
        // 需在此移除路由/job/事件等资源
    }
}
