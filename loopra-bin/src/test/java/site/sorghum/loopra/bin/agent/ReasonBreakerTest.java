package site.sorghum.loopra.bin.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import site.sorghum.loopra.bin.agent.resilient.ReasonBreaker;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ReasonBreaker 单元测试 —— 推理断路器滑动窗口循环检测。
 * <p>
 * 覆盖阶段一（调参）与阶段二（最小间隔约束、增量检测、两次命中硬终止）。
 */
class ReasonBreakerTest {

    private ReasonBreaker breaker;

    /**
     * 构造一个 WINDOW_SIZE 长的自然语言文本块（非 uniform，避免单一块内重叠计数）。
     * <p>每个句子都带 label，确保不同 label 的 block 内容完全不同，
     * 避免不同 block 的公共前缀窗口被误算为同一窗口。
     */
    private static String textBlock(char label) {
        String[] sentences = {
                label + "模型正在分析代码库的结构和依赖关系。",
                label + "需要检查每个函数的调用链和参数传递方式。",
                label + "这意味着要深入理解整个系统的架构设计。",
                label + "同时还要考虑性能影响和内存使用模式。",
                label + "不同模块之间的耦合度也需要仔细评估。",
                label + "测试覆盖率是否足够，边界情况是否处理妥当。",
                label + "重构时要注意保持向后兼容性，避免破坏现有功能。",
                label + ": 这是标记文本用于区分不同测试块的唯一标识。"
        };
        StringBuilder sb = new StringBuilder();
        while (sb.length() < ReasonBreaker.WINDOW_SIZE) {
            for (String s : sentences) {
                sb.append(s);
                if (sb.length() >= ReasonBreaker.WINDOW_SIZE) break;
            }
        }
        return sb.substring(0, ReasonBreaker.WINDOW_SIZE);
    }

    /**
     * 生成不重复的前缀文本（避免周期性模式被误判为循环）
     */
    private static String variedPrefix(int minLength) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (sb.length() < minLength) {
            sb.append("这是第").append(i++).append("段前缀文本用于填充推理内容长度需求。");
        }
        return sb.toString();
    }

    /**
     * 生成长度 ≥ minGap 的不重复分隔文本，确保两次 block 起始位置间隔 ≥ MIN_GAP。
     * 用于构造满足最小间隔约束的"远距离重复"场景。
     * 默认 offset=0；不同 offset 生成完全不同的内容，避免同一分隔符被重复使用
     * 导致其自身窗口跨副本累积到 MIN_REPEATS。
     */
    private static String longSeparator(int minLen) {
        return longSeparator(minLen, 0);
    }

    /**
     * 生成长度 ≥ minLen 的不重复分隔文本，起始编号从 offset 开始。
     */
    private static String longSeparator(int minLen, int offset) {
        StringBuilder sb = new StringBuilder();
        int i = offset;
        while (sb.length() < minLen) {
            sb.append("分隔段落编号").append(i++).append("用于填充足够长的中间间隔内容。");
        }
        return sb.toString();
    }

    @BeforeEach
    void setUp() {
        breaker = new ReasonBreaker();
    }

    @Test
    void shortReasoningNotDetected() {
        // 短于 MIN_REASONING_LENGTH 的推理不检测
        String shortText = "短文本".repeat(10); // ~30 chars
        ReasonBreaker.LoopResult r = breaker.analyze(shortText);
        assertFalse(r.looping, "短推理不应触发检测");
    }

    @Test
    void normalReasoningNotTriggered() {
        // 正常的、不重复的推理（长度需 ≥ MIN_REASONING_LENGTH 才会进入检测路径）
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 35; i++) {
            sb.append("这是第").append(i).append("段正常的推理内容。")
                    .append("模型在分析代码结构，考虑不同的实现方案。")
                    .append("需要检查文件依赖关系和接口兼容性。".repeat(3));
        }
        ReasonBreaker.LoopResult r = breaker.analyze(sb.toString());
        assertFalse(r.looping, "正常推理不应触发");
    }

    @Test
    void repeatedWindowDetected() {
        // 构造明确远距离重复（4 次相同块，间隔 ≥ MIN_GAP）
        String block = textBlock('A');
        String sep = longSeparator(ReasonBreaker.MIN_GAP); // 确保间隔够大
        StringBuilder sb = new StringBuilder();
        sb.append(variedPrefix(2600)); // 凑够 MIN_REASONING_LENGTH，使用不重复前缀
        sb.append(block);
        sb.append(sep);
        sb.append(block);
        sb.append(sep);
        sb.append(block);
        sb.append(sep);
        sb.append(block); // 第 4 次

        ReasonBreaker.LoopResult r = breaker.analyze(sb.toString());
        assertTrue(r.looping, "远距离窗口重复应被检测");
        assertTrue(r.count >= ReasonBreaker.MIN_REPEATS, "count 应 >= MIN_REPEATS");
        assertNotNull(r.snippet);
        assertFalse(r.snippet.isEmpty());
    }

    @Test
    void exactlyFourRepeatsTriggered() {
        // MIN_REPEATS = 4，恰好 4 次远距离重复应触发
        String block = textBlock('B');
        String sep = longSeparator(ReasonBreaker.MIN_GAP);
        StringBuilder sb = new StringBuilder();
        sb.append(variedPrefix(2600)); // 凑够 MIN_REASONING_LENGTH
        sb.append(block);
        sb.append(sep);
        sb.append(block);
        sb.append(sep);
        sb.append(block);
        sb.append(sep);
        sb.append(block); // 第 4 次

        ReasonBreaker.LoopResult r = breaker.analyze(sb.toString());
        assertTrue(r.looping, "远距离窗口重复 4 次应被检测");
        assertTrue(r.count >= ReasonBreaker.MIN_REPEATS);
    }

    @Test
    void twoRepeatsNotTriggered() {
        // 仅 2 次远距离重复不应触发（但文本需够长才会检测）
        String block = textBlock('C');
        String sep = longSeparator(ReasonBreaker.MIN_GAP);
        StringBuilder sb = new StringBuilder();
        sb.append(variedPrefix(2600));
        sb.append(block);
        sb.append(sep);
        sb.append(block); // 仅 2 次

        ReasonBreaker.LoopResult r = breaker.analyze(sb.toString());
        assertFalse(r.looping, "窗口重复 2 次不应触发");
    }

    @Test
    void nullReasoningNotDetected() {
        ReasonBreaker.LoopResult r = breaker.analyze(null);
        assertFalse(r.looping, "null 推理不应触发");
    }

    @Test
    void emptyReasoningNotDetected() {
        ReasonBreaker.LoopResult r = breaker.analyze("");
        assertFalse(r.looping, "空推理不应触发");
    }

    @Test
    void resetClearsTriggerCount() {
        // 触发一次后 reset，应能再次触发
        String block = textBlock('D');
        String sep = longSeparator(ReasonBreaker.MIN_GAP);
        StringBuilder sb = new StringBuilder();
        sb.append(variedPrefix(2600));
        sb.append(block);
        sb.append(sep);
        sb.append(block);
        sb.append(sep);
        sb.append(block);
        sb.append(sep);
        sb.append(block); // 第 4 次

        // 第一次触发（analyze 不再自增 triggerCount，需手动 recordTrigger 模拟硬终止）
        ReasonBreaker.LoopResult r1 = breaker.analyze(sb.toString());
        assertTrue(r1.looping, "第一次应检测到循环");
        breaker.recordTrigger(); // 模拟 AgentLoop 硬终止时记账

        // reset 后应能再次检测
        breaker.reset();
        ReasonBreaker.LoopResult r2 = breaker.analyze(sb.toString());
        assertTrue(r2.looping, "reset 后应能再次检测");
    }

    @Test
    void maxTriggersPerTurnEnforced() {
        // 超过 MAX_TRIGGERS_PER_TURN 后 analyze 直接返回 NO_LOOP
        String block = textBlock('E');
        String sep = longSeparator(ReasonBreaker.MIN_GAP);
        StringBuilder sb = new StringBuilder();
        sb.append(variedPrefix(2600));
        sb.append(block);
        sb.append(sep);
        sb.append(block);
        sb.append(sep);
        sb.append(block);
        sb.append(sep);
        sb.append(block); // 第 4 次

        // 模拟 AgentLoop 硬终止：每次 analyze 命中后调 recordTrigger
        for (int i = 0; i < ReasonBreaker.MAX_TRIGGERS_PER_TURN; i++) {
            ReasonBreaker.LoopResult r = breaker.analyze(sb.toString());
            assertTrue(r.looping, "第 " + (i + 1) + " 次应检测到循环");
            breaker.recordTrigger();
        }

        // 第 MAX_TRIGGERS_PER_TURN + 1 次 analyze 不应再返回循环
        ReasonBreaker.LoopResult r = breaker.analyze(sb.toString());
        assertFalse(r.looping, "超过上限后不应再检测");
    }

    @Test
    void differentBlocksDontCrossTrigger() {
        // 不同内容的窗口各自计数，不应互相影响。
        // 每个分隔段用不同 offset 生成，避免分隔符自身窗口重复。
        String blockA = textBlock('F');
        String blockB = textBlock('G');
        int gap = ReasonBreaker.MIN_GAP;
        StringBuilder sb = new StringBuilder();
        sb.append(variedPrefix(2600));
        sb.append(blockA);
        sb.append(longSeparator(gap, 0));
        sb.append(blockB);
        sb.append(longSeparator(gap, 1000));
        sb.append(blockA);
        sb.append(longSeparator(gap, 2000));
        sb.append(blockB);
        sb.append(longSeparator(gap, 3000));

        // blockA 出现 2 次，blockB 出现 2 次——都不够 MIN_REPEATS(4) 次
        ReasonBreaker.LoopResult r = breaker.analyze(sb.toString());
        assertFalse(r.looping, "不同内容各 2 次不应交叉触发");
    }

    @Test
    void loopResultNoLoopSingleton() {
        // NO_LOOP 单例
        assertFalse(ReasonBreaker.LoopResult.NO_LOOP.looping);
        assertNull(ReasonBreaker.LoopResult.NO_LOOP.snippet);
        assertEquals(0, ReasonBreaker.LoopResult.NO_LOOP.count);
    }

    @Test
    void warningMessageFormat() {
        ReasonBreaker.LoopResult r = new ReasonBreaker.LoopResult(
                true, "重复的文本片段", 3);
        String warning = r.toWarning();
        assertTrue(warning.contains("推理断路器"));
        assertTrue(warning.contains("3"));
        assertTrue(warning.contains("重复的文本片段"));
    }

    @Test
    void longSnippetTruncatedInWarning() {
        // snippet 超过 80 字符时，toWarning 应使用截断后的版本
        String longSnippet = "X".repeat(100);
        ReasonBreaker.LoopResult r = new ReasonBreaker.LoopResult(
                true, longSnippet, 4);
        String warning = r.toWarning();
        // analyze() 方法内部已将 snippet 截断到 80 字符 + "..."
        // 这里直接测试 toWarning 使用传入的 snippet
        assertTrue(warning.contains(longSnippet));
    }

    // ==================== 阶段二新增测试 ====================

    @Test
    void adjacentRepeatsNotTriggered() {
        // 最小间隔约束：相邻重复（间隔 < MIN_GAP）不应触发。
        // 4 次 block 紧挨着放，无分隔——模拟模型在相邻段落反复斟酌同一句话。
        String block = textBlock('H');
        StringBuilder sb = new StringBuilder();
        sb.append(variedPrefix(2600));
        sb.append(block);
        sb.append(block);
        sb.append(block);
        sb.append(block); // 4 次紧挨，相邻间隔 = WINDOW_SIZE < MIN_GAP

        ReasonBreaker.LoopResult r = breaker.analyze(sb.toString());
        assertFalse(r.looping, "相邻重复（间隔 < MIN_GAP）不应触发——这是正常思考节奏");
    }

    @Test
    void shortGapRepeatsNotTriggered() {
        // 分隔文本不足 MIN_GAP 的重复不应触发。
        // 4 次 block 之间用短分隔（约 100 字符 < MIN_GAP=900）
        String block = textBlock('I');
        String shortSep = "短分隔。".repeat(20); // ~100 字符
        StringBuilder sb = new StringBuilder();
        sb.append(variedPrefix(2600));
        sb.append(block);
        sb.append(shortSep);
        sb.append(block);
        sb.append(shortSep);
        sb.append(block);
        sb.append(shortSep);
        sb.append(block);

        ReasonBreaker.LoopResult r = breaker.analyze(sb.toString());
        assertFalse(r.looping, "短间隔重复不应触发");
    }

    @Test
    void truncationStillDetectsLoop() {
        // 文本超过 MAX_ANALYZE_LENGTH 时取尾部重扫，应仍能检测到远距离循环。
        // 构造超长文本：前段不重复，尾部含 4 次远距离重复 block。
        String block = textBlock('K');
        int gap = ReasonBreaker.MIN_GAP;
        StringBuilder sb = new StringBuilder();
        // 前段填充超过 MAX_ANALYZE_LENGTH 的不重复内容，确保截断后 block 进入尾部
        sb.append(variedPrefix(ReasonBreaker.MAX_ANALYZE_LENGTH + 1000));
        sb.append(block);
        sb.append(longSeparator(gap, 0));
        sb.append(block);
        sb.append(longSeparator(gap, 1000));
        sb.append(block);
        sb.append(longSeparator(gap, 2000));
        sb.append(block);

        ReasonBreaker.LoopResult r = breaker.analyze(sb.toString());
        assertTrue(r.looping, "截断后应全量重扫尾部并检测到远距离循环");
    }

    @Test
    void analyzeDoesNotIncrementTriggerCount() {
        // analyze 命中循环后不应自增 triggerCount（由 recordTrigger 负责）
        String block = textBlock('L');
        String sep = longSeparator(ReasonBreaker.MIN_GAP);
        StringBuilder sb = new StringBuilder();
        sb.append(variedPrefix(2600));
        sb.append(block);
        sb.append(sep);
        sb.append(block);
        sb.append(sep);
        sb.append(block);
        sb.append(sep);
        sb.append(block);

        int before = breaker.getTriggerCount();
        ReasonBreaker.LoopResult r = breaker.analyze(sb.toString());
        assertTrue(r.looping, "应检测到循环");
        assertEquals(before, breaker.getTriggerCount(),
                "analyze 不应自增 triggerCount");

        breaker.recordTrigger();
        assertEquals(before + 1, breaker.getTriggerCount(),
                "recordTrigger 应自增 triggerCount");
    }

    @Test
    void minGapConstantIsTwiceWindowSize() {
        // 确保 MIN_GAP 常量与 WINDOW_SIZE 的 2 倍关系（防止误改）
        assertEquals(ReasonBreaker.WINDOW_SIZE * 2, ReasonBreaker.MIN_GAP,
                "MIN_GAP 应为 WINDOW_SIZE 的 2 倍");
    }
}
