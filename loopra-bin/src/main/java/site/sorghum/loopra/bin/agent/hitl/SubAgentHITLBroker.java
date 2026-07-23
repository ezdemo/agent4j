package site.sorghum.loopra.bin.agent.hitl;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 子代理 HITL 审批代理 —— 全局注册表。
 * <p>
 * 当子代理触发 HITL 时，通过 {@link #register(int)} 注册到此表并阻塞等待审批；
 * 前端审批后通过 REST 端点调用 {@link #resolve(int, boolean)} 释放阻塞。
 * </p>
 *
 * @author Sorghum
 */
public class SubAgentHITLBroker {

    /** 默认审批超时（5 分钟） */
    private static final long DEFAULT_TIMEOUT_MINUTES = 5;

    private static final ConcurrentHashMap<Integer, Pending> map = new ConcurrentHashMap<>();

    /**
     * 待审批条目：CountDownLatch + 审批结果。
     */
    public record Pending(CountDownLatch latch, AtomicBoolean approved) {
    }

    /**
     * 注册一个待审批的子代理。
     *
     * @param subId 子代理唯一标识
     * @return Pending 对象（调用方通过 {@code latch.await()} 阻塞等待）
     */
    public static Pending register(int subId) {
        Pending p = new Pending(new CountDownLatch(1), new AtomicBoolean(false));
        map.put(subId, p);
        return p;
    }

    /**
     * 注册并等待审批（带超时）。超时后自动视为拒绝。
     *
     * @param subId          子代理唯一标识
     * @param timeoutMinutes 超时分钟数
     * @return true=审批通过, false=拒绝或超时
     */
    public static boolean registerAndAwait(int subId, int timeoutMinutes) {
        Pending p = register(subId);
        try {
            boolean released = p.latch.await(timeoutMinutes, TimeUnit.MINUTES);
            if (!released) {
                // 超时：自动拒绝
                p.approved.set(false);
            }
            return p.approved.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            remove(subId);
        }
    }

    /**
     * 注册并等待审批（使用默认 5 分钟超时）。
     */
    public static boolean registerAndAwait(int subId) {
        return registerAndAwait(subId, (int) DEFAULT_TIMEOUT_MINUTES);
    }

    /**
     * 解析审批结果（由 REST 端点调用）。
     *
     * @param subId    子代理唯一标识
     * @param approved true=批准, false=拒绝
     */
    public static void resolve(int subId, boolean approved) {
        Pending p = map.get(subId);
        if (p != null) {
            p.approved.set(approved);
            p.latch.countDown();
        }
    }

    /**
     * 清理（子代理恢复后调用，也可在超时后清理）。
     */
    public static void remove(int subId) {
        map.remove(subId);
    }
}
