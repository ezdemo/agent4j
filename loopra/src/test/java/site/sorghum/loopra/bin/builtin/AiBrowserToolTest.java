package site.sorghum.loopra.bin.builtin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import site.sorghum.loopra.bin.agent.model.ImageToolResult;
import site.sorghum.loopra.bin.model.LoopraModelProvider;
import site.sorghum.loopra.bin.model.ModalitySupport;
import site.sorghum.loopra.bin.model.ModelModalityProvider;
import site.sorghum.loopra.bin.tool.ToolRegistry;
import site.sorghum.loopra.tool.AgentLoopController;
import site.sorghum.loopra.tool.AgentOutput;
import site.sorghum.loopra.tool.ToolContext;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AiBrowserToolTest {
    private static final String SCREENSHOT_RESPONSE = """
            {"success":true,"data":{"snapshotId":"tab-1-s1","elements":[],\
            "imageUrl":"data:image/png;base64,AAAA","imageDetail":"auto"}}
            """;

    @AfterEach
    void resetDependencies() {
        ImageReadTool.modalityProvider = null;
        ToolContext.clearCurrentController();
    }

    @Test
    void attachesScreenshotWhenModelSupportsImageInput() {
        ImageReadTool.modalityProvider = providerWith(new ModalitySupport(true, false, false, false, false, false, false, true, true));
        ToolContext.setCurrentController(controllerWith(new LoopraModelProvider(
                "https://api.example.test/v1/chat/completions", "key", "vision-model", "high", "vision-channel")));

        String result = AiBrowserTool.postProcessScreenshot(SCREENSHOT_RESPONSE,
                new ToolContext(Map.of(), ".", "session-1"));

        ImageToolResult.ImageResult image = ImageReadTool.parseResult(result);
        assertNotNull(image);
        assertEquals("auto", image.detail());
        assertEquals("data:image/png;base64,AAAA", image.dataUri());
        assertTrue(image.summary().contains("tab-1-s1"));
        assertFalse(image.summary().contains("imageUrl"));
    }

    @Test
    void stripsScreenshotWhenModelLacksImageInput() {
        ImageReadTool.modalityProvider = providerWith(ModalitySupport.TEXT_ONLY);
        ToolContext.setCurrentController(controllerWith(new LoopraModelProvider(
                "https://api.example.test/v1/chat/completions", "key", "text-model", "high", "text-channel")));

        String result = AiBrowserTool.postProcessScreenshot(SCREENSHOT_RESPONSE,
                new ToolContext(Map.of(), ".", "session-1"));

        assertNull(ImageReadTool.parseResult(result));
        assertTrue(result.contains("tab-1-s1"), result);
        assertTrue(result.contains("imageOmitted"), result);
        assertFalse(result.contains("imageUrl"), result);
        assertFalse(result.contains("data:image"), result);
    }

    @Test
    void keepsScreenshotWhenImageSupportCannotBeDetermined() {
        // 无控制器/无提供者（单元测试、CLI 等场景）时保持兼容，仍附带截图
        String result = AiBrowserTool.postProcessScreenshot(SCREENSHOT_RESPONSE,
                new ToolContext(Map.of(), ".", "session-1"));

        assertNotNull(ImageReadTool.parseResult(result));
    }

    @Test
    void passesThroughBrowserUnavailableResult() {
        String unavailable = "BROWSER_UNAVAILABLE: Start Loopra Desktop before using browser tools.";

        String result = AiBrowserTool.postProcessScreenshot(unavailable,
                new ToolContext(Map.of(), ".", "session-1"));

        assertEquals(unavailable, result);
    }

    private static ModelModalityProvider providerWith(ModalitySupport support) {
        return modelName -> support;
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
            public ToolRegistry getToolRegistry() {
                return null;
            }

            @Override
            public LoopraModelProvider getModelProvider() {
                return modelProvider;
            }
        };
    }
}
