package site.sorghum.loopra.bin.agent.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;
import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.ai.chat.tool.FunctionToolDesc;
import site.sorghum.loopra.bin.agent.context.ContextTokenEstimate;
import site.sorghum.loopra.bin.agent.context.ConversationContext;
import site.sorghum.loopra.bin.agent.model.LoopraChatMessage;
import site.sorghum.loopra.bin.agent.prompt.PromptPrefix;
import site.sorghum.loopra.bin.model.ModelClient;
import site.sorghum.loopra.bin.tool.ToolRegistry;
import site.sorghum.loopra.bin.tool.ToolScanProvider;
import site.sorghum.loopra.bin.tool.ToolScanUtil;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentLoopPromptFreezeTest {

    /**
     * refresh() 依赖 ToolScanUtil 扫描恢复工具；单元测试未安装 provider 时为 EMPTY（扫描结果为空）。
     * 需要刷新路径的用例先安装 fake provider 模拟生产行为（harness 的 SolonToolScanProvider），
     * 结束后恢复，避免静态状态泄漏到其他测试类。
     */
    @AfterEach
    void restoreScanProvider() {
        ToolScanUtil.install(null);
    }

    private static void installFakeScanProvider(List<FunctionTool> tools) {
        ToolScanUtil.install(new ToolScanProvider() {
            @Override
            public List<FunctionTool> scanTools(Path workspace) {
                return tools;
            }

            @Override
            public String getSkillToolDescription(Path workspace) {
                return "";
            }
        });
    }

    @Test
    void frozenPrefixIgnoresLaterInstructionAndToolChanges() {
        ToolRegistry registry = new ToolRegistry().setDisabledTools(Set.of());
        registry.register(tool("read"));
        ConversationContext context = new ConversationContext(
                new PromptPrefix("system", registry.toOpenAiTools()));
        AgentLoop loop = new AgentLoop(new NoOpModelClient(), registry, context);

        loop.freezePromptPrefix();
        ContextTokenEstimate before = loop.estimateCurrentContext();

        loop.setTerminateOnNoToolCall(false);
        registry.register(tool("edit"));
        ContextTokenEstimate after = loop.estimateCurrentContext();

        assertEquals(before.systemTokens(), after.systemTokens());
        assertEquals(before.toolDefinitionTokens(), after.toolDefinitionTokens());
        assertEquals(1, loop.refreshTools().size());
    }

    @Test
    void refreshFunctionToolsFrozenIgnoresLaterToolChanges() {
        ToolRegistry registry = new ToolRegistry().setDisabledTools(Set.of());
        registry.register(tool("read"));
        ConversationContext context = new ConversationContext(
                new PromptPrefix("system", registry.toOpenAiTools()));
        AgentLoop loop = new AgentLoop(new NoOpModelClient(), registry, context);

        loop.freezePromptPrefix();
        assertEquals(1, loop.refreshFunctionTools().size());
        assertEquals("read", loop.refreshFunctionTools().get(0).name());

        registry.register(tool("edit"));
        assertEquals(1, loop.refreshFunctionTools().size());
        assertEquals("read", loop.refreshFunctionTools().get(0).name());
    }

    @Test
    void refreshFunctionToolsPlanModeFiltersToReadOnly() {
        // read 命中内置只读名单，edit 非只读
        installFakeScanProvider(List.of(tool("read"), tool("edit")));
        ToolRegistry registry = new ToolRegistry().setDisabledTools(Set.of());
        ConversationContext context = new ConversationContext(
                new PromptPrefix("system", registry.toOpenAiTools()));
        AgentLoop loop = new AgentLoop(new NoOpModelClient(), registry, context);

        loop.setPlanMode(true);
        List<FunctionTool> tools = loop.refreshFunctionTools();
        assertEquals(1, tools.size());
        assertEquals("read", tools.get(0).name());
    }

    @Test
    void refreshFunctionToolsNormalModeReturnsAllEnabled() {
        installFakeScanProvider(List.of(tool("read"), tool("edit")));
        ToolRegistry registry = new ToolRegistry().setDisabledTools(Set.of());
        ConversationContext context = new ConversationContext(
                new PromptPrefix("system", registry.toOpenAiTools()));
        AgentLoop loop = new AgentLoop(new NoOpModelClient(), registry, context);

        List<FunctionTool> tools = loop.refreshFunctionTools();
        assertEquals(2, tools.size());
    }

    private static FunctionToolDesc tool(String name) {
        return new FunctionToolDesc(name)
                .description(name)
                .inputSchema("{\"type\":\"object\"}")
                .returnType(String.class)
                .doHandle(args -> "ok");
    }

    private static final class NoOpModelClient implements ModelClient {
        @Override
        public ONode chat(List<LoopraChatMessage> messages, ONode tools) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void chatStream(List<LoopraChatMessage> messages, ONode tools, StreamCallback callback) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getModel() {
            return "test-model";
        }

        @Override
        public void setModel(String model) {
        }
    }
}
