package site.sorghum.cutin.core.plugin;

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
 */
public final class PluginPackageLoader {

    /** 从指定路径加载全部 LoopPlugin 实现。 */
    public List<LoopPlugin> load(Path packagePath) {
        List<LoopPlugin> plugins = new ArrayList<>();
        try (URLClassLoader classLoader = new URLClassLoader(
            new URL[]{toUrl(packagePath)},
            PluginPackageLoader.class.getClassLoader()
        )) {
            for (LoopPlugin plugin : ServiceLoader.load(LoopPlugin.class, classLoader)) {
                plugins.add(plugin);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("failed to load plugin package: " + packagePath, exception);
        }
        return plugins;
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
