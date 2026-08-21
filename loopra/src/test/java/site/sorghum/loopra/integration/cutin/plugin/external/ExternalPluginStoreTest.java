package site.sorghum.loopra.integration.cutin.plugin.external;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import site.sorghum.cutin.core.plugin.PluginBeanManager;
import site.sorghum.loopra.integration.cutin.plugin.external.ExternalPluginStore.InstalledPlugin;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 外置插件仓库集成测试：通过本地 HTTP 服务模拟 JAR 直链，
 * 验证安装、启动加载、启停与卸载全链路。
 */
class ExternalPluginStoreTest {

    /** 临时安装目录。 */
    @TempDir
    Path tempDir;

    /** 本地 HTTP 服务。 */
    HttpServer server;

    /** 外置插件仓库（指向临时目录）。 */
    ExternalPluginStore store;

    @BeforeEach
    void setUp() throws Exception {
        store = new ExternalPluginStore(tempDir);
        server = HttpServer.create(new InetSocketAddress(0), 0);
        byte[] jarBytes = buildPluginJar();
        server.createContext("/sample-1.0.jar", exchange -> {
            exchange.sendResponseHeaders(200, jarBytes.length);
            exchange.getResponseBody().write(jarBytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    /** 直链安装：下载 jar、从文件名推断 id/version、写入清单并可被管理器加载。 */
    @Test
    void installsFromDirectLinkAndLoadsIntoManager() throws Exception {
        String url = baseUrl() + "/sample-1.0.jar";
        InstalledPlugin plugin = store.install(url);

        assertEquals("sample", plugin.id());
        assertEquals("1.0", plugin.version());
        assertEquals(url, plugin.sourceUrl());
        assertTrue(store.find("sample").isPresent());
        assertEquals(64, plugin.sha256().length());

        PluginBeanManager manager = new PluginBeanManager(new site.sorghum.cutin.core.plugin.DefaultLoopRegistrar());
        store.loadInto(manager);
        assertTrue(manager.pluginStates().stream().anyMatch(s -> s.id().equals("sample")));

        // 卸载后清单与本地文件均被清理，再次加载不再出现
        manager.unregisterPlugin("sample");
        store.uninstall("sample");
        assertTrue(store.installed().isEmpty());
        assertFalse(Files.exists(tempDir.resolve("sample-1.0.jar")));
    }

    /** 停用后 loadInto 跳过该插件；重新启用后恢复加载。 */
    @Test
    void disabledPluginIsSkippedOnStartupLoad() throws Exception {
        store.install(baseUrl() + "/sample-1.0.jar");
        store.setEnabled("sample", false);

        PluginBeanManager manager = new PluginBeanManager(new site.sorghum.cutin.core.plugin.DefaultLoopRegistrar());
        store.loadInto(manager);
        assertFalse(manager.pluginStates().stream().anyMatch(s -> s.id().equals("sample")));

        store.setEnabled("sample", true);
        PluginBeanManager reloaded = new PluginBeanManager(new site.sorghum.cutin.core.plugin.DefaultLoopRegistrar());
        store.loadInto(reloaded);
        assertTrue(reloaded.pluginStates().stream().anyMatch(s -> s.id().equals("sample")));

        // 释放类加载器，避免 Windows 下临时目录因文件锁无法删除
        reloaded.unregisterPlugin("sample");
    }

    /** 摘要校验失败时拒绝加载（模拟 jar 被篡改）。 */
    @Test
    void rejectsTamperedJarOnStartupLoad() throws Exception {
        store.install(baseUrl() + "/sample-1.0.jar");
        // 篡改清单中的摘要
        Path manifest = tempDir.resolve("installed.json");
        String json = Files.readString(manifest, StandardCharsets.UTF_8)
            .replaceFirst("\"sha256\": \"[0-9a-f]{64}\"", "\"sha256\": \"" + "0".repeat(64) + "\"");
        Files.writeString(manifest, json, StandardCharsets.UTF_8);

        PluginBeanManager manager = new PluginBeanManager(new site.sorghum.cutin.core.plugin.DefaultLoopRegistrar());
        store.loadInto(manager);
        assertFalse(manager.pluginStates().stream().anyMatch(s -> s.id().equals("sample")));
    }

    /** 非 .jar 结尾的来源被拒绝；不存在的插件卸载报错。 */
    @Test
    void validatesSourceAndUninstallTarget() {
        assertThrows(IllegalArgumentException.class,
            () -> store.install(baseUrl() + "/sample-1.0.zip"));
        assertThrows(IllegalArgumentException.class,
            () -> store.install("ftp://example.com/sample-1.0.jar"));
        assertThrows(IllegalArgumentException.class,
            () -> store.uninstall("not-exists"));
    }

    // ==================== 辅助 ====================

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** 动态构建包含 SampleExternalPlugin 与服务文件的插件 jar 字节。 */
    private byte[] buildPluginJar() throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(buffer)) {
            jar.putNextEntry(new JarEntry(
                "META-INF/services/site.sorghum.cutin.core.plugin.LoopPlugin"));
            jar.write(("site.sorghum.loopra.integration.cutin.plugin.external.SampleExternalPlugin"
                + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();

            writeClass(jar, SampleExternalPlugin.class);
        }
        return buffer.toByteArray();
    }

    /** 把类及其字节码写入 jar。 */
    private static void writeClass(JarOutputStream jar, Class<?> type) throws Exception {
        String entryName = type.getName().replace('.', '/') + ".class";
        jar.putNextEntry(new JarEntry(entryName));
        try (InputStream input = type.getClassLoader().getResourceAsStream(entryName)) {
            input.transferTo(jar);
        }
        jar.closeEntry();
    }
}
