package site.sorghum.loopra.bin.builtin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import site.sorghum.loopra.bin.agent.model.ImageToolResult;
import site.sorghum.loopra.bin.model.LoopraModelProvider;
import site.sorghum.loopra.bin.model.ModalitySupport;
import site.sorghum.loopra.bin.model.ModelModalityProvider;
import site.sorghum.loopra.tool.AgentLoopController;
import site.sorghum.loopra.tool.AgentOutput;
import site.sorghum.loopra.tool.ToolContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ImageReadToolTest {
    private static final byte[] PNG_HEADER = {
            (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'
    };

    @TempDir
    Path workspace;

    @AfterEach
    void resetDependencies() {
        ImageReadTool.modalityProvider = null;
        ToolContext.clearCurrentController();
    }

    @Test
    void readsWorkspaceImageAsVisualContext() throws Exception {
        Files.write(workspace.resolve("diagram.png"), PNG_HEADER);
        ImageReadTool tool = new ImageReadTool();

        String result = tool.readImage("diagram.png", "high",
                new ToolContext(Map.of(), workspace.toString(), "session-1"));
        ImageToolResult.ImageResult image = ImageReadTool.parseResult(result);

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
        ImageToolResult.ImageResult image = ImageReadTool.parseResult(result);

        assertNotNull(image);
        assertEquals("low", image.detail());
        assertTrue(image.dataUri().startsWith("data:image/png;base64,"));
    }

    @Test
    void createsVisualResultForBrowserSnapshot() {
        String result = ImageReadTool.imageResult("{\"snapshotId\":\"tab-1-s1\"}",
                "data:image/png;base64," + Base64.getEncoder().encodeToString(PNG_HEADER), "auto");
        ImageToolResult.ImageResult image = ImageReadTool.parseResult(result);

        assertNotNull(image);
        assertEquals("auto", image.detail());
        assertEquals("{\"snapshotId\":\"tab-1-s1\"}", image.summary());
    }

    @Test
    void rejectsPathsOutsideWorkspace() throws Exception {
        Path outside = workspace.getParent().resolve("outside.png");
        Files.write(outside, PNG_HEADER);
        ImageReadTool tool = new ImageReadTool();

        String result = tool.readImage("../outside.png", "auto",
                new ToolContext(Map.of(), workspace.toString(), "session-1"));

        assertEquals("PATH_DENIED: 项目相对路径必须位于当前项目内", result);
    }

    @Test
    void blocksImageReadWhenModelLacksImageInput() throws Exception {
        Files.write(workspace.resolve("diagram.png"), PNG_HEADER);
        ImageReadTool tool = new ImageReadTool();
        ImageReadTool.modalityProvider = new ModelModalityProvider() {
            @Override
            public ModalitySupport _getModalitySupport(String modelName) {
                return ModalitySupport.TEXT_ONLY;
            }
        };
        ToolContext.setCurrentController(controllerWith(new LoopraModelProvider(
                "https://api.example.test/v1/chat/completions", "key", "text-model", "high", "text-channel")));

        String result = tool.readImage("diagram.png", "high",
                new ToolContext(Map.of(), workspace.toString(), "session-1"));

        assertTrue(result.startsWith("MODEL_NOT_SUPPORTED: "), result);
        assertTrue(result.contains("text-model"), result);
    }

    @Test
    void allowsImageReadWhenModelSupportsImageInput() throws Exception {
        Files.write(workspace.resolve("diagram.png"), PNG_HEADER);
        ImageReadTool tool = new ImageReadTool();
        ImageReadTool.modalityProvider = new ModelModalityProvider() {
            @Override
            public ModalitySupport _getModalitySupport(String modelName) {
                return new ModalitySupport(true, false, false, false, false, false, false, true, true);
            }
        };
        ToolContext.setCurrentController(controllerWith(new LoopraModelProvider(
                "https://api.example.test/v1/chat/completions", "key", "vision-model", "high", "vision-channel")));

        String result = tool.readImage("diagram.png", "high",
                new ToolContext(Map.of(), workspace.toString(), "session-1"));

        assertNotNull(ImageReadTool.parseResult(result));
    }

    private static AgentLoopController controllerWith(LoopraModelProvider modelProvider) {
        return new AgentLoopController() {
            @Override
            public AgentOutput getOutput() {
                return null;
            }

            @Override
            public void requestStop() {
            }

            @Override
            public void injectUserMessage(String message) {
            }

            @Override
            public <T> T getToolRegistry() {
                return null;
            }

            @Override
            public LoopraModelProvider getModelProvider() {
                return modelProvider;
            }
        };
    }
}
