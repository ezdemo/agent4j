package site.sorghum.loopra.integration.cutin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;
import site.sorghum.cutin.core.context.Message;
import site.sorghum.cutin.core.model.ModelCallRequest;
import site.sorghum.loopra.bin.agent.context.ConversationContext;
import site.sorghum.loopra.bin.agent.core.AgentLoop;
import site.sorghum.loopra.bin.agent.model.UserMessage;
import site.sorghum.loopra.bin.agent.prompt.PromptPrefix;
import site.sorghum.loopra.bin.model.ModelModalityProvider;
import site.sorghum.loopra.bin.model.ModalitySupport;
import site.sorghum.loopra.bin.model.TestLoopraProvider;
import site.sorghum.loopra.bin.model.UserMessageSanitizer;
import site.sorghum.loopra.bin.tool.ToolRegistry;
import site.sorghum.loopra.tool.AgentOutput;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 端到端回归：用户发送"文字+图片"消息，走完整 cutin 主循环后，
 * 模型请求里必须同时保留文本与图片 —— 修复前 compaction 用
 * toCutin(ChatMessage) 整体覆盖上下文时会把两者全部清空，
 * 导致上游 400 input must be non-empty。
 */
class CutinMultimodalFlowTest {

    private static final ModalitySupport VISION_SUPPORT = new ModalitySupport(
            true, false, false, false, false, false, false, true, true);

    @AfterEach
    void resetSanitizerModalityProvider() {
        // 测试用的静态注入替身，用完还原，避免影响其他测试
        UserMessageSanitizer.modalityProvider = null;
    }

    @Test
    void multimodalUserMessageReachesModelRequestWithTextAndImages() throws Exception {
        // 模拟"当前模型支持图片输入"：sanitize 保留图片
        UserMessageSanitizer.modalityProvider = modelName -> VISION_SUPPORT;

        AtomicReference<ModelCallRequest> seen = new AtomicReference<>();
        TestLoopraProvider client = TestLoopraProvider.builder()
                .model("vision-model")
                .stream(request -> {
                    seen.set(request);
                    return TestLoopraProvider.contentStream("ok");
                })
                .build();

        ConversationContext context = new ConversationContext(
                new PromptPrefix("system", new ONode().asArray()));
        AgentLoop loop = new AgentLoop(
                client, new ToolRegistry().setDisabledTools(Set.of()), context, null);
        loop.freezePromptPrefix();
        loop.setOutput(AgentOutput.NOOP);

        String result = loop.run(UserMessage.of(
                "看看这张图", List.of("https://example.com/a.png")));

        assertEquals("ok", result);
        assertNotNull(seen.get(), "BEFORE_MODEL 后模型请求应已发出");
        Message userMsg = seen.get().messages().stream()
                .filter(m -> "user".equals(m.role()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("模型请求中缺少 user 消息"));
        assertEquals("看看这张图", userMsg.content(), "用户文本在请求中必须保留");
        assertEquals(List.of("https://example.com/a.png"), userMsg.metadata("images"),
                "用户图片在请求中必须保留");
    }
}