package site.sorghum.loopra.extra.demo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.noear.solon.test.SolonTest;
import site.sorghum.cutin.core.loop.InterceptPoint;
import site.sorghum.cutin.core.plugin.DefaultLoopRegistrar;
import site.sorghum.cutin.core.tool.Tool;
import site.sorghum.cutin.core.tool.ToolCall;
import site.sorghum.cutin.core.tool.ToolResult;
import site.sorghum.loopra.integration.extpack.ExtPackStore;
import site.sorghum.loopra.integration.extpack.LoopraExtPackRuntime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 示例拓展包全链路冒烟测试：把 target/classes 打成拓展包 jar，
 * 经 ExtPackStore 安装 → Solon 容器启动 → 桥接工具/拦截器注册进
 * AgentLoop → 停止注销 → 卸载清理。
 */
@SolonTest(value = DemoTestApp.class, enableHttp = false, scanning = false)
public class DemoExtPackSmokeTest {

    /** 临时安装目录（不用 @TempDir：Windows 下 jar 句柄可能延迟释放，手动创建+尽力删除）。 */
    Path tempDir;
    ExtPackStore store;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("extpack-demo");
        store = new ExtPackStore(tempDir);
    }

    @AfterEach
    void tearDown() {
        // 停止仍运行中的拓展包并清理静态桥接表
        for (ExtPackStore.ExtPackView view : store.list()) {
            if (view.started()) {
                store.stop(view.id());
            }
        }
        LoopraExtPackRuntime.activeBridgeIds()
            .forEach(LoopraExtPackRuntime::unregisterBridgeEverywhere);
        try (var paths = Files.walk(tempDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // 句柄未释放时容忍残留
                }
            });
        } catch (IOException ignored) {
            // 目录不可遍历，跳过
        }
    }

    @Test
    void fullLifecycleBridgesToolsAndInterceptors() throws Exception {
        Path jar = tempDir.resolve("loopra-extra-demo-1.0.0.jar");
        Files.write(jar, buildDemoJar());

        DefaultLoopRegistrar registrar = new DefaultLoopRegistrar();
        LoopraExtPackRuntime.attach(registrar);
        try {
            ExtPackStore.InstalledExtPack pack = store.install(jar.toString());
            assertEquals("loopra-extra-demo", pack.id());
            assertEquals("1.0.0", pack.version());
            assertTrue(pack.bridgeClasses().contains(DemoExtPackBridge.class.getName()));

            // 安装即启动：桥接已注册
            assertTrue(LoopraExtPackRuntime.activeBridgeIds().contains("demo-bridge"));
            ExtPackStore.ExtPackView view = store.list().get(0);
            assertTrue(view.started());
            assertEquals(1, view.bridgeCount());

            // 工具已注册进 AgentLoop 注册中心
            Tool timeTool = registrar.tools().find("demo-server-time").orElseThrow();
            Tool greetingTool = registrar.tools().find("demo-greeting").orElseThrow();

            // 调用工具：默认格式与自定义格式
            ToolResult time = timeTool.call(new ToolCall("c1", "demo-server-time", Map.of(), null), null);
            assertTrue(time.ok(), "服务器时间工具应成功: " + time);
            ToolResult custom = timeTool.call(
                new ToolCall("c2", "demo-server-time", Map.of("format", "yyyy/MM/dd"), null), null);
            assertTrue(custom.ok(), "自定义格式应成功: " + custom);
            assertTrue(String.valueOf(custom.content()).matches("\\d{4}/\\d{2}/\\d{2}"), "格式应生效: " + custom);

            // 参数缺失/格式错误 → 失败路径
            ToolResult noName = greetingTool.call(new ToolCall("c3", "demo-greeting", Map.of(), null), null);
            assertFalse(noName.ok(), "缺少 name 应失败");
            ToolResult badFormat = timeTool.call(
                new ToolCall("c4", "demo-server-time", Map.of("format", "'"), null), null);
            assertFalse(badFormat.ok(), "非法格式应失败");

            // 拦截器已注册进 BEFORE_STEP 链
            assertEquals(1, registrar.interceptors().size(InterceptPoint.BEFORE_STEP));

            // 停止：桥接与扩展全部注销
            store.stop("loopra-extra-demo");
            assertFalse(LoopraExtPackRuntime.activeBridgeIds().contains("demo-bridge"));
            assertFalse(registrar.tools().find("demo-server-time").isPresent());
            assertEquals(0, registrar.interceptors().size(InterceptPoint.BEFORE_STEP));

            // 卸载：清单与文件清理
            store.uninstall("loopra-extra-demo");
            assertTrue(store.list().isEmpty());
            assertFalse(Files.exists(tempDir.resolve("loopra-extra-demo-1.0.0.jar")));
        } finally {
            LoopraExtPackRuntime.detach(registrar);
        }
    }

    /** 把 target/classes（含 META-INF 服务文件与 Solon 插件声明）打成拓展包 jar。 */
    private byte[] buildDemoJar() throws Exception {
        Path classes = Path.of("target", "classes");
        assertTrue(Files.isDirectory(classes), "target/classes 不存在，请先执行 mvn compile");
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(buffer)) {
            try (var paths = Files.walk(classes)) {
                for (Path file : paths.filter(Files::isRegularFile).toList()) {
                    String entry = classes.relativize(file).toString().replace('\\', '/');
                    jar.putNextEntry(new JarEntry(entry));
                    jar.write(Files.readAllBytes(file));
                    jar.closeEntry();
                }
            }
        }
        return buffer.toByteArray();
    }
}
