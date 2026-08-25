package site.sorghum.loopra.bin.agent.core;

import org.junit.jupiter.api.Test;
import site.sorghum.cutin.core.context.DefaultLoopContext;
import site.sorghum.cutin.core.context.Message;
import site.sorghum.cutin.core.loop.DefaultLoopEngine;
import site.sorghum.cutin.core.model.ModelCallRequest;
import site.sorghum.cutin.core.model.ModelRegistry;
import site.sorghum.cutin.core.model.StreamChunk;
import site.sorghum.cutin.core.plugin.DefaultLoopRegistrar;
import site.sorghum.loopra.bin.agent.context.ConversationContext;
import site.sorghum.loopra.bin.agent.model.UserMessage;
import site.sorghum.loopra.bin.agent.prompt.PromptPrefix;
import site.sorghum.loopra.bin.model.TestLoopraProvider;
import site.sorghum.loopra.bin.tool.ToolRegistry;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 回归测试：AgentLoop.setModel 热更新后，cutin 模型注册表与网关必须能解析新模型。
 * <p>修复前注册表在构造时按模型 id 快照索引，setModel 后索引过期，
 * 模型调用会抛 "no provider for model"（见 AgentLoop#doModelStep 的调用链）。</p>
 */
class AgentLoopModelSwitchTest {

    @Test
    void setModelRefreshesCutinRegistryAndGateway() throws Exception {
        ToolRegistry registry = new ToolRegistry().setDisabledTools(Set.of());
        ConversationContext context = new ConversationContext(
                new PromptPrefix("system", registry.toOpenAiTools()));
        // 与主循环前置节点一致：用户消息必须先进入 Loopra 上下文，否则
        // BEFORE_MODEL 的空输入守卫（GOTO output）会拦截本次调用
        context.addUser(UserMessage.of("hi"));
        AtomicReference<String> streamedModel = new AtomicReference<>();
        TestLoopraProvider provider = TestLoopraProvider.builder()
                .model("model-a")
                .stream(request -> {
                    streamedModel.set(request.modelId());
                    return TestLoopraProvider.contentStream("ok");
                })
                .build();
        AgentLoop loop = new AgentLoop(provider, registry, context, null);

        ModelRegistry models = cutinModels(loop);
        assertNotNull(models.resolve("model-a"), "构造时应能解析注册时的模型");

        loop.setModel("model-b");

        // 注册表层面：新模型可解析，旧模型不可再命中
        assertNotNull(models.resolve("model-b"), "setModel 后注册表必须能解析新模型");
        assertThrows(IllegalArgumentException.class, () -> models.resolve("model-a"));

        // 网关层面：流式调用新模型必须路由到 provider（修复前此处抛 no provider for model）
        DefaultLoopEngine engine = cutinEngine(loop);
        DefaultLoopContext cutinContext = engine.newContext("test", Map.of());
        // 携带一条真实用户消息，避免命中 BEFORE_MODEL 的空输入守卫（GOTO output）
        ModelCallRequest request = new ModelCallRequest(
                "model-b", List.of(new Message("user", "hi")), List.of(), Map.of());
        try (Stream<StreamChunk> chunks = cutinContext.models().stream(request, cutinContext)) {
            chunks.forEach(chunk -> { });
        }
        assertEquals("model-b", streamedModel.get(), "请求应路由到热更新后的模型");
    }

    private static ModelRegistry cutinModels(AgentLoop loop) throws Exception {
        return ((DefaultLoopRegistrar) cutinEngine(loop).registrar()).models();
    }

    private static DefaultLoopEngine cutinEngine(AgentLoop loop) throws Exception {
        Field engineField = AgentLoop.class.getDeclaredField("cutinEngine");
        engineField.setAccessible(true);
        return (DefaultLoopEngine) engineField.get(loop);
    }
}
