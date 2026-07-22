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

    private static FunctionToolDesc tool(String name) {
        return new FunctionToolDesc(name)
                .description(name)
                .inputSchema("{\"type\":\"object\"}")
                .returnType(String.class)
                .doHandle(args -> "ok");
    }
}
