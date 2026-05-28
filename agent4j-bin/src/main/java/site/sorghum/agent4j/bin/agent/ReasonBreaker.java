package site.sorghum.agent4j.bin.agent;

import java.util.HashMap;
import java.util.Map;

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
 * {@value #MIN_REPEATS} 次及以上时，判定为循环。
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

    /** 滑动窗口大小（字符数） */
    static final int WINDOW_SIZE = 200;

    /** 同一窗口出现多少次视为循环 */
    static final int MIN_REPEATS = 3;

    /** 推理内容至少多长才开始检测 */
    static final int MIN_REASONING_LENGTH = 1000;

    /** 最多分析多长的推理内容（性能上限，超出取尾部） */
    static final int MAX_ANALYZE_LENGTH = 20_000;

    /** 每回合最多触发次数（防止 ReasonBreaker 自身形成无限循环） */
    static final int MAX_TRIGGERS_PER_TURN = 3;

    /** 本回合已触发次数 */
    private int triggerCount = 0;

    /**
     * 分析推理内容是否包含循环重复。
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
        if (triggerCount >= MAX_TRIGGERS_PER_TURN) {
            return LoopResult.NO_LOOP;
        }

        // 截取尾部（循环通常出现在最近的输出中）
        String text = reasoningContent;
        if (text.length() > MAX_ANALYZE_LENGTH) {
            text = text.substring(text.length() - MAX_ANALYZE_LENGTH);
        }

        // 滑动窗口统计
        Map<String, Integer> counts = new HashMap<>();
        int maxCount = 0;
        String maxWindow = null;
        int end = text.length() - WINDOW_SIZE;

        for (int i = 0; i <= end; i++) {
            String window = text.substring(i, i + WINDOW_SIZE);
            // 跳过全 uniform 窗口（如 "AAAA..."），避免单一块内重叠计数
            if (isUniform(window)) {
                continue;
            }
            int count = counts.merge(window, 1, Integer::sum);
            if (count > maxCount) {
                maxCount = count;
                maxWindow = window;
                // 提前退出：一旦达到阈值即可判定
                if (maxCount >= MIN_REPEATS) {
                    break;
                }
            }
        }

        if (maxCount >= MIN_REPEATS) {
            triggerCount++;
            // 截取摘要（取重复片段的前 80 字符）
            String snippet = maxWindow.length() > 80 ? maxWindow.substring(0, 80) + "..." : maxWindow;
            return new LoopResult(true, snippet, maxCount);
        }

        return LoopResult.NO_LOOP;
    }

    /** 每回合开始时重置触发计数 */
    public void reset() {
        triggerCount = 0;
    }

    /** 检测窗口是否全由同一字符构成（如 "AAAA..."），此类窗口跳过不计数 */
    private static boolean isUniform(String s) {
        char first = s.charAt(0);
        for (int i = 1; i < s.length(); i++) {
            if (s.charAt(i) != first) return false;
        }
        return true;
    }

    /**
     * 检测结果。
     */
    public static class LoopResult {
        /** 无循环的单例 */
        public static final LoopResult NO_LOOP = new LoopResult(false, null, 0);

        /** 是否检测到循环 */
        public final boolean looping;
        /** 重复片段的摘要（最多 80 字符） */
        public final String snippet;
        /** 重复次数 */
        public final int count;

        LoopResult(boolean looping, String snippet, int count) {
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
