package site.sorghum.cutin.core.plugin;

import java.io.Closeable;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * 插件包加载器：通过 {@link ServiceLoader} 从外部目录/JAR 发现插件实现。
 *
 * <p>使用独立的 {@link URLClassLoader} 加载包内的
 * {@code META-INF/services/site.sorghum.cutin.core.plugin.LoopPlugin} 服务文件。</p>
 *
 * <p>返回的 {@link LoadedPackage} 持有类加载器引用，由调用方（通常是
 * {@link PluginBeanManager}）在插件卸载时关闭，避免插件运行期间懒加载资源失败，
 * 同时保证热卸载时类加载器可被真正释放。</p>
 */
public final class PluginPackageLoader {

    /** 加载结果：发现的插件列表与其类加载器；关闭结果即释放类加载器。 */
    public record LoadedPackage(List<LoopPlugin> plugins, URLClassLoader classLoader) implements Closeable {

        /** 关闭底层类加载器；仅在全部插件卸载后调用。 */
        @Override
        public void close() throws java.io.IOException {
            classLoader.close();
        }
    }

    /** 从指定路径加载全部 LoopPlugin 实现，返回携带类加载器的加载结果。 */
    public LoadedPackage load(Path packagePath) {
        URLClassLoader classLoader = new URLClassLoader(
            new URL[]{toUrl(packagePath)},
            PluginPackageLoader.class.getClassLoader()
        );
        List<LoopPlugin> plugins = new ArrayList<>();
        try {
            for (LoopPlugin plugin : ServiceLoader.load(LoopPlugin.class, classLoader)) {
                plugins.add(plugin);
            }
        } catch (Exception exception) {
            try {
                classLoader.close();
            } catch (Exception ignored) {
                // 关闭失败不影响原始异常抛出
            }
            throw new IllegalStateException("failed to load plugin package: " + packagePath, exception);
        }
        return new LoadedPackage(List.copyOf(plugins), classLoader);
    }

    /** 把路径转换为 URL，失败时抛出带路径信息的异常。 */
    private static URL toUrl(Path path) {
        try {
            return path.toUri().toURL();
        } catch (Exception exception) {
            throw new IllegalArgumentException("invalid plugin package path: " + path, exception);
        }
    }
}
