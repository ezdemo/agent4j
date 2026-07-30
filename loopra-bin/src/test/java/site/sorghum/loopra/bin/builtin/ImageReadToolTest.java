package site.sorghum.loopra.bin.builtin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import site.sorghum.loopra.tool.ToolContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageReadToolTest {
    private static final byte[] PNG_HEADER = {
            (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'
    };

    @TempDir
    Path workspace;

    @Test
    void readsWorkspaceImageAsVisualContext() throws Exception {
        Files.write(workspace.resolve("diagram.png"), PNG_HEADER);
        ImageReadTool tool = new ImageReadTool();

        String result = tool.readImage("diagram.png", "high",
                new ToolContext(Map.of(), workspace.toString(), "session-1"));
        ImageReadTool.ImageResult image = ImageReadTool.parseResult(result);

        assertNotNull(image);
        assertEquals("high", image.detail());
        assertTrue(image.summary().contains("diagram.png"));
        assertTrue(image.dataUri().startsWith("data:image/png;base64,"));
    }

    @Test
    void readsAbsoluteImagePath() throws Exception {
        Path imagePath = workspace.getParent().resolve("absolute.png");
        Files.write(imagePath, PNG_HEADER);
        ImageReadTool tool = new ImageReadTool();

        String result = tool.readImage(imagePath.toString(), "auto",
                new ToolContext(Map.of(), workspace.toString(), "session-1"));

        assertNotNull(ImageReadTool.parseResult(result));
    }

    @Test
    void decodesBase64DataUri() {
        ImageReadTool tool = new ImageReadTool();
        String dataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(PNG_HEADER);

        String result = tool.readImage(null, dataUri, null, "low", null);
        ImageReadTool.ImageResult image = ImageReadTool.parseResult(result);

        assertNotNull(image);
        assertEquals("low", image.detail());
        assertTrue(image.dataUri().startsWith("data:image/png;base64,"));
    }

    @Test
    void rejectsPathsOutsideWorkspace() throws Exception {
        Path outside = workspace.getParent().resolve("outside.png");
        Files.write(outside, PNG_HEADER);
        ImageReadTool tool = new ImageReadTool();

        String result = tool.readImage("../outside.png", "auto",
                new ToolContext(Map.of(), workspace.toString(), "session-1"));

        assertEquals("PATH_DENIED: 工作区相对路径必须位于当前工作区内", result);
    }
}
