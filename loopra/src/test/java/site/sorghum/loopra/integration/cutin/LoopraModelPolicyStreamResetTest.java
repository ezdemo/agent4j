package site.sorghum.loopra.integration.cutin;

import org.junit.jupiter.api.Test;
import site.sorghum.cutin.core.context.LoopContext;
import site.sorghum.cutin.core.context.Usage;
import site.sorghum.cutin.core.loop.InterceptContext;
import site.sorghum.cutin.core.loop.InterceptPoint;
import site.sorghum.cutin.core.loop.InterceptionResult;
import site.sorghum.cutin.core.model.StreamChunk;
import site.sorghum.cutin.core.plugin.DefaultLoopRegistrar;
import site.sorghum.loopra.bin.agent.resilient.ReasonBreaker;
import site.sorghum.loopra.bin.model.LoopraModelProvider;
import site.sorghum.loopra.integration.cutin.plugin.policy.LoopraModelPolicyPlugin;
import site.sorghum.loopra.integration.cutin.plugin.policy.LoopraPolicyHost;
import site.sorghum.loopra.tool.AgentOutput;
import site.sorghum.loopra.tool.ChoiceOption;
import site.sorghum.loopra.tool.LogLevel;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ReasonBreaker 硬触发重试时，服务端必须通知前端作废本轮已流出的思考/正文
 * （stream_reset 事件），否则两轮模型输出叠加在同一条消息里，
 * 视觉上表现为"同一个思考重复渲染"。
 */
class LoopraModelPolicyStreamResetTest {

    @Test
    void softWarningDoesNotEmitStreamReset() {
        RecordingHost host = new RecordingHost();
        DefaultLoopRegistrar registrar = new DefaultLoopRegistrar();
        new LoopraModelPolicyPlugin(host).register(registrar);
        LoopContext context = context();

        InterceptionResult result = streamChunk(registrar, context, LOOPING_REASONING);

        assertFalse(result.decision().isRetry());
        assertEquals(List.of(), host.resetEvents);
        assertEquals(0, host.reminderCount);
    }

    @Test
    void hardTriggerEmitsStreamResetBeforeRetry() {
        RecordingHost host = new RecordingHost();
        DefaultLoopRegistrar registrar = new DefaultLoopRegistrar();
        new LoopraModelPolicyPlugin(host).register(registrar);
        LoopContext context = context();

        // 第一次命中：软警告
        streamChunk(registrar, context, LOOPING_REASONING);
        // 第二次命中：硬触发 → abort + stream_reset + retry
        InterceptionResult result = streamChunk(registrar, context, LOOPING_REASONING);

        assertTrue(result.decision().isRetry(), "连续两次检测到思考循环应触发重试");
        assertEquals(List.of("stream_reset"), host.resetEvents, "硬触发重试前应通知前端作废本轮流式内容");
        assertEquals(1, host.reminderCount);
        assertEquals(1, host.reasonBreaker.getTriggerCount());
    }

    /** 450 字符非 uniform 窗口重复 4 次、间隔均超过 MIN_GAP，ReasonBreaker 必然判定循环。
     * 注意窗口不能全由同一字符构成（如 "M"*450），isUniform 会跳过这类窗口。 */
    private static final String LOOPING_REASONING =
        "M".repeat(449) + "N"
        + "X".repeat(1000)
        + "M".repeat(449) + "N"
        + "Y".repeat(1000)
        + "M".repeat(449) + "N"
        + "Z".repeat(1000)
        + "M".repeat(449) + "N";

    private static InterceptionResult streamChunk(DefaultLoopRegistrar registrar,
                                                  LoopContext context, String reasoning) {
        StreamChunk chunk = new StreamChunk(
            "", reasoning, List.of(), List.of(), Usage.ZERO, Map.of(), false);
        return registrar.interceptors().run(
            InterceptPoint.ON_MODEL_STREAM,
            new InterceptContext(InterceptPoint.ON_MODEL_STREAM, "model", null, context, chunk));
    }

    private static LoopContext context() {
        Map<String, Object> variables = new HashMap<>();
        return (LoopContext) Proxy.newProxyInstance(
            LoopContext.class.getClassLoader(),
            new Class<?>[]{LoopContext.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "variables" -> variables;
                case "putVariable" -> variables.put(String.valueOf(args[0]), args[1]);
                case "usage" -> Usage.ZERO;
                case "stateVersion" -> 0L;
                case "id" -> "model-policy-stream-reset-test";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "ModelPolicyStreamResetTestContext";
                default -> null;
            }
        );
    }

    private static final class RecordingHost implements LoopraPolicyHost {

        private final List<String> resetEvents = new ArrayList<>();
        private int reminderCount;
        private final ReasonBreaker reasonBreaker = new ReasonBreaker();
        private final LoopraModelProvider modelProvider =
                new LoopraModelProvider("http://localhost:0", "test", "test-model");

        @Override
        public AgentOutput getOutput() {
            return new AgentOutput() {
                public void onContentDelta(String token) { }
                public void onContentComplete() { }
                public void onReasoningDelta(String token) { }
                public void onReasoning(String reasoning) { }
                public void onReasoningStarted() { }
                public void onToolCall(String name, String args) { }
                public void onToolResult(String name, String result) { }
                public void onUsage(int prompt, int completion, int total, int hit, int miss) { }
                public void onError(String error) { }
                public void onLog(LogLevel level, String message) { }
                public void onChoice(List<ChoiceOption> options) { }

                @Override
                public void sendEvent(String type, String data) {
                    resetEvents.add(type);
                }
            };
        }

        @Override
        public ReasonBreaker reasonBreaker() {
            return reasonBreaker;
        }

        @Override
        public LoopraModelProvider getModelProvider() {
            return modelProvider;
        }

        @Override
        public void injectReasonBreakReminder() {
            reminderCount++;
        }

        @Override
        public site.sorghum.loopra.bin.agent.model.PreparedMessages prepareCutinMessages(
                site.sorghum.cutin.core.context.DefaultLoopContext context, int step) {
            return null;
        }

        @Override
        public String interceptHITLFromCutin(site.sorghum.cutin.core.model.ModelResponse response) {
            return null;
        }

        @Override
        public site.sorghum.loopra.bin.tool.ToolRegistry getToolRegistry() {
            return null;
        }

        @Override
        public boolean isPlanMode() {
            return false;
        }

        @Override
        public site.sorghum.cutin.core.tool.ToolResult rejectCutinTool(
                site.sorghum.cutin.core.tool.ToolCall call, String message, String reason) {
            return null;
        }

        @Override
        public site.sorghum.loopra.bin.agent.resilient.StormBreaker stormBreaker() {
            return null;
        }

        @Override
        public void markCutinStormSuppressed() {
        }
    }
}
