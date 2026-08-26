package site.sorghum.loopra.extra.demo;

import org.noear.solon.core.AppContext;
import org.noear.solon.core.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 示例拓展包的 Solon 插件：演示 H-SPI 容器生命周期。
 *
 * <p>在 jar 内以 {@code META-INF/solon/extpack.properties} 声明
 * （{@code solon.plugin=...DemoExtPackPlugin}），由 Solon 容器在
 * {@code PluginPackage.start()} 时实例化并回调。</p>
 */
public class DemoExtPackPlugin implements Plugin {

    private static final Logger log = LoggerFactory.getLogger(DemoExtPackPlugin.class);

    /** 无参构造，供 Solon SPI 实例化。 */
    public DemoExtPackPlugin() {
    }

    @Override
    public void start(AppContext context) {
        // 容器已可用：注册路由组件（也可在此 beanScan 自己的包、注册 Job/事件等）
        context.beanMake(DemoExtController.class);
        log.info("[extpack-demo] 插件已启动，路由 /api/ext-demo/* 已挂载");
    }

    @Override
    public void preStop() {
        // 停止前回调：可在此反注册外部资源
    }

    @Override
    public void stop() {
        // 停止回调：清理路由/job/事件等资源（示例无外部资源，仅记录）
        log.info("[extpack-demo] 插件已停止");
    }
}
