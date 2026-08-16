package site.sorghum.loopra.bin.model;

import org.noear.snack4.ONode;
import site.sorghum.cutin.core.context.Message;
import site.sorghum.cutin.core.context.Usage;
import site.sorghum.cutin.core.model.ModelCallRequest;
import site.sorghum.cutin.core.model.ModelResponse;
import site.sorghum.cutin.core.model.StreamChunk;
import site.sorghum.cutin.core.tool.ToolCall;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * LoopraModelProvider 的测试替身：按测试场景返回固定响应或流。
 */
public class TestLoopraProvider extends LoopraModelProvider {

    @FunctionalInterface
    public interface CallHandler {
        ModelResponse call(ModelCallRequest request);
    }

    @FunctionalInterface
    public interface StreamHandler {
        Stream<StreamChunk> stream(ModelCallRequest request);
    }

    private final CallHandler callHandler;
    private final StreamHandler streamHandler;
    private final int contextTokens;
    private final AtomicInteger resetStreamAbortCalls = new AtomicInteger();
    private final List<String> sessionAffinities = new ArrayList<>();

    private TestLoopraProvider(String model, String channelId, int contextTokens,
                               CallHandler callHandler, StreamHandler streamHandler) {
        super("http://localhost/v1/chat/completions", "test-key", model, "high", channelId, "chat_completions");
        this.callHandler = callHandler;
        this.streamHandler = streamHandler;
        this.contextTokens = contextTokens;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static TestLoopraProvider returning(String content) {
        return builder()
                .call(request -> response(content))
                .stream(request -> Stream.of(contentChunk(content), terminalChunk()))
                .build();
    }

    public static TestLoopraProvider returningCall(String content) {
        return builder().call(request -> response(content)).build();
    }

    public static TestLoopraProvider returningStream(String content) {
        return builder().stream(request -> Stream.of(contentChunk(content), terminalChunk())).build();
    }

    public static TestLoopraProvider toolCallsFirstThenAnswer(ONode toolCalls, String answer) {
        AtomicInteger streams = new AtomicInteger();
        return builder().stream(request -> {
            if (streams.getAndIncrement() == 0) {
                return Stream.of(toolCallsChunk(toolCalls), terminalChunk());
            }
            return Stream.of(contentChunk(answer), terminalChunk());
        }).build();
    }

    public static TestLoopraProvider contextOverflowThen(String error, String success) {
        AtomicInteger streams = new AtomicInteger();
        return builder().stream(request -> {
            if (streams.getAndIncrement() == 0) {
                return Stream.of(errorChunk(error));
            }
            return Stream.of(contentChunk(success), terminalChunk());
        }).build();
    }

    public static TestLoopraProvider failingCall(RuntimeException exception) {
        return builder().call(request -> {
            throw exception;
        }).build();
    }

    public static TestLoopraProvider usageStream(String content, Usage usage) {
        return builder().model("usage-stub").stream(request -> Stream.of(
                new StreamChunk(content, null, List.of(), List.of(), usage, Map.of(), false),
                terminalChunk()
        )).build();
    }

    public static Stream<StreamChunk> contentStream(String content) {
        return Stream.of(contentChunk(content), terminalChunk());
    }

    public static Stream<StreamChunk> errorStream(String error) {
        return Stream.of(errorChunk(error));
    }

    public static Stream<StreamChunk> toolCallsStream(ONode toolCalls) {
        return Stream.of(toolCallsChunk(toolCalls), terminalChunk());
    }

    @Override
    public ModelResponse call(ModelCallRequest request) {
        return callHandler.call(request);
    }

    @Override
    public Stream<StreamChunk> stream(ModelCallRequest request) {
        return streamHandler.stream(request);
    }

    @Override
    public int getMaxContextTokens() {
        return contextTokens > 0 ? contextTokens : super.getMaxContextTokens();
    }

    @Override
    public void resetStreamAbort() {
        resetStreamAbortCalls.incrementAndGet();
    }

    @Override
    public void setSessionAffinity(String sessionAffinity) {
        sessionAffinities.add(sessionAffinity);
        super.setSessionAffinity(sessionAffinity);
    }

    public int resetStreamAbortCount() {
        return resetStreamAbortCalls.get();
    }

    public List<String> sessionAffinities() {
        return List.copyOf(sessionAffinities);
    }

    public static ModelResponse response(String content) {
        return ModelResponse.of(new Message("assistant", content, null, List.of(), Map.of()), Usage.ZERO);
    }

    public static ModelResponse toolCallsResponse(ONode toolCalls) {
        return ModelResponse.of(
                new Message("assistant", "", null, toToolCalls(toolCalls), Map.of()),
                Usage.ZERO
        );
    }

    private static StreamChunk contentChunk(String content) {
        return new StreamChunk(content, null, List.of(), List.of(), Usage.ZERO, Map.of(), false);
    }

    private static StreamChunk toolCallsChunk(ONode toolCalls) {
        return new StreamChunk("", null, toToolCalls(toolCalls), List.of(), Usage.ZERO, Map.of(), false);
    }

    private static StreamChunk errorChunk(String error) {
        return new StreamChunk("", null, List.of(), List.of(), Usage.ZERO,
                Map.of("error", error), true);
    }

    private static StreamChunk terminalChunk() {
        return new StreamChunk("", null, List.of(), List.of(), Usage.ZERO, Map.of(), true);
    }

    private static List<ToolCall> toToolCalls(ONode calls) {
        List<ToolCall> result = new ArrayList<>();
        if (calls == null || !calls.isArray()) {
            return result;
        }
        for (ONode call : calls.getArray()) {
            String id = call.get("id").getString();
            ONode function = call.get("function");
            String name = function.get("name").getString();
            ONode arguments = function.get("arguments");
            Map<String, Object> args = arguments.isObject()
                    ? toMap(arguments.toBean(Map.class))
                    : parseArguments(arguments.getString());
            result.add(new ToolCall(id, name, args, id));
        }
        return result;
    }

    private static Map<String, Object> parseArguments(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Object bean = ONode.ofJson(json).toBean(Map.class);
            return bean instanceof Map<?, ?> map ? toMap(map) : Map.of();
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    private static Map<String, Object> toMap(Map<?, ?> source) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    public static final class Builder {
        private String model = "test-model";
        private String channelId;
        private int contextTokens;
        private CallHandler callHandler = request -> response("");
        private StreamHandler streamHandler = request -> Stream.of(terminalChunk());

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder channelId(String channelId) {
            this.channelId = channelId;
            return this;
        }

        public Builder contextTokens(int contextTokens) {
            this.contextTokens = contextTokens;
            return this;
        }

        public Builder call(CallHandler callHandler) {
            this.callHandler = callHandler;
            return this;
        }

        public Builder stream(StreamHandler streamHandler) {
            this.streamHandler = streamHandler;
            return this;
        }

        public TestLoopraProvider build() {
            return new TestLoopraProvider(model, channelId, contextTokens, callHandler, streamHandler);
        }
    }
}
