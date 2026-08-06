package site.sorghum.loopra.bin.agent.bridge;

import org.junit.jupiter.api.Test;
import org.noear.solon.ai.chat.ChatRole;
import org.noear.solon.ai.chat.content.ImageBlock;
import org.noear.solon.ai.chat.content.TextBlock;
import org.noear.solon.ai.chat.message.AssistantMessage;
import org.noear.solon.ai.chat.message.ChatMessage;
import org.noear.solon.ai.chat.message.SystemMessage;
import org.noear.solon.ai.chat.message.ToolMessage;
import org.noear.solon.ai.chat.message.UserMessage;
import org.noear.solon.ai.chat.prompt.Prompt;
import org.noear.solon.ai.chat.tool.ToolCall;
import site.sorghum.loopra.bin.agent.model.LoopraChatMessage;
import site.sorghum.loopra.bin.agent.model.ToolCallEntry;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LoopraChatMessageConverterTest {

    @Test
    void convert_system() {
        ChatMessage msg = LoopraChatMessageConverter.convert(LoopraChatMessage.ofSystem("你是助手"));
        assertInstanceOf(SystemMessage.class, msg);
        assertEquals(ChatRole.SYSTEM, msg.getRole());
        assertEquals("你是助手", msg.getContent());
    }

    @Test
    void convert_user_text() {
        ChatMessage msg = LoopraChatMessageConverter.convert(LoopraChatMessage.ofUser("你好"));
        assertInstanceOf(UserMessage.class, msg);
        assertEquals(ChatRole.USER, msg.getRole());
        assertEquals("你好", msg.getContent());
        assertFalse(((UserMessage) msg).isMultiModal());
    }

    @Test
    void convert_user_multimodal() {
        LoopraChatMessage src = LoopraChatMessage.ofUser("看这张图", List.of("https://example.com/a.png"));
        UserMessage msg = (UserMessage) LoopraChatMessageConverter.convert(src);
        assertTrue(msg.isMultiModal());
        // ofUser(text, images) 的内容段顺序：图片在前、文本在后
        assertEquals(2, msg.getBlocks().size());
        ImageBlock image = (ImageBlock) msg.getBlocks().get(0);
        assertEquals("https://example.com/a.png", image.getUrl());
        assertInstanceOf(TextBlock.class, msg.getBlocks().get(1));
    }

    @Test
    void convert_assistant_plain() {
        ChatMessage msg = LoopraChatMessageConverter.convert(
                LoopraChatMessage.assistant("答案", null, null));
        AssistantMessage am = (AssistantMessage) msg;
        assertEquals(ChatRole.ASSISTANT, am.getRole());
        assertEquals("答案", am.getContent());
        assertFalse(am.isThinking());
        assertFalse(am.isToolCalls());
    }

    @Test
    void convert_assistant_with_reasoning() {
        LoopraChatMessage src = new LoopraChatMessage("assistant");
        src.setContent("最终回答");
        src.setReasoningContent("推理过程");
        AssistantMessage am = (AssistantMessage) LoopraChatMessageConverter.convert(src);

        assertEquals("推理过程", am.getReasoning());
        assertEquals("最终回答", am.getResultContent());
        assertEquals("reasoning_content", am.getReasoningFieldName());
        assertFalse(am.isThinking());
    }

    @Test
    void convert_assistant_reasoning_only() {
        LoopraChatMessage src = new LoopraChatMessage("assistant");
        src.setReasoningContent("纯推理");
        AssistantMessage am = (AssistantMessage) LoopraChatMessageConverter.convert(src);

        assertTrue(am.isThinking());
        assertEquals("纯推理", am.getReasoning());
        assertEquals("reasoning_content", am.getReasoningFieldName());
    }

    @Test
    void convert_assistant_with_tool_calls() {
        ToolCallEntry entry = new ToolCallEntry("call_1", "read_file",
                Map.of("path", "a.txt"), null);
        LoopraChatMessage src = LoopraChatMessage.assistant(null,
                List.of(entry), null);
        AssistantMessage am = (AssistantMessage) LoopraChatMessageConverter.convert(src);

        assertTrue(am.isToolCalls());
        List<ToolCall> calls = am.getToolCalls();
        assertEquals(1, calls.size());
        assertEquals("call_1", calls.get(0).getId());
        assertEquals("read_file", calls.get(0).getName());
        assertEquals(Map.of("path", "a.txt"), calls.get(0).getArguments());

        // OpenAI 兼容 raw（solon-ai 方言回传 tool_calls 仅认 toolCallsRaw）
        List<Map> raws = am.getToolCallsRaw();
        assertEquals(1, raws.size());
        Map<?, ?> fn = (Map<?, ?>) raws.get(0).get("function");
        assertEquals("read_file", fn.get("name"));
        assertEquals("{\"path\":\"a.txt\"}", fn.get("arguments"));
        assertEquals("call_1", raws.get(0).get("id"));
    }

    @Test
    void convert_assistant_with_response_reasoning() {
        LoopraChatMessage src = new LoopraChatMessage("assistant");
        src.setContent("回答");
        src.setResponseReasoning("{\"type\":\"reasoning\",\"summary\":[]}");
        AssistantMessage am = (AssistantMessage) LoopraChatMessageConverter.convert(src);

        assertEquals("{\"type\":\"reasoning\",\"summary\":[]}", am.getResponseReasoning());
        assertEquals("回答", am.getContent());
    }

    @Test
    void convert_tool_text() {
        LoopraChatMessage src = LoopraChatMessage.tool("call_1", "{\"ok\":true}");
        ToolMessage msg = (ToolMessage) LoopraChatMessageConverter.convert(src);
        assertEquals(ChatRole.TOOL, msg.getRole());
        assertEquals("{\"ok\":true}", msg.getContent());
        assertEquals("call_1", msg.getToolCallId());
        assertNull(msg.getName());
        assertFalse(msg.isMultiModal());
    }

    @Test
    void convert_tool_with_image() {
        LoopraChatMessage src = LoopraChatMessage.toolWithImage(
                "call_1", "截图", "https://example.com/s.png", "high");
        ToolMessage msg = (ToolMessage) LoopraChatMessageConverter.convert(src);
        assertTrue(msg.isMultiModal());
        assertEquals(2, msg.getBlocks().size());
        ImageBlock image = (ImageBlock) msg.getBlocks().get(1);
        assertEquals("https://example.com/s.png", image.getUrl());
        assertEquals("high", image.metas().get("detail"));
    }

    @Test
    void convertAll_skips_null() {
        List<ChatMessage> result = LoopraChatMessageConverter.convertAll(
                Arrays.asList(LoopraChatMessage.ofUser("a"), null, LoopraChatMessage.ofSystem("b")));
        assertEquals(2, result.size());
    }

    @Test
    void convert_unknown_role_throws() {
        LoopraChatMessage src = new LoopraChatMessage("developer");
        assertThrows(IllegalArgumentException.class, () -> LoopraChatMessageConverter.convert(src));
    }

    @Test
    void convertToPrompt_buildsPromptFromMessages() {
        List<LoopraChatMessage> src = Arrays.asList(
                LoopraChatMessage.ofSystem("你是助手"),
                LoopraChatMessage.ofUser("你好"));

        Prompt prompt = LoopraChatMessageConverter.convertToPrompt(src);

        assertEquals(2, prompt.size());
        assertEquals(ChatRole.SYSTEM, prompt.getFirstMessage().getRole());
        assertEquals(ChatRole.USER, prompt.getLastMessage().getRole());
        assertEquals("你是助手", prompt.getSystemContent());
        assertEquals("你好", prompt.getUserContent());
    }
}
