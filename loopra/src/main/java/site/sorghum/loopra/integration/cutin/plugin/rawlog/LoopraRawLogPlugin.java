package site.sorghum.loopra.integration.cutin.plugin.rawlog;

import lombok.extern.slf4j.Slf4j;
import site.sorghum.cutin.core.loop.InterceptContext;
import site.sorghum.cutin.core.loop.InterceptDecision;
import site.sorghum.cutin.core.loop.InterceptPoint;
import site.sorghum.cutin.core.model.ModelResponse;
import site.sorghum.cutin.core.plugin.AgentPlugin;
import site.sorghum.cutin.core.plugin.LoopPlugin;
import site.sorghum.cutin.core.plugin.LoopRegistrar;

/**
 * 在模型响应完成后把 provider 的原始响应体打印到应用日志。
 *
 * <p>同步调用时 {@link ModelResponse#raw()} 为完整 JSON 响应体；
 * 流式调用时为全部 SSE 数据行按到达顺序以换行拼接的内容。</p>
 */
@Slf4j
@AgentPlugin(id = "loopra-raw-log")
public final class LoopraRawLogPlugin implements LoopPlugin {

    @Override
    public String id() {
        return "loopra-raw-log";
    }

    @Override
    public void register(LoopRegistrar registrar) {
        registrar.addInterceptor(InterceptPoint.AFTER_MODEL, -1000, this::printRaw);
    }

    private InterceptDecision printRaw(InterceptContext context) {
        if (!(context.payload() instanceof ModelResponse response)) {
            return InterceptDecision.pass();
        }
        String raw = response.raw();
        if (raw == null || raw.isBlank()) {
            log.info("[raw] 该模型调用未附带原始响应体（Provider 未启用或响应被拦截器替换）");
            return InterceptDecision.pass();
        }
        log.info("[raw] provider 原始响应体：\n{}", raw);
        return InterceptDecision.pass();
    }
}
