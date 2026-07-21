package site.sorghum.loopra.bin.model;

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

    ONode parseResponse(ONode response, String responseText) throws IOException;

    void processStreamChunk(ONode chunk, ModelClient.StreamCallback callback,
                            ModelApiStreamState state);

    default String streamCompletionError(ModelApiStreamState state) {
        return null;
    }

    default void completeStream(ModelApiStreamState state, ModelClient.StreamCallback callback) {
    }

    record RequestContext(String model, String reasoningEffort, List<ChatMessage> messages,
                          ONode tools, String userId, String sessionId) {
    }
}
