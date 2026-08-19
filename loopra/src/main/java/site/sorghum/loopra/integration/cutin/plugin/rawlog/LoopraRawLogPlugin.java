package site.sorghum.loopra.integration.cutin.plugin.rawlog;

import lombok.extern.slf4j.Slf4j;
import site.sorghum.cutin.core.json.JsonSupport;
import site.sorghum.cutin.core.loop.InterceptContext;
import site.sorghum.cutin.core.loop.InterceptDecision;
import site.sorghum.cutin.core.loop.InterceptPoint;
import site.sorghum.cutin.core.model.ModelHttpExchange;
import site.sorghum.cutin.core.model.ModelResponse;
import site.sorghum.cutin.core.plugin.AgentPlugin;
import site.sorghum.cutin.core.plugin.LoopPlugin;
import site.sorghum.cutin.core.plugin.LoopRegistrar;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 在模型响应完成后把 provider 的原始请求与响应体打印到应用日志。
 *
 * <p>同步调用时 {@link ModelResponse#raw()} 为完整 JSON 响应体；
 * 流式调用时为全部 SSE 数据行按到达顺序以换行拼接的内容。
 * 原始请求体与请求头通过 {@link ModelResponse#request()} 获取，
 * 包含 {@code prompt_cache_key} 等 wire 级字段。</p>
 */
@Slf4j
@AgentPlugin(id = "loopra-raw-log")
public final class LoopraRawLogPlugin implements LoopPlugin {

    private final boolean logRequest;

    public LoopraRawLogPlugin() {
        this(true);
    }

    public LoopraRawLogPlugin(boolean logRequest) {
        this.logRequest = logRequest;
    }

    @Override
    public String id() {
        return "loopra-raw-log";
    }

    @Override
    public void register(LoopRegistrar registrar) {
        registrar.registerInterceptor(InterceptPoint.AFTER_MODEL, -1000, this::printRaw);
    }

    private InterceptDecision printRaw(InterceptContext context) {
        if (!(context.payload() instanceof ModelResponse response)) {
            return InterceptDecision.pass();
        }
        if (logRequest) {
            ModelHttpExchange request = response.request();
            if (request != null && !request.body().isBlank()) {
                log.info("[raw] provider 原始请求 endpoint={} headers={} body=\n{}",
                    request.endpoint(), sanitizeHeaders(request.headers()), pretty(request.body()));
            } else if (request != null) {
                log.info("[raw] provider 原始请求 endpoint={} headers={}", request.endpoint(), sanitizeHeaders(request.headers()));
            } else {
                log.info("[raw] 该模型调用未附带原始请求（Provider 未启用或响应被拦截器替换）");
            }
        }
        String raw = response.raw();
        if (raw == null || raw.isBlank()) {
            log.info("[raw] 该模型调用未附带原始响应体（Provider 未启用或响应被拦截器替换）");
            return InterceptDecision.pass();
        }
        log.info("[raw] provider 原始响应体：\n{}", raw);
        return InterceptDecision.pass();
    }

    private static Map<String, String> sanitizeHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        Map<String, String> sanitized = new LinkedHashMap<>();
        headers.forEach((key, value) -> {
            String lower = key.toLowerCase();
            if (lower.equals("authorization") || lower.equals("x-api-key") || lower.equals("api-key")) {
                sanitized.put(key, "***");
            } else {
                sanitized.put(key, value);
            }
        });
        return Map.copyOf(sanitized);
    }

    private static String pretty(String json) {
        try {
            return JsonSupport.read(json).toJson();
        } catch (RuntimeException ignored) {
            return json;
        }
    }
}
