package site.sorghum.loopra.integration.extpack;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.test.SolonTest;
import site.sorghum.cutin.core.event.EventBus;
import site.sorghum.cutin.core.loop.InterceptPoint;
import site.sorghum.cutin.core.loop.InterceptorRegistry;
import site.sorghum.cutin.core.model.ModelRegistry;
import site.sorghum.cutin.core.plugin.DefaultLoopRegistrar;
import site.sorghum.cutin.core.tool.Tool;
import site.sorghum.loopra.integration.cutin.CutinFunctionToolBridge;
import site.sorghum.loopra.integration.cutin.CutinToolRegistryView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 拓展包仓库集成测试：通过本地 HTTP 服务模拟 JAR 直链，
 * 验证安装、Solon 容器启动、Agent 能力桥接与卸载全链路。
 */
@SolonTest(value = ExtPackTestApp.class, enableHttp = false, scanning = false)
public class ExtPackStoreTest {

    /** 临时安装目录。 */
    @TempDir
    Path tempDir;

    /** 本地 HTTP 服务（模拟下载直链）。 */
    HttpServer server;

    /** 拓展包仓库（指向临时目录）。 */
    ExtPackStore store;

    @BeforeEach
    void setUp() throws Exception {
        store = new ExtPackStore(tempDir);
        server = HttpServer.create(new InetSocketAddress(0), 0);
        byte[] jarBytes = buildExtPackJar();
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
        // 停止仍运行中的拓展包并清理静态桥接表，避免跨测试残留与 Windows 文件锁
        for (ExtPackStore.ExtPackView view : store.list()) {
            if (view.started()) {
                store.stop(view.id());
            }
        }
        LoopraExtPackRuntime.activeBridgeIds()
            .forEach(LoopraExtPackRuntime::unregisterBridgeEverywhere);
    }

    /** 本地 jar 安装：启动 Solon 容器、桥接就位，停止后全部注销、卸载后文件清理。 */
    @Test
    void installsFromLocalJarAndRunsFullLifecycle(@TempDir Path sourceDir) throws Exception {
        Path localJar = sourceDir.resolve("sample-1.0.jar");
        Files.write(localJar, buildExtPackJar());

        ExtPackStore.InstalledExtPack pack = store.install(localJar.toString());

        assertEquals("sample", pack.id());
        assertEquals("1.0", pack.version());
        assertEquals(64, pack.sha256().length());
        assertEquals(List.of(SampleExtPackBridge.class.getName()), pack.bridgeClasses());
        assertTrue(store.find("sample").isPresent());

        // 安装后自动启动：桥接已注册，清单状态正确
        ExtPackStore.ExtPackView view = store.list().stream().filter(v -> v.id().equals("sample")).findFirst().orElseThrow();
        assertTrue(view.started());
        assertEquals(1, view.bridgeCount());
        assertTrue(LoopraExtPackRuntime.activeBridgeIds().contains("sample-bridge"));

        // 停止：桥接注销
        store.stop("sample");
        view = store.list().stream().filter(v -> v.id().equals("sample")).findFirst().orElseThrow();
        assertFalse(view.started());
        assertFalse(LoopraExtPackRuntime.activeBridgeIds().contains("sample-bridge"));

        // 重新启动后卸载：文件与清单均被清理
        store.start("sample");
        store.uninstall("sample");
        assertTrue(store.list().isEmpty());
        assertFalse(Files.exists(tempDir.resolve("sample-1.0.jar")));
    }

    /** 直链安装：下载、推断 id/version、启动；桥接的工具与拦截器注册进已挂接的 AgentLoop。 */
    @Test
    void installsFromDirectLinkAndBridgesIntoAttachedAgentLoop() throws Exception {
        DefaultLoopRegistrar registrar = new DefaultLoopRegistrar();
        LoopraExtPackRuntime.attach(registrar);
        try {
            ExtPackStore.InstalledExtPack pack = store.install(baseUrl() + "/sample-1.0.jar");
            assertEquals("sample", pack.id());

            // 工具已注册进 AgentLoop 注册中心
            assertTrue(registrar.tools().find("sample-ext-tool").isPresent());
            // 拦截器已注册进 BEFORE_STEP 链
            assertEquals(1, registrar.interceptors().size(InterceptPoint.BEFORE_STEP));

            // 停止后全部注销
            store.stop("sample");
            assertFalse(registrar.tools().find("sample-ext-tool").isPresent());
            assertEquals(0, registrar.interceptors().size(InterceptPoint.BEFORE_STEP));
        } finally {
            LoopraExtPackRuntime.detach(registrar);
            store.uninstall("sample");
        }
    }

    /** 新挂接的 AgentLoop 自动补注册已启动拓展包的能力。 */
    @Test
    void newlyAttachedRegistrarReceivesActiveBridge() throws Exception {
        store.install(baseUrl() + "/sample-1.0.jar");
        DefaultLoopRegistrar late = new DefaultLoopRegistrar();
        try {
            LoopraExtPackRuntime.attach(late);
            assertTrue(late.tools().find("sample-ext-tool").isPresent());
        } finally {
            LoopraExtPackRuntime.detach(late);
            store.uninstall("sample");
        }
    }

    /**
     * 真实 AgentLoop 视图场景：桥接工具经 register 进入 CutinToolRegistryView 外部注册区，
     * loopra 工具表刷新（setTools 全量替换同步区）不得抹掉它们。
     */
    @Test
    void bridgeToolsSurviveCutinViewSyncReplacement() throws Exception {
        // 与真实 AgentLoop 同构：DefaultLoopRegistrar 包装 CutinToolRegistryView
        CutinToolRegistryView view = new CutinToolRegistryView();
        DefaultLoopRegistrar registrar = new DefaultLoopRegistrar(
            new InterceptorRegistry(), view, new ModelRegistry(), new EventBus());
        LoopraExtPackRuntime.attach(registrar);
        try {
            LoopraExtPackRuntime.registerBridgeEverywhere("sample-bridge", new SampleExtPackBridge());
            assertTrue(view.find("sample-ext-tool").isPresent());

            // 模拟 loopra 工具表刷新：refresh/register 均触发 syncCutinRegistry → setTools
            view.setTools(List.of(new CutinFunctionToolBridge(simpleFunctionTool("ping"))));
            assertTrue(view.find("sample-ext-tool").isPresent(), "loopra 工具表同步不能抹掉桥接工具");
            assertTrue(view.find("ping").isPresent());

            // 桥接注销：外部注册工具可正常移除
            LoopraExtPackRuntime.unregisterBridgeEverywhere("sample-bridge");
            assertFalse(view.find("sample-ext-tool").isPresent());
        } finally {
            LoopraExtPackRuntime.detach(registrar);
        }
    }

    private static FunctionTool simpleFunctionTool(String name) {
        Map<String, Object> meta = new LinkedHashMap<>();
        return new FunctionTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String title() {
                return name;
            }

            @Override
            public String description() {
                return "Simple tool.";
            }

            @Override
            public boolean returnDirect() {
                return false;
            }

            @Override
            public String inputSchema() {
                return "{\"type\":\"object\",\"properties\":{}}";
            }

            @Override
            public Type returnType() {
                return String.class;
            }

            @Override
            public Object handle(Map<String, Object> args) {
                return "pong";
            }

            @Override
            public Map<String, Object> meta() {
                return meta;
            }

            @Override
            public void metaPut(String key, Object value) {
                meta.put(key, value);
            }
        };
    }

    /** 停用后 loadAll 跳过该拓展包；重新启用后恢复启动。 */
    @Test
    void disabledExtPackIsSkippedOnLoadAll() throws Exception {
        store.install(baseUrl() + "/sample-1.0.jar");
        store.setEnabled("sample", false);

        ExtPackStore.InstalledExtPack disabled = store.find("sample").orElseThrow();
        assertFalse(disabled.enabled());
        assertFalse(store.list().stream().filter(v -> v.id().equals("sample")).findFirst().orElseThrow().started());

        ExtPackStore reloaded = new ExtPackStore(tempDir);
        reloaded.loadAll();
        assertFalse(reloaded.list().stream().filter(v -> v.id().equals("sample")).findFirst().orElseThrow().started());

        store.setEnabled("sample", true);
        ExtPackStore reloaded2 = new ExtPackStore(tempDir);
        reloaded2.loadAll();
        assertTrue(reloaded2.list().stream().filter(v -> v.id().equals("sample")).findFirst().orElseThrow().started());
        // 清理第二实例的运行时（running 为实例字段，tearDown 只停 store 本实例）
        reloaded2.stop("sample");
    }

    /** 非 .jar 结尾、不存在的本地路径或未知卸载目标均报错。 */
    @Test
    void validatesSourceAndUninstallTarget() {
        assertThrows(IllegalArgumentException.class,
            () -> store.install(baseUrl() + "/sample-1.0.zip"));
        assertThrows(IllegalArgumentException.class,
            () -> store.install("ftp://example.com/sample-1.0.jar"));
        assertThrows(IllegalArgumentException.class,
            () -> store.install(tempDir.resolve("missing-1.0.jar").toString()));
        assertThrows(IllegalArgumentException.class,
            () -> store.uninstall("not-exists"));
    }

    // ==================== 辅助 ====================

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** 动态构建包含桥接实现、Solon 插件声明与服务文件的拓展包 jar 字节。 */
    private byte[] buildExtPackJar() throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(buffer)) {
            // 桥接服务声明（LoopraExtPackBridge）
            jar.putNextEntry(new JarEntry(
                "META-INF/services/site.sorghum.loopra.integration.extpack.LoopraExtPackBridge"));
            jar.write((SampleExtPackBridge.class.getName()
                + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();

            // Solon 插件声明（META-INF/solon/*.properties）
            jar.putNextEntry(new JarEntry("META-INF/solon/extpack.properties"));
            jar.write(("solon.plugin=" + SampleExtPackPlugin.class.getName()
                + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();

            writeClass(jar, SampleExtPackBridge.class);
            writeClass(jar, SampleExtPackBridge.SampleExtTool.class);
            writeClass(jar, SampleExtPackPlugin.class);
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
