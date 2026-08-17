package site.sorghum.loopra.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.noear.snack4.Feature;
import org.noear.snack4.ONode;
import org.noear.snack4.codec.BeanDecoder;
import org.noear.solon.ai.chat.tool.FunctionTool;
import site.sorghum.loopra.bin.builtin.MemoryTool;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolContextSnack4Test {

    @TempDir
    Path tempDir;

    @Test
    void beanRoundTripPreservesRuntimeFields() {
        ToolContext source = new ToolContext(
            Map.of("action", "search"),
            Path.of("C:/work/project").toString(),
            Path.of("C:/work/project/.loopra").toString(),
            "session-1"
        );

        ToolContext decoded = BeanDecoder.decode(ONode.ofBean(source), ToolContext.class);

        assertEquals(Path.of("C:/work/project"), decoded.getRootDir());
        assertEquals(Path.of("C:/work/project/.loopra"), decoded.getStateRootDir());
        assertEquals("session-1", decoded.getSessionId());
        assertEquals("search", decoded.getString("action"));
    }

    @Test
    void decodesToolContextFromMcpBodyLikeMethodExecuteHandler() {
        ToolContext source = new ToolContext(
            Map.of("action", "search"),
            Path.of("C:/work/project").toString(),
            Path.of("C:/work/project/.loopra").toString(),
            "session-1"
        );
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("action", "search");
        args.put("__cwd", Path.of("C:/work/project").toString());
        args.put("ctx", source);

        ONode body = ONode.ofBean(args, Feature.Read_AutoRepair);
        ONode ctxNode = body.get("ctx");

        assertNotNull(ctxNode);
        ToolContext decoded = BeanDecoder.decode(ctxNode, ToolContext.class);

        assertEquals(Path.of("C:/work/project"), decoded.getRootDir());
        assertEquals(Path.of("C:/work/project/.loopra"), decoded.getStateRootDir());
        assertEquals("session-1", decoded.getSessionId());
        assertEquals("search", decoded.getString("action"));
    }

    @Test
    void methodFunctionToolPassesFullToolContextToTool() throws Throwable {
        FunctionTool memory = new MemoryTool().getSolonTools().iterator().next();
        ToolContext source = new ToolContext(
            Map.of(),
            tempDir.toString(),
            tempDir.toString(),
            "session-1"
        );
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("action", "search");
        args.put("__cwd", tempDir.toString());
        args.put("ctx", source);

        String content = memory.call(args).getContent();

        assertNotNull(content);
        assertFalse(content.contains("WORKSPACE_MISSING"));
    }

}
