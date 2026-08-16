package site.sorghum.cutin.core.plugin;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.*;

/**
 * 插件 Bean 管理器：负责插件注册、Bean 依赖注入、启动与停止。
 *
 * <p>插件可以按类或实例注册；注册时以 {@link AgentPlugin} 注解或插件 id
 * 为唯一键，并把插件实现的接口登记为可按类型查找的 Bean。
 * 启动时按 order 排序依次执行 configure、register、start。</p>
 */
public final class PluginBeanManager implements PluginContext {

    /** 插件注册的目标注册中心。 */
    private final LoopRegistrar registrar;
    /** 按 id 索引的 Bean。 */
    private final Map<String, Object> beansById = new LinkedHashMap<>();
    /** 按类型索引的 Bean。 */
    private final Map<Class<?>, Object> beansByType = new HashMap<>();
    /** 已注册的插件列表。 */
    private final List<PluginEntry> plugins = new ArrayList<>();
    /** 是否已启动。 */
    private boolean started;

    /** 创建管理器并绑定注册中心。 */
    public PluginBeanManager(LoopRegistrar registrar) {
        this.registrar = Objects.requireNonNull(registrar, "registrar");
    }

    /** 通过无参构造创建插件实例并注册。 */
    public void registerPlugin(Class<? extends LoopPlugin> pluginClass) {
        try {
            LoopPlugin plugin = pluginClass.getDeclaredConstructor().newInstance();
            registerPlugin(plugin);
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException
                 | InvocationTargetException exception) {
            throw new IllegalStateException("plugin must have a public no-arg constructor: " + pluginClass, exception);
        }
    }

    /** 注册一个已创建的插件实例，id 重复时抛出异常。 */
    public void registerPlugin(LoopPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        String id = pluginId(plugin);
        if (beansById.containsKey(id)) {
            throw new IllegalStateException("duplicate plugin id: " + id);
        }
        AgentPlugin annotation = plugin.getClass().getAnnotation(AgentPlugin.class);
        int order = annotation == null ? 0 : annotation.order();
        plugins.add(new PluginEntry(order, id, plugin));
        beansById.put(id, plugin);
        beansByType.put(plugin.getClass(), plugin);
        registerBeanInterfaces(plugin);
    }

    /** 从外部插件包目录发现并注册全部插件。 */
    public void discover(Path pluginPackage) {
        for (LoopPlugin plugin : new PluginPackageLoader().load(pluginPackage)) {
            registerPlugin(plugin);
        }
    }

    /** 按类简单名注册普通 Bean。 */
    public void registerBean(Object bean) {
        registerBean(bean.getClass().getSimpleName(), bean);
    }

    /** 按指定 id 注册普通 Bean，并登记其实现的接口。 */
    public void registerBean(String id, Object bean) {
        beansById.put(id, bean);
        beansByType.put(bean.getClass(), bean);
        registerBeanInterfaces(bean);
    }

    /** 按 order 排序并启动全部插件；重复调用不会重复启动。 */
    public void startAll() {
        if (started) {
            return;
        }
        List<PluginEntry> sorted = plugins.stream()
            .sorted(Comparator.comparingInt(PluginEntry::order))
            .toList();
        for (PluginEntry entry : sorted) {
            entry.plugin().configure(this);
            entry.plugin().register(registrar);
            entry.plugin().start();
        }
        started = true;
    }

    /** 按注册逆序停止全部插件。 */
    public void stopAll() {
        for (int i = plugins.size() - 1; i >= 0; i--) {
            plugins.get(i).plugin().stop();
        }
        started = false;
    }

    /** 按类型获取 Bean，找不到时抛出异常。 */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> type) {
        Object bean = beansByType.get(type);
        if (bean == null) {
            throw new IllegalStateException("no plugin bean of type: " + type);
        }
        return (T) bean;
    }

    /** 按名字获取 Bean，找不到时抛出异常。 */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getBean(String name) {
        Object bean = beansById.get(name);
        if (bean == null) {
            throw new IllegalStateException("no plugin bean named: " + name);
        }
        return (T) bean;
    }

    /** 解析插件 id：优先使用注解 id，其次使用插件自身 id。 */
    private String pluginId(LoopPlugin plugin) {
        AgentPlugin annotation = plugin.getClass().getAnnotation(AgentPlugin.class);
        return annotation == null ? plugin.id() : annotation.id();
    }

    /** 把 Bean 实现的接口登记到按类型索引，便于按接口注入。 */
    private void registerBeanInterfaces(Object bean) {
        for (Class<?> type : bean.getClass().getInterfaces()) {
            beansByType.putIfAbsent(type, bean);
        }
    }

    /** 插件注册记录：顺序、id 与实例。 */
    private record PluginEntry(int order, String id, LoopPlugin plugin) {
    }
}
