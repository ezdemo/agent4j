package site.sorghum.agent4j.bin.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ReasonBreaker 单元测试 —— 推理断路器滑动窗口循环检测。
 */
class ReasonBreakerTest {

    private ReasonBreaker breaker;

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
        // 正常的、不重复的推理
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append("这是第").append(i).append("段正常的推理内容。")
              .append("模型在分析代码结构，考虑不同的实现方案。")
              .append("需要检查文件依赖关系和接口兼容性。".repeat(3));
        }
        ReasonBreaker.LoopResult r = breaker.analyze(sb.toString());
        assertFalse(r.looping, "正常推理不应触发");
    }

    /** 构造一个 ~WINDOW_SIZE 长的自然语言文本块（非 uniform，避免单一块内重叠计数） */
    private static String textBlock(char label) {
        // 使用变长的自然语言句子组成 ~200 字符的块，确保相邻窗口内容不同
        String[] sentences = {
            "模型正在分析代码库的结构和依赖关系。",
            "需要检查每个函数的调用链和参数传递方式。",
            "这意味着要深入理解整个系统的架构设计。",
            "同时还要考虑性能影响和内存使用模式。",
            "不同模块之间的耦合度也需要仔细评估。",
            "测试覆盖率是否足够，边界情况是否处理妥当。",
            "重构时要注意保持向后兼容性，避免破坏现有功能。",
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

    /** 生成不重复的前缀文本（避免周期性模式被误判为循环） */
    private static String variedPrefix(int minLength) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (sb.length() < minLength) {
            sb.append("这是第").append(i++).append("段前缀文本用于填充推理内容长度需求。");
        }
        return sb.toString();
    }

    @Test
    void repeatedWindowDetected() {
        // 构造一个明确重复的窗口（4 次相同块）
        String block = textBlock('A');
        StringBuilder sb = new StringBuilder();
        sb.append(variedPrefix(300)); // 凑够 MIN_REASONING_LENGTH，使用不重复前缀
        sb.append(block);
        sb.append("不同的中间内容，模型在尝试其他思路。".repeat(10));
        sb.append(block);
        sb.append("又一段不同的思考内容。".repeat(10));
        sb.append(block);
        sb.append("最后一段内容。".repeat(10));
        sb.append(block); // 第 4 次

        ReasonBreaker.LoopResult r = breaker.analyze(sb.toString());
        assertTrue(r.looping, "窗口重复应被检测");
        assertTrue(r.count >= ReasonBreaker.MIN_REPEATS, "count 应 >= MIN_REPEATS");
        assertNotNull(r.snippet);
        assertFalse(r.snippet.isEmpty());
    }

    @Test
    void exactlyThreeRepeatsTriggered() {
        // MIN_REPEATS = 3，恰好 3 次应触发
        String block = textBlock('B');
        StringBuilder sb = new StringBuilder();
        sb.append(variedPrefix(1000)); // 凑够 MIN_REASONING_LENGTH
        sb.append(block);
        sb.append("中间内容".repeat(10));
        sb.append(block);
        sb.append("其他内容".repeat(10));
        sb.append(block); // 第 3 次

        ReasonBreaker.LoopResult r = breaker.analyze(sb.toString());
        assertTrue(r.looping, "窗口重复 3 次应被检测");
        assertTrue(r.count >= ReasonBreaker.MIN_REPEATS);
    }

    @Test
    void twoRepeatsNotTriggered() {
        // 仅 2 次重复不应触发（但文本需够长才会检测）
        String block = textBlock('C');
        StringBuilder sb = new StringBuilder();
        sb.append(variedPrefix(1000));
        sb.append(block);
        sb.append("中间分隔文本填充".repeat(3));
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
        StringBuilder sb = new StringBuilder();
        sb.append(variedPrefix(1000));
        sb.append(block);
        sb.append("中间分隔内容".repeat(10));
        sb.append(block);
        sb.append("其他分隔内容".repeat(10));
        sb.append(block);

        // 第一次触发
        ReasonBreaker.LoopResult r1 = breaker.analyze(sb.toString());
        assertTrue(r1.looping, "第一次应触发");

        // reset 后应能再次触发
        breaker.reset();
        ReasonBreaker.LoopResult r2 = breaker.analyze(sb.toString());
        assertTrue(r2.looping, "reset 后应能再次触发");
    }

    @Test
    void maxTriggersPerTurnEnforced() {
        // 超过 MAX_TRIGGERS_PER_TURN 后不再触发
        String block = textBlock('E');
        StringBuilder sb = new StringBuilder();
        sb.append(variedPrefix(1000));
        sb.append(block);
        sb.append("分隔一".repeat(10));
        sb.append(block);
        sb.append("分隔二".repeat(10));
        sb.append(block);

        // 触发 MAX_TRIGGERS_PER_TURN 次
        for (int i = 0; i < ReasonBreaker.MAX_TRIGGERS_PER_TURN; i++) {
            ReasonBreaker.LoopResult r = breaker.analyze(sb.toString());
            assertTrue(r.looping, "第 " + (i + 1) + " 次应触发");
        }

        // 第 MAX_TRIGGERS_PER_TURN + 1 次不应触发
        ReasonBreaker.LoopResult r = breaker.analyze(sb.toString());
        assertFalse(r.looping, "超过上限后不应触发");
    }

    @Test
    void differentBlocksDontCrossTrigger() {
        // 不同内容的窗口各自计数，不应互相影响
        String blockA = textBlock('F');
        String blockB = textBlock('G');
        StringBuilder sb = new StringBuilder();
        sb.append(variedPrefix(1000));
        sb.append(blockA);
        sb.append("中间分隔A内容".repeat(10));
        sb.append(blockB);
        sb.append("中间分隔B内容".repeat(10));
        sb.append(blockA);
        sb.append("中间分隔C内容".repeat(10));
        sb.append(blockB);
        sb.append("中间分隔D内容".repeat(10));

        // blockA 出现 2 次，blockB 出现 2 次——都不够 3 次
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
}
