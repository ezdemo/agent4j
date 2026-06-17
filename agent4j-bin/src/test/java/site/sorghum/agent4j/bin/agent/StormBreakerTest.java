package site.sorghum.agent4j.bin.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import site.sorghum.agent4j.bin.agent.breaker.StormBreaker;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StormBreaker 单元测试 —— 风暴断路器滑动窗口检测。
 */
class StormBreakerTest {

    private StormBreaker breaker;

    @BeforeEach
    void setUp() {
        breaker = new StormBreaker();
    }

    @Test
    void firstCallNotSuppressed() {
        StormBreaker.SuppressResult r = breaker.inspect("edit_file", "{\"path\":\"a.java\",\"search\":\"x\",\"replace\":\"y\"}", false);
        assertFalse(r.suppressed, "首次调用不应被抑制");
    }

    @Test
    void sameCallThreeTimesSuppressed() {
        // THRESHOLD = 3，count >= THRESHOLD - 1 即 count >= 2 时抑制
        // 3次调用：前2次通过，第3次 count=2 触发抑制
        String args = "{\"path\":\"a.java\",\"search\":\"x\",\"replace\":\"y\"}";
        breaker.inspect("edit_file", args, false);
        breaker.inspect("edit_file", args, false);
        StormBreaker.SuppressResult r = breaker.inspect("edit_file", args, false);
        assertTrue(r.suppressed, "3次相同调用应被抑制（count>=2触发）");
        assertNotNull(r.reason);
        assertTrue(r.reason.contains("风暴断路器"));
    }

    @Test
    void twoCallsNotSuppressed() {
        String args = "{\"path\":\"a.java\",\"search\":\"x\",\"replace\":\"y\"}";
        breaker.inspect("edit_file", args, false);
        StormBreaker.SuppressResult r = breaker.inspect("edit_file", args, false);
        assertFalse(r.suppressed, "2次相同调用不应被抑制");
    }

    @Test
    void differentArgsNotSuppressed() {
        breaker.inspect("edit_file", "{\"path\":\"a.java\"}", false);
        breaker.inspect("edit_file", "{\"path\":\"b.java\"}", false);
        breaker.inspect("edit_file", "{\"path\":\"c.java\"}", false);
        StormBreaker.SuppressResult r = breaker.inspect("edit_file", "{\"path\":\"d.java\"}", false);
        assertFalse(r.suppressed, "不同参数不应被抑制");
    }

    @Test
    void readOnlyCallsRemovedByMutating() {
        // 只读调用添加到窗口
        breaker.inspect("read_file", "{\"path\":\"a.java\"}", true);
        breaker.inspect("read_file", "{\"path\":\"b.java\"}", true);
        breaker.inspect("read_file", "{\"path\":\"c.java\"}", true);
        // 变异调用会清除只读条目
        breaker.inspect("edit_file", "{\"path\":\"x.java\"}", false);
        // 此时只读条目应已被清除，不会触发抑制
        StormBreaker.SuppressResult r = breaker.inspect("read_file", "{\"path\":\"a.java\"}", true);
        assertFalse(r.suppressed, "只读条目被清除后不应抑制");
    }

    @Test
    void windowSizeRespected() {
        // 窗口大小为 6，超出的旧条目会被移除
        for (int i = 0; i < 10; i++) {
            breaker.inspect("tool_" + i, "{\"i\":" + i + "}", false);
        }
        // 旧的 tool_0 已被移出窗口，重新调用不应被抑制
        StormBreaker.SuppressResult r = breaker.inspect("tool_0", "{\"i\":0}", false);
        assertFalse(r.suppressed, "超出窗口的旧条目不应抑制新调用");
    }

    @Test
    void resetClearsWindow() {
        String args = "{\"path\":\"a.java\"}";
        breaker.inspect("edit_file", args, false);
        breaker.inspect("edit_file", args, false);
        breaker.inspect("edit_file", args, false);
        breaker.reset();
        StormBreaker.SuppressResult r = breaker.inspect("edit_file", args, false);
        assertFalse(r.suppressed, "reset 后不应抑制");
    }

    @Test
    void differentToolNamesNotSuppressed() {
        String args = "{\"path\":\"a.java\"}";
        breaker.inspect("edit_file", args, false);
        breaker.inspect("edit_file", args, false);
        breaker.inspect("edit_file", args, false);
        StormBreaker.SuppressResult r = breaker.inspect("read_file", args, true);
        assertFalse(r.suppressed, "不同工具名不应被抑制");
    }

    @Test
    void identicalJsonArgsHaveSameFingerprint() {
        // 完全相同的 JSON 应被抑制
        String args = "{\"path\":\"a.java\",\"search\":\"x\"}";
        breaker.inspect("edit_file", args, false);
        breaker.inspect("edit_file", args, false);
        StormBreaker.SuppressResult r = breaker.inspect("edit_file", args, false);
        assertTrue(r.suppressed, "完全相同的 JSON 应被抑制");
    }

    @Test
    void differentArgsJsonNotSuppressed() {
        // 参数值不同不应抑制
        breaker.inspect("edit_file", "{\"path\":\"a.java\"}", false);
        breaker.inspect("edit_file", "{\"path\":\"a.java\"}", false);
        StormBreaker.SuppressResult r = breaker.inspect("edit_file", "{\"path\":\"b.java\"}", false);
        assertFalse(r.suppressed, "不同参数值不应被抑制");
    }
}
