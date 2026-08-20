package site.sorghum.loopra.integration.cutin.plugin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import site.sorghum.cutin.core.plugin.AgentPlugin;
import site.sorghum.cutin.core.plugin.DefaultLoopRegistrar;
import site.sorghum.cutin.core.plugin.LoopPlugin;
import site.sorghum.cutin.core.plugin.LoopRegistrar;
import site.sorghum.cutin.core.plugin.PluginBeanManager;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoopraPluginRuntimeTest {

    private final PluginBeanManager first = manager();
    private final PluginBeanManager second = manager();

    @AfterEach
    void cleanup() {
        LoopraPluginRuntime.setEnabled("runtime-test", true);
        LoopraPluginRuntime.detach(first);
        LoopraPluginRuntime.detach(second);
        first.stopAll();
        second.stopAll();
    }

    @Test
    void broadcastsHotSwapAndAppliesPolicyToNewManagers() {
        LoopraPluginRuntime.configureDisabled(Set.of());
        LoopraPluginRuntime.attach(first, Set.of());
        LoopraPluginRuntime.attach(second, Set.of());

        LoopraPluginRuntime.PluginView disabled = LoopraPluginRuntime.setEnabled("runtime-test", false);
        assertFalse(disabled.enabled());
        assertEquals(0, disabled.activeInstances());
        assertEquals(2, disabled.totalInstances());

        PluginBeanManager later = manager();
        try {
            LoopraPluginRuntime.attach(later, Set.of());
            assertFalse(later.pluginStates().get(0).active());

            LoopraPluginRuntime.PluginView enabled = LoopraPluginRuntime.setEnabled("runtime-test", true);
            assertTrue(enabled.enabled());
            assertEquals(3, enabled.activeInstances());
        } finally {
            LoopraPluginRuntime.detach(later);
            later.stopAll();
        }
    }

    private static PluginBeanManager manager() {
        PluginBeanManager manager = new PluginBeanManager(new DefaultLoopRegistrar());
        manager.registerPlugin(new RuntimeTestPlugin());
        manager.startAll();
        return manager;
    }

    @AgentPlugin(id = "runtime-test", remark = "运行时切换测试插件")
    private static final class RuntimeTestPlugin implements LoopPlugin {
        @Override
        public String id() {
            return "runtime-test";
        }

        @Override
        public void register(LoopRegistrar registrar) {
        }
    }
}
