package site.sorghum.agent4j.bin.tool;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {

    private ToolDef createTool(String name, boolean readOnly, boolean stormExempt) {
        return new ToolDef(name, "desc " + name, new ArrayList<ToolDef.ParamDef>(),
                args -> "ok", readOnly, stormExempt, null);
    }

    @Test
    void registerAndGet() {
        ToolRegistry reg = new ToolRegistry();
        ToolDef tool = createTool("my_tool", false, false);
        reg.register(tool);
        assertTrue(reg.has("my_tool"));
        assertEquals(tool, reg.get("my_tool"));
    }

    @Test
    void getUnknownReturnsNull() {
        ToolRegistry reg = new ToolRegistry();
        assertNull(reg.get("nonexistent"));
        assertFalse(reg.has("nonexistent"));
    }

    @Test
    void disabledToolNotRegistered() {
        ToolRegistry reg = new ToolRegistry();
        Set<String> disabled = new HashSet<String>();
        disabled.add("disabled_tool");
        reg.setDisabledTools(disabled);
        reg.register(createTool("disabled_tool", false, false));
        assertFalse(reg.has("disabled_tool"));
    }

    @Test
    void enabledToolStillRegisteredWhenOthersDisabled() {
        ToolRegistry reg = new ToolRegistry();
        Set<String> disabled = new HashSet<String>();
        disabled.add("disabled_tool");
        reg.setDisabledTools(disabled);
        reg.register(createTool("enabled_tool", false, false));
        assertTrue(reg.has("enabled_tool"));
    }

    @Test
    void allReturnsUnmodifiable() {
        ToolRegistry reg = new ToolRegistry();
        reg.register(createTool("t1", false, false));
        Map<String, ToolDef> all = reg.all();
        assertThrows(UnsupportedOperationException.class, () -> {
            all.put("new", createTool("new", false, false));
        });
    }

    @Test
    void toOpenAiToolsSortedByName() {
        ToolRegistry reg = new ToolRegistry();
        reg.register(createTool("zebra", false, false));
        reg.register(createTool("alpha", false, false));
        reg.register(createTool("middle", false, false));
        List<Map<String, Object>> tools = reg.toOpenAiTools();
        assertEquals(3, tools.size());
        assertEquals("alpha", ((Map<?, ?>) tools.get(0).get("function")).get("name"));
        assertEquals("middle", ((Map<?, ?>) tools.get(1).get("function")).get("name"));
        assertEquals("zebra", ((Map<?, ?>) tools.get(2).get("function")).get("name"));
    }

    @Test
    void toOpenAiToolsContainsCorrectStructure() {
        ToolRegistry reg = new ToolRegistry();
        reg.register(createTool("test_tool", true, true));
        List<Map<String, Object>> tools = reg.toOpenAiTools();
        assertEquals(1, tools.size());
        Map<?, ?> entry = tools.get(0);
        assertEquals("function", entry.get("type"));
        Map<?, ?> func = (Map<?, ?>) entry.get("function");
        assertEquals("test_tool", func.get("name"));
        assertNotNull(func.get("parameters"));
    }
}
