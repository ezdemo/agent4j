package site.sorghum.loopra.integration.extpack;

import site.sorghum.cutin.core.plugin.LoopRegistrar;
import site.sorghum.cutin.core.plugin.Registration;
import site.sorghum.cutin.core.tool.Tool;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loopra 进程级拓展包桥接运行时：把已启动拓展包贡献的 Agent 能力
 * 同步到全部存活的 AgentLoop 注册中心。
 *
 * <p>与 {@code LoopraPluginRuntime} 的差异：cutin 插件在每个 AgentLoop
 * 内各有一份实例；H-SPI 拓展包是进程级单例（一个 jar 一份），因此这里
 * 登记的是各 AgentLoop 的 {@link LoopRegistrar}（弱引用），并维护
 * 全局已启动桥接的注册句柄——新 AgentLoop 创建时自动补注册，拓展包
 * 停止时统一逆序注销。</p>
 */
public final class LoopraExtPackRuntime {

    /** 存活 AgentLoop 的注册中心（弱引用，Agent 淘汰后自动清理）。 */
    private static final List<WeakReference<LoopRegistrar>> REGISTRARS = new ArrayList<>();
    /** 已启动拓展包：bridgeId → 桥接运行态。 */
    private static final Map<String, ActiveBridge> ACTIVE = new LinkedHashMap<>();

    private LoopraExtPackRuntime() {
    }

    /** 登记一个 AgentLoop 的注册中心；已启动的拓展包自动补注册。 */
    public static synchronized void attach(LoopRegistrar registrar) {
        cleanupRegistrars();
        if (REGISTRARS.stream().anyMatch(ref -> ref.get() == registrar)) {
            return;
        }
        REGISTRARS.add(new WeakReference<>(registrar));
        for (ActiveBridge active : ACTIVE.values()) {
            // 新登记的 registrar 单独注册；既有句柄列表不含它
            active.addHandle(registerTo(registrar, active.bridge));
        }
    }

    /** 解除登记（AgentLoop 销毁时调用）。 */
    public static synchronized void detach(LoopRegistrar registrar) {
        REGISTRARS.removeIf(ref -> ref.get() == null || ref.get() == registrar);
    }

    /**
     * 把拓展包桥接注册到全部存活 AgentLoop，返回聚合注册句柄；
     * 停止拓展包时关闭该句柄即可逆序注销全部扩展。
     *
     * <p>重复注册同一 bridgeId 返回既有句柄（幂等），避免重复注册。</p>
     */
    public static synchronized Registration registerBridgeEverywhere(String bridgeId, LoopraExtPackBridge bridge) {
        ActiveBridge existing = ACTIVE.get(bridgeId);
        if (existing != null) {
            return existing;
        }
        cleanupRegistrars();
        ActiveBridge active = new ActiveBridge(bridge);
        for (LoopRegistrar registrar : liveRegistrars()) {
            active.addHandle(registerTo(registrar, bridge));
        }
        ACTIVE.put(bridgeId, active);
        return active;
    }

    /** 从全部存活 AgentLoop 注销指定拓展包的扩展。 */
    public static synchronized void unregisterBridgeEverywhere(String bridgeId) {
        ActiveBridge active = ACTIVE.remove(bridgeId);
        if (active != null) {
            active.close();
        }
    }

    /** 当前已启动的拓展包桥接 id 列表。 */
    public static synchronized List<String> activeBridgeIds() {
        return List.copyOf(ACTIVE.keySet());
    }

    /** 把单个桥接注册进一个 registrar，返回句柄。 */
    private static Registration registerTo(LoopRegistrar registrar, LoopraExtPackBridge bridge) {
        List<Registration> handles = new ArrayList<>();
        for (Tool tool : bridge.tools()) {
            handles.add(registrar.registerTool(tool));
        }
        for (LoopraExtPackBridge.RegisteredInterceptor entry : bridge.interceptors()) {
            handles.add(registrar.registerInterceptor(entry.point(), entry.order(), entry.interceptor()));
        }
        return Registration.composite(handles);
    }

    private static List<LoopRegistrar> liveRegistrars() {
        return REGISTRARS.stream().map(WeakReference::get).filter(ref -> ref != null).toList();
    }

    private static void cleanupRegistrars() {
        REGISTRARS.removeIf(ref -> ref.get() == null);
    }

    /** 已启动桥接的运行态：桥接实例 + 可增长的每-registrar 句柄列表。 */
    static final class ActiveBridge implements Registration {
        private final LoopraExtPackBridge bridge;
        private final List<Registration> handles = new ArrayList<>();
        private boolean closed;

        private ActiveBridge(LoopraExtPackBridge bridge) {
            this.bridge = bridge;
        }

        /** 追加一个新 registrar 的注册句柄（幂等关闭后忽略）。 */
        synchronized void addHandle(Registration handle) {
            if (!closed) {
                handles.add(handle);
            }
        }

        /** 逆序关闭全部句柄；重复调用安全。 */
        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            for (int i = handles.size() - 1; i >= 0; i--) {
                handles.get(i).close();
            }
        }
    }
}
