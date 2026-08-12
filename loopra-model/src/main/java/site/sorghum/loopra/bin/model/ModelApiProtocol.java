package site.sorghum.loopra.bin.model;

import okhttp3.Request;
import org.noear.snack4.ONode;
import site.sorghum.loopra.bin.agent.model.ChatMessage;

import java.io.IOException;
import java.util.List;

/**
 * API protocol strategy used by {@link HttpModelClient}.
 *
 * <p>Implementations own protocol-specific request and response mapping while
 * the client owns HTTP transport, retry, cancellation and logging.</p>
 */
interface ModelApiProtocol {

    String name();

    ONode buildRequest(RequestContext context);

    /**
     * 按协议写入认证请求头。默认 OpenAI 兼容的 Bearer 方式，
     * 其他协议（如 Anthropic 的 x-api-key）自行覆盖。
     */
    default void applyAuthHeaders(Request.Builder builder, String apiKey) {
        builder.addHeader("Authorization", "Bearer " + apiKey);
    }

    ONode parseResponse(ONode response, String responseText) throws IOException;

    void processStreamChunk(ONode chunk, ModelClient.StreamCallback callback,
                            ModelApiStreamState state);

    default String streamCompletionError(ModelApiStreamState state) {
        return null;
    }

    default void completeStream(ModelApiStreamState state, ModelClient.StreamCallback callback) {
    }

    record RequestContext(String model, String reasoningEffort, List<ChatMessage> messages,
                          ONode tools, String userId, String sessionId, String apiUrl, String serviceTier) {
        RequestContext(String model, String reasoningEffort, List<ChatMessage> messages,
                       ONode tools, String userId, String sessionId) {
            this(model, reasoningEffort, messages, tools, userId, sessionId, null, null);
        }
    }
}
