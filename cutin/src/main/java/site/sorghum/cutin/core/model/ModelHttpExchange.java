package site.sorghum.cutin.core.model;

import java.util.Map;

public record ModelHttpExchange(
    String endpoint,
    Map<String, String> headers,
    String body
) {
    public ModelHttpExchange {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        body = body == null ? "" : body;
        endpoint = endpoint == null ? "" : endpoint;
    }
}
