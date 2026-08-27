package site.sorghum.loopra.bin.session;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;
import site.sorghum.loopra.bin.agent.core.SubAgent;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 子代理会话管理器 —— 维护「活跃子代理」（可继续对话）的进程内注册表。
 * <p>
 * 子代理执行是一次性任务对象（SubAgentTool 执行完即丢弃），但用户需要在执行结束后
 * 对同一子代理会话继续对话。管理器在子代理创建时登记实例，继续对话端点据此复用
 * 同一 SubAgent（其子循环与上下文在首次 run 后保留，支持多轮）；进程重启后实例丢失，
 * 对应会话将不可继续（仅保留回放）。
 * </p>
 * <ul>
 *   <li>LRU 淘汰：同时保留最多 {@value #MAX_ACTIVE} 个，超出按最久未使用移除；</li>
 *   <li>运行互斥：同一会话同一时刻只允许一轮续跑（tryBeginRun/endRun 配对）；</li>
 *   <li>已取消（abort）的会话不可继续对话。</li>
 * </ul>
 *
 * @author Sorghum
 */
@Slf4j
@Component
public class SubAgentSessionManager {

    /** 同时保留的最大活跃子代理数（超出按最久未使用淘汰） */
    private static final int MAX_ACTIVE = 20;

    /** accessOrder=true：get 会更新访问顺序，淘汰最久未使用项 */
    private final Map<String, SubAgent> active = new LinkedHashMap<>(16, 0.75f, true);
    /** 正在执行（续跑中）的会话标识，防止同一会话并发续跑 */
    private final Set<String> running = new HashSet<>();

    /** 登记一个子代理实例（执行开始时调用；重复登记覆盖旧实例）。 */
    public synchronized void register(String subSessionId, SubAgent sub) {
        if (sub == null || subSessionId == null || subSessionId.isBlank()) return;
        active.put(subSessionId, sub);
        evictIfNeeded();
    }

    /** 查找子代理实例（可能为 null）。 */
    public synchronized SubAgent find(String subSessionId) {
        return subSessionId == null ? null : active.get(subSessionId);
    }

    /** 当前保留的活跃子代理数（含运行中的）。 */
    public synchronized int size() {
        return active.size();
    }

    /** 是否可继续对话：会话在册且未被取消。 */
    public synchronized boolean isResumable(String subSessionId) {
        SubAgent sub = find(subSessionId);
        return sub != null && !sub.isAbortRequested();
    }

    /**
     * 标记开始一轮续跑。返回 false 表示会话不可续跑或正在执行中。
     * 成功后必须配对调用 {@link #endRun}。
     */
    public synchronized boolean tryBeginRun(String subSessionId) {
        if (!isResumable(subSessionId)) return false;
        return running.add(subSessionId);
    }

    /** 一轮续跑结束（成功或异常都必须调用）。 */
    public synchronized void endRun(String subSessionId) {
        running.remove(subSessionId);
    }

    /** 会话是否正在执行中（续跑进行时不可删除/继续）。 */
    public synchronized boolean isRunning(String subSessionId) {
        return subSessionId != null && running.contains(subSessionId);
    }

    /** 移除会话登记（删除会话后调用，防止残留引用）。 */
    public synchronized void remove(String subSessionId) {
        if (subSessionId == null) return;
        active.remove(subSessionId);
        running.remove(subSessionId);
    }

    private void evictIfNeeded() {
        while (active.size() > MAX_ACTIVE) {
            Iterator<Map.Entry<String, SubAgent>> it = active.entrySet().iterator();
            if (!it.hasNext()) break;
            it.next();
            it.remove();
        }
    }
}
