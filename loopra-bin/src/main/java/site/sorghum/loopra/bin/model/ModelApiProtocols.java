package site.sorghum.loopra.bin.model;

/**
 * Resolves configured protocol names to their mapping strategies.
 */
final class ModelApiProtocols {

    private ModelApiProtocols() {
    }

    static ModelApiProtocol resolve(String protocol) {
        if (ResponsesApiProtocol.PROTOCOL_NAME.equalsIgnoreCase(protocol)) {
            return new ResponsesApiProtocol();
        }
        return new ChatCompletionsApiProtocol();
    }
}
