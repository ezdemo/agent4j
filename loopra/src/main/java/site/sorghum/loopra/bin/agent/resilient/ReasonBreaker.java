package site.sorghum.loopra.bin.agent.resilient;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 推理断路器 —— 检测模型思考（reasoning_content）中的循环重复。
 * <p>
 * 当模型在推理过程中反复输出相同段落时，说明模型陷入了思维死循环。
 * ReasonBreaker 通过滑动窗口检测重复文本片段，一旦发现立即介入，
 * 注入警告消息引导模型跳出循环，避免耗尽 token 预算。
 * </p>
 *
 * <h3>检测算法</h3>
 * <p>
 * 在 reasoning 文本上滑动一个固定大小的窗口（{@value #WINDOW_SIZE} 字符），
 * 统计每个窗口内容的出现次数。当任一窗口在文本中出现
 * {@value #MIN_REPEATS} 次及以上，且相邻两次重复位置间隔不小于
 * {@value #MIN_GAP} 字符时，判定为循环。
 * </p>
 *
 * <h3>最小间隔约束</h3>
 * <p>
 * 两次重复窗口的起始位置至少相距 {@value #MIN_GAP} 字符才计为有效重复。
 * 这过滤了模型在相邻段落中反复斟酌同一句话的正常思考节奏——
 * 真正的死循环是远处再次出现完全相同的内容，不是相邻重复。
 * </p>
 *
 * <h3>性能</h3>
 * <p>
 * 仅分析尾部最多 {@value #MAX_ANALYZE_LENGTH} 字符；
 * reasoning 短于 {@value #MIN_REASONING_LENGTH} 字符时直接跳过。
 * 每回合最多触发 {@value #MAX_TRIGGERS_PER_TURN} 次，防止自身形成无限循环。
 * </p>
 *
 * @author Sorghum
 */
public class ReasonBreaker {

    /**
     * 滑动窗口大小（字符数）。
     * <p>调大到 450：原 200 字符仅相当于一两句话，模型在正常推理中反复使用
     * 相同引导语（"让我再分析一下"、"接下来需要检查" 等）极易误触发。
     * 真正的死循环通常是数百字符的整段重复，450 字符能覆盖整段思考块。
     */
    public static final int WINDOW_SIZE = 450;

    /**
     * 同一窗口出现多少次视为循环。
     * <p>调大到 4：原 3 次阈值过于敏感，正常思考中反复斟酌同一句话的节奏
     * 也会被算成 3 次重复。4 次能更好地与正常思考节奏区分。
     */
    public static final int MIN_REPEATS = 4;

    /**
     * 推理内容至少多长才开始检测。
     * <p>调大到 2500：原 1000 字符在深度推理场景下太短，正常长推理前段也
     * 容易触发。2500 字符能确保只在真正进入深度推理后才介入。
     */
    public static final int MIN_REASONING_LENGTH = 2500;

    /**
     * 最多分析多长的推理内容（性能上限，超出取尾部）
     */
    public static final int MAX_ANALYZE_LENGTH = 20_000;

    /**
     * 每回合最多触发次数（防止 ReasonBreaker 自身形成无限循环）。
     * <p>注意：triggerCount 不再由 analyze 自增，改由 AgentLoop 在真正硬终止时
     * 调用 {@link #recordTrigger()} 自增，使软警告不消耗配额。
     */
    public static final int MAX_TRIGGERS_PER_TURN = 3;

    /**
     * 最小间隔约束：两次重复窗口的起始位置至少相距 WINDOW_SIZE*2 字符才计为有效重复。
     * <p>过滤模型在相邻段落中反复斟酌同一句话的正常思考节奏——
     * 真正的死循环是远处再次出现完全相同的内容，不是相邻重复。
     */
    public static final int MIN_GAP = WINDOW_SIZE * 2;

    /**
     * 本回合已触发次数（volatile 保证跨线程可见性）
     */
    private final AtomicInteger triggerCount = new AtomicInteger(0);

    /**
     * Rabin-Karp 滚动哈希基数（素数）
     */
    private static final long BASE = 257L;

    /**
     * BASE^(WINDOW_SIZE-1) mod M，用于滚动哈希移除首字符
     */
    private static final long POW_BASE_WINDOW;

    static {
        long pow = 1;
        for (int i = 0; i < WINDOW_SIZE - 1; i++) {
            pow = (pow * BASE);
        }
        POW_BASE_WINDOW = pow;
    }

    /**
     * 复用的哈希计数器（每次 analyze 清空重扫）
     */
    private final Map<Long, Integer> hashCounts = new HashMap<>();

    /**
     * 记录每个哈希值首次出现的位置（用于 regionMatches 验证）
     */
    private final Map<Long, Integer> hashFirstPos = new HashMap<>();

    /**
     * 记录每个哈希值最近一次被计为有效重复的位置（用于最小间隔约束）
     */
    private final Map<Long, Integer> hashLastPos = new HashMap<>();

    /**
     * 检测窗口是否全由同一字符构成（如 "AAAA..."），此类窗口跳过不计数。
     * 使用下标范围避免 substring 分配。
     */
    private static boolean isUniform(String s, int start, int end) {
        if (start >= end) return true;
        char first = s.charAt(start);
        for (int i = start + 1; i < end; i++) {
            if (s.charAt(i) != first) return false;
        }
        return true;
    }

    /**
     * 分析推理内容是否包含循环重复。
     * <p>使用 Rabin-Karp 滚动哈希避免大量 substring 分配。
     * 每次调用全量扫描尾部文本，清空计数器重新统计。
     *
     * @param reasoningContent 模型的推理/思考文本（可为 null）
     * @return 检测结果，{@link LoopResult#looping} 为 true 表示检测到循环
     */
    public LoopResult analyze(String reasoningContent) {
        // 空或太短 → 不检测
        if (reasoningContent == null || reasoningContent.length() < MIN_REASONING_LENGTH) {
            return LoopResult.NO_LOOP;
        }

        // 达到每回合触发上限 → 不再介入
        if (triggerCount.get() >= MAX_TRIGGERS_PER_TURN) {
            return LoopResult.NO_LOOP;
        }

        // 截取尾部（循环通常出现在最近的输出中）
        String text = reasoningContent;
        if (text.length() > MAX_ANALYZE_LENGTH) {
            text = text.substring(text.length() - MAX_ANALYZE_LENGTH);
        }

        int end = text.length() - WINDOW_SIZE;
        if (end < 0) {
            return LoopResult.NO_LOOP;
        }

        // 全量扫描：清空重扫
        hashCounts.clear();
        hashFirstPos.clear();
        hashLastPos.clear();

        int maxCount = 0;
        int maxCountFirstPos = 0;

        // 计算首个窗口的哈希值
        long hash = 0;
        for (int i = 0; i < WINDOW_SIZE; i++) {
            hash = hash * BASE + text.charAt(i);
        }

        // 检测首个窗口是否全 uniform
        if (!isUniform(text, 0, WINDOW_SIZE)) {
            hashCounts.put(hash, 1);
            hashFirstPos.put(hash, 0);
            hashLastPos.put(hash, 0);
            maxCount = 1;
            maxCountFirstPos = 0;
        }

        // 滚动哈希遍历剩余窗口
        for (int i = 1; i <= end; i++) {
            // 滚动更新哈希：移除首字符，添加尾字符
            char oldChar = text.charAt(i - 1);
            char newChar = text.charAt(i + WINDOW_SIZE - 1);
            hash = (hash - oldChar * POW_BASE_WINDOW) * BASE + newChar;

            // 跳过全 uniform 窗口
            if (isUniform(text, i, i + WINDOW_SIZE)) {
                continue;
            }

            Integer count = hashCounts.get(hash);
            if (count == null) {
                // 首次出现该窗口
                hashCounts.put(hash, 1);
                hashFirstPos.put(hash, i);
                hashLastPos.put(hash, i);
                if (1 > maxCount) {
                    maxCount = 1;
                    maxCountFirstPos = i;
                }
            } else {
                // 哈希碰撞验证：用 regionMatches 确认内容确实相同
                int firstPos = hashFirstPos.get(hash);
                if (!text.regionMatches(firstPos, text, i, WINDOW_SIZE)) {
                    // 哈希碰撞，跳过（不更新 lastPos）
                    continue;
                }
                // 最小间隔约束：与最近一次有效重复位置的距离
                int lastPos = hashLastPos.getOrDefault(hash, firstPos);
                if (i - lastPos < MIN_GAP) {
                    // 间隔不足，不计为有效重复，但更新最近位置（跟踪模型当前位置）
                    hashLastPos.put(hash, i);
                    continue;
                }
                // 有效重复：计数 +1
                int newCount = count + 1;
                hashCounts.put(hash, newCount);
                hashLastPos.put(hash, i);
                if (newCount > maxCount) {
                    maxCount = newCount;
                    maxCountFirstPos = firstPos;
                    if (maxCount >= MIN_REPEATS) {
                        return buildLoopResult(text, maxCountFirstPos, maxCount);
                    }
                }
            }
        }

        if (maxCount >= MIN_REPEATS) {
            return buildLoopResult(text, maxCountFirstPos, maxCount);
        }

        return LoopResult.NO_LOOP;
    }

    /**
     * 构造命中循环的结果（截取摘要），不自增 triggerCount。
     * triggerCount 由 AgentLoop 在硬终止时调用 {@link #recordTrigger()} 自增。
     */
    private LoopResult buildLoopResult(String text, int firstPos, int count) {
        int snippetEnd = Math.min(firstPos + 80, text.length());
        String snippet = text.substring(firstPos, snippetEnd);
        if (snippetEnd - firstPos >= 80) snippet += "...";
        return new LoopResult(true, snippet, count);
    }

    /**
     * 记录一次硬终止触发（由 AgentLoop 在真正 abort 流时调用）。
     * 与 {@link #analyze} 解耦，使软警告不消耗触发配额。
     */
    public void recordTrigger() {
        triggerCount.incrementAndGet();
    }

    /**
     * 每回合开始时重置触发计数和计数器
     */
    public void reset() {
        triggerCount.set(0);
        hashCounts.clear();
        hashFirstPos.clear();
        hashLastPos.clear();
    }

    /**
     * 获取本回合已触发次数
     */
    public int getTriggerCount() {
        return triggerCount.get();
    }

    /**
     * 检测结果。
     */
    public static class LoopResult {
        /**
         * 无循环的单例
         */
        public static final LoopResult NO_LOOP = new LoopResult(false, null, 0);

        /**
         * 是否检测到循环
         */
        public final boolean looping;
        /**
         * 重复片段的摘要（最多 80 字符）
         */
        public final String snippet;
        /**
         * 重复次数
         */
        public final int count;

        public LoopResult(boolean looping, String snippet, int count) {
            this.looping = looping;
            this.snippet = snippet;
            this.count = count;
        }

        /**
         * 生成供模型阅读的警告消息。
         */
        public String toWarning() {
            return "推理断路器: 检测到思考循环——以下内容重复出现了 " + count + " 次: \""
                    + snippet + "\"。请停止当前思路，尝试不同的方法。";
        }
    }
}
