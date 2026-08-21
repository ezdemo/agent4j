package site.sorghum.cutin.core.plugin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 外置插件生命周期测试：验证类加载器随插件注册/卸载正确持有与释放。
 */
class ExternalPluginLifecycleTest {

    /** 临时插件包目录。 */
    @TempDir
    Path tempDir;

    /** 在指定目录写入服务文件，注册给定插件实现。 */
    private static void writeServices(Path dir, List<String> impls) throws Exception {
        Path services = dir.resolve("META-INF/services");
        Files.createDirectories(services);
        Files.writeString(
            services.resolve("site.sorghum.cutin.core.plugin.LoopPlugin"),
            String.join(System.lineSeparator(), impls) + System.lineSeparator(),
            StandardCharsets.UTF_8
        );
    }

    /** 卸载后重新 discover 同一路径仍成功，证明旧加载器已释放且不影响新加载。 */
    @Test
    void rediscoverAfterUnregister() throws Exception {
        writeServices(tempDir, List.of("site.sorghum.cutin.plugins.PackageSamplePlugin"));
        PluginBeanManager manager = new PluginBeanManager(new DefaultLoopRegistrar());

        manager.discover(tempDir);
        assertTrue(manager.pluginStates().stream().anyMatch(s -> s.id().equals("package-sample")));

        manager.unregisterPlugin("package-sample");
        assertFalse(manager.pluginStates().stream().anyMatch(s -> s.id().equals("package-sample")));

        manager.discover(tempDir);
        assertTrue(manager.pluginStates().stream().anyMatch(s -> s.id().equals("package-sample")));
    }

    /** 同一 jar 的两个插件共享类加载器：卸载其一不影响另一个启停。 */
    @Test
    void sharedClassLoaderSurvivesPartialUnregister() throws Exception {
        writeServices(tempDir, List.of(
            "site.sorghum.cutin.plugins.PackageSamplePlugin",
            "site.sorghum.cutin.plugins.SecondPackageSamplePlugin"
        ));
        PluginBeanManager manager = new PluginBeanManager(new DefaultLoopRegistrar());
        manager.discover(tempDir);
        manager.startAll();
        assertEquals(2, manager.pluginStates().size());

        // 卸载第一个：加载器仍被第二个插件引用，不应关闭
        manager.unregisterPlugin("package-sample");
        assertTrue(manager.pluginStates().stream().anyMatch(s -> s.id().equals("package-sample-second")));
        manager.stopPlugin("package-sample-second");
        manager.startPlugin("package-sample-second");

        // 全部卸载后加载器归零关闭；再次 discover 仍可正常加载
        manager.unregisterPlugin("package-sample-second");
        manager.discover(tempDir);
        assertEquals(2, manager.pluginStates().size());
    }
}
