package site.sorghum.cutin.core.loop;

import org.junit.jupiter.api.Test;
import site.sorghum.cutin.core.context.LoopContext;
import site.sorghum.cutin.core.tool.Tool;
import site.sorghum.cutin.core.tool.ToolCall;
import site.sorghum.cutin.core.tool.ToolDefinition;
import site.sorghum.cutin.core.tool.ToolResult;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * 工具策略拦截测试：BEFORE_TOOL 可以拒绝工具或把循环挂起等待审批。
 */
class ToolPolicyInterceptorTest {

    /** 需要审批的工具应挂起循环，并保留挂起原因。 */
    @Test
    void approvalDecisionSuspendsToolCall() throws Exception {
        DefaultLoopEngine engine = new DefaultLoopEngine();
        engine.addTool(new NamedTool("allowed"));
        engine.addTool(new NamedTool("secret"));
        engine.addInterceptor(InterceptPoint.BEFORE_TOOL, 0, context -> {
            if (!(context.payload() instanceof ToolCall call)) {
                return InterceptDecision.pass();
            }
            if ("secret".equals(call.toolId())) {
                return InterceptDecision.suspend("tool requires approval: " + call.toolId());
            }
            return InterceptDecision.pass();
        });

        LoopProgram program = LoopProgram.builder("permission")
            .node("tool", NodeType.TOOL, Steps.tool("secret", context -> Map.of()))
            .node("finish", NodeType.OUTPUT, Steps.finish())
            .build();

        LoopHandle handle = engine.run(program, Map.of());
        LoopResult result = handle.result().get(5, TimeUnit.SECONDS);

        assertEquals(LoopResult.Status.SUSPENDED, result.status());
        assertEquals("tool requires approval: secret", result.message());
    }

    /** 拒绝时替换为失败结果，并且底层工具不执行。 */
    @Test
    void replacedResultSkipsUnderlyingToolExecution() {
        DefaultLoopEngine engine = new DefaultLoopEngine();
        NamedTool secret = new NamedTool("secret");
        engine.addTool(secret);
        engine.addInterceptor(InterceptPoint.BEFORE_TOOL, 0, context -> {
            if (!(context.payload() instanceof ToolCall call)) {
                return InterceptDecision.pass();
            }
            if ("secret".equals(call.toolId())) {
                return InterceptDecision.replace(ToolResult.failure(call.id(), "denied by tool policy"));
            }
            return InterceptDecision.pass();
        });

        LoopProgram program = LoopProgram.builder("permission")
            .node("tool", NodeType.TOOL, Steps.tool("secret", context -> Map.of()))
            .node("finish", NodeType.OUTPUT, Steps.finish())
            .build();

        LoopResult result = engine.run(program, Map.of()).result().join();

        assertEquals(LoopResult.Status.COMPLETED, result.status());
        assertFalse(secret.called());
        Object raw = result.finalSnapshot().variables().get("lastToolResult");
        assertInstanceOf(ToolResult.class, raw);
        ToolResult toolResult = (ToolResult) raw;
        assertFalse(toolResult.ok());
        assertEquals("denied by tool policy", toolResult.error());
    }

    /** 测试用命名工具。 */
    static class NamedTool implements Tool {

        /** 工具 id。 */
        private final String id;
        /** 是否真正执行过。 */
        private boolean called;

        /** 创建指定 id 的工具。 */
        NamedTool(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(id, id, Map.of());
        }

        @Override
        public ToolResult call(ToolCall call, LoopContext context) {
            called = true;
            return ToolResult.success(call.id(), Map.of());
        }

        boolean called() {
            return called;
        }
    }
}
