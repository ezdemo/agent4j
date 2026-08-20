package site.sorghum.cutin.core.plugin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginBeanManagerStateTest {

    @Test
    void exposesAndUpdatesPluginRuntimeState() {
        PluginBeanManager manager = new PluginBeanManager(new DefaultLoopRegistrar());
        manager.registerPlugin(new TestPlugin());
        manager.startAll();

        assertTrue(manager.pluginStates().get(0).active());
        manager.stopPlugin("state-test");
        assertFalse(manager.pluginStates().get(0).active());
        manager.startPlugin("state-test");
        assertTrue(manager.pluginStates().get(0).active());
    }

    @AgentPlugin(id = "state-test", order = 7)
    private static final class TestPlugin implements LoopPlugin {
        @Override
        public String id() {
            return "state-test";
        }

        @Override
        public void register(LoopRegistrar registrar) {
        }
    }
}
