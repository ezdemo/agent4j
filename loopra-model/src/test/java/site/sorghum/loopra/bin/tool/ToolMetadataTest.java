package site.sorghum.loopra.bin.tool;

import org.junit.jupiter.api.Test;
import org.noear.solon.ai.chat.tool.FunctionToolDesc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolMetadataTest {

    @Test
    void recognizesBuiltInReadOnlyTools() {
        assertTrue(ToolMetadata.isReadOnly(tool("read")));
        assertTrue(ToolMetadata.isReadOnly(tool("workspace_write")));
        assertTrue(ToolMetadata.isReadOnly(tool("finish")));
        assertFalse(ToolMetadata.isReadOnly(tool("edit")));
        assertFalse(ToolMetadata.isReadOnly(tool("bash")));
    }

    @Test
    void explicitMetadataClassifiesExtensionTools() {
        FunctionToolDesc readOnly = tool("custom_query");
        readOnly.metaPut("readOnly", true);
        FunctionToolDesc write = tool("custom_write");
        write.metaPut("readOnly", false);

        assertTrue(ToolMetadata.isReadOnly(readOnly));
        assertFalse(ToolMetadata.isReadOnly(write));
    }

    @Test
    void runtimeOverrideTakesPrecedenceAndCanBeRemoved() {
        FunctionToolDesc builtInRead = tool("read");
        ToolMetadata.applyReadOnlyOverride(builtInRead, false);
        assertFalse(ToolMetadata.isReadOnly(builtInRead));
        assertFalse(ToolMetadata.readOnlyOverride(builtInRead));

        ToolMetadata.applyReadOnlyOverride(builtInRead, null);
        assertTrue(ToolMetadata.isReadOnly(builtInRead));

        FunctionToolDesc declaredWrite = tool("custom_write");
        declaredWrite.metaPut("readOnly", false);
        ToolMetadata.applyReadOnlyOverride(declaredWrite, true);
        assertTrue(ToolMetadata.isReadOnly(declaredWrite));
        ToolMetadata.applyReadOnlyOverride(declaredWrite, null);
        assertFalse(ToolMetadata.isReadOnly(declaredWrite));
    }

    private static FunctionToolDesc tool(String name) {
        return new FunctionToolDesc(name)
                .description(name)
                .inputSchema("{\"type\":\"object\"}")
                .returnType(String.class)
                .doHandle(args -> "ok");
    }
}
