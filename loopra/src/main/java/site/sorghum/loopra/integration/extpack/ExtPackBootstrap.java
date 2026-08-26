package site.sorghum.loopra.integration.extpack;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Init;

/**
 * 拓展包启动引导：主程序启动时按清单加载并启动全部启用的拓展包。
 *
 * <p>与 cutin 外置插件的差异：拓展包是进程级单例，只需在此加载一次；
 * 后续新建的 AgentLoop 会在构造时经由 {@link LoopraExtPackRuntime#attach}
 * 自动补注册其贡献的 Agent 能力。</p>
 */
@Slf4j
@Component
public class ExtPackBootstrap {

    /** 拓展包仓库（无状态，按需创建）。 */
    private final ExtPackStore store = new ExtPackStore();

    @Init
    public void init() {
        try {
            store.loadAll();
            log.info("[extpack] 启动加载完成，运行中拓展包: {}", LoopraExtPackRuntime.activeBridgeIds());
        } catch (RuntimeException exception) {
            log.warn("[extpack] 启动加载拓展包失败（不影响主程序）: {}", exception.getMessage());
        }
    }
}
