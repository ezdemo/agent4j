package site.sorghum.agent4j.bin.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class ToolDispatcherTest {

    private ToolRegistry registry;
    private ToolDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        // 注册一个普通写入工具
        registry.register(new ToolDef("edit_file", "edit", new ArrayList<>(),
                args -> "edited:" + args.get("path"), false, false, null));
        // 注册一个只读工具
        registry.register(new ToolDef("read_file", "read", new ArrayList<>(),
                args -> "content_of:" + args.get("path"), true, true, null));
        // 注册一个 storm 豁免工具
        registry.register(new ToolDef("glob", "glob", new ArrayList<>(),
                args -> "match", true, true, null));
        dispatcher = new ToolDispatcher(registry);
    }

    @Test
    void dispatchKnownTool() {
        String result = dispatcher.dispatch("read_file", "{\"path\":\"a.java\"}");
        assertEquals("content_of:a.java", result);
    }

    @Test
    void dispatchUnknownToolReturnsError() {
        String result = dispatcher.dispatch("nonexistent", "{}");
        assertTrue(result.contains("error"));
        assertTrue(result.contains("unknown tool"));
    }

    @Test
    void planModeBlocksWriteTool() {
        dispatcher.setPlanMode(true);
        String result = dispatcher.dispatch("edit_file", "{\"path\":\"a.java\"}");
        assertTrue(result.contains("rejectedReason"));
        assertTrue(result.contains("plan-mode"));
    }

    @Test
    void planModeAllowsReadOnlyTool() {
        dispatcher.setPlanMode(true);
        String result = dispatcher.dispatch("read_file", "{\"path\":\"a.java\"}");
        assertFalse(result.contains("rejectedReason"), "只读工具在计划模式下应可用");
        assertEquals("content_of:a.java", result);
    }

    @Test
    void stormBreakerTriggers() {
        String args = "{\"path\":\"a.java\"}";
        dispatcher.dispatch("edit_file", args);
        dispatcher.dispatch("edit_file", args);
        dispatcher.dispatch("edit_file", args);
        String result = dispatcher.dispatch("edit_file", args);
        assertTrue(result.contains("rejectedReason"));
        assertTrue(result.contains("storm"));
    }

    @Test
    void stormExemptToolNotBlocked() {
        String args = "{\"pattern\":\"*.java\"}";
        for (int i = 0; i < 10; i++) {
            String result = dispatcher.dispatch("glob", args);
            assertFalse(result.contains("rejectedReason"), "storm 豁免工具不应被阻断");
        }
    }

    @Test
    void invalidArgumentsJsonReturnsError() {
        String result = dispatcher.dispatch("read_file", "not json");
        assertTrue(result.contains("error"));
        assertTrue(result.contains("invalid arguments"));
    }

    @Test
    void preDispatchHookCanIntercept() {
        dispatcher.setPreDispatchHook(name -> {
            if ("edit_file".equals(name)) return "intercepted!";
            return null;
        });
        String result = dispatcher.dispatch("edit_file", "{\"path\":\"a.java\"}");
        assertEquals("intercepted!", result);
    }

    @Test
    void postDispatchHookTransformsResult() {
        dispatcher.setPostDispatchHook((name, result) -> "HOOKED:" + result);
        String result = dispatcher.dispatch("read_file", "{\"path\":\"a.java\"}");
        assertEquals("HOOKED:content_of:a.java", result);
    }

    @Test
    void resetStormClearsBreaker() {
        String args = "{\"path\":\"a.java\"}";
        dispatcher.dispatch("edit_file", args);
        dispatcher.dispatch("edit_file", args);
        dispatcher.dispatch("edit_file", args);
        dispatcher.resetStorm();
        String result = dispatcher.dispatch("edit_file", args);
        assertFalse(result.contains("rejectedReason"), "reset 后不应被阻断");
    }
}
