package site.sorghum.agent4j.bin.agent;

import java.util.*;

/**
 * 风暴断路器 —— 滑动窗口检测重复工具调用。
 * <p>
 * 当模型在循环中反复调用同一工具且参数完全相同时，
 * 抑制后续调用并返回提示，避免死循环耗尽 token 预算。
 * </p>
 *
 * @author Sorghum
 */
public class StormBreaker {

    private static final int WINDOW_SIZE = 6;
    private static final int THRESHOLD = 3;

    static class Entry {
        final String name;
        final String argsFingerprint;
        final boolean readOnly;

        Entry(String name, String argsFingerprint, boolean readOnly) {
            this.name = name;
            this.argsFingerprint = argsFingerprint;
            this.readOnly = readOnly;
        }
    }

    private final LinkedList<Entry> recent = new LinkedList<>();

    /** 每回合重置（线程安全） */
    public synchronized void reset() {
        recent.clear();
    }

    /**
     * 检查是否应抑制此调用。
     * @param name          工具名
     * @param argumentsJson 参数 JSON 字符串
     * @param readOnly      工具是否为只读
     * @return 抑制信息
     */
    public synchronized SuppressResult inspect(String name, String argumentsJson, boolean readOnly) {
        String fp = fingerprint(name, argumentsJson);
        int count = 0;
        for (Entry e : recent) {
            if (e.name.equals(name) && e.argsFingerprint.equals(fp)) count++;
        }
        if (count >= THRESHOLD - 1) {
            return new SuppressResult(true,
                    "storm-guard: " + name + " called with identical args " + (count + 1)
                            + " times — suppressed. DO NOT retry with identical args. "
                            + "If the intent is still valid, call with different arguments or pick a different tool.");
        }

        if (!readOnly) {
            recent.removeIf(e -> e.readOnly);
        }

        recent.addLast(new Entry(name, fp, readOnly));
        while (recent.size() > WINDOW_SIZE) {
            recent.removeFirst();
        }
        return new SuppressResult(false, null);
    }

    private static String fingerprint(String name, String args) {
        try {
            // 用 ONode 解析后再序列化，消除键顺序/空格差异
            org.noear.snack4.ONode node = org.noear.snack4.ONode.ofJson(args);
            return name + "|" + node.toJson();
        } catch (Exception e) {
            return name + "|" + args;
        }
    }

    public static class SuppressResult {
        public final boolean suppressed;
        public final String reason;

        SuppressResult(boolean suppressed, String reason) {
            this.suppressed = suppressed;
            this.reason = reason;
        }
    }
}
