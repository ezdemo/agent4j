package site.sorghum.cutin.core.plugin;

import org.junit.jupiter.api.Test;
import site.sorghum.cutin.core.loop.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 插件 Bean 管理器测试：验证插件注册、Bean 注入与启动流程。
 */
class PluginBeanManagerTest {

    /** 插件应能通过 Bean 管理器注册并启动，效果在循环中可见。 */
    @Test
    void pluginIsRegisteredAndStartedWithoutYaml() throws Exception {
        DefaultLoopEngine engine = new DefaultLoopEngine();
        PluginBeanManager manager = new PluginBeanManager(engine.registrar());

        manager.registerBean("greeting", "hello");
        manager.registerPlugin(StatePlugin.class);
        manager.startAll();

        LoopProgram program = LoopProgram.builder("plugin")
            .node("finish", NodeType.CODE, Steps.finish())
            .build();

        LoopHandle handle = engine.run(program, Map.of());
        LoopResult result = handle.result().get(5, TimeUnit.SECONDS);

        assertEquals(LoopResult.Status.COMPLETED, result.status());
        assertTrue(result.finalSnapshot().variables().containsKey("pluginApplied"));
        assertEquals("hello", manager.getBean("greeting"));
    }

    /** 测试用状态插件：在 BEFORE_STEP 中写入变量。 */
    static class StatePlugin implements LoopPlugin {

        @Override
        public String id() {
            return "state-plugin";
        }

        @Override
        public void register(LoopRegistrar registrar) {
            registrar.addInterceptor(InterceptPoint.BEFORE_STEP, 300, context -> {
                context.context().putVariable("pluginApplied", true);
                return InterceptDecision.pass();
            });
        }
    }
}
