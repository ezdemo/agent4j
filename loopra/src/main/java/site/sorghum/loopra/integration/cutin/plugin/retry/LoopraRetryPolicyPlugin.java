package site.sorghum.loopra.integration.cutin.plugin.retry;

import lombok.extern.slf4j.Slf4j;
import site.sorghum.cutin.core.loop.InterceptContext;
import site.sorghum.cutin.core.loop.InterceptDecision;
import site.sorghum.cutin.core.loop.InterceptPoint;
import site.sorghum.cutin.core.model.ModelCallError;
import site.sorghum.cutin.core.plugin.AgentPlugin;
import site.sorghum.cutin.core.plugin.LoopPlugin;
import site.sorghum.cutin.core.plugin.LoopRegistrar;
import site.sorghum.loopra.bin.model.ModelApiError;
import site.sorghum.loopra.tool.LogLevel;

import java.util.Objects;

/**
  * 把瞬时模型错误的重试从 HTTP 传输层移入 cutin 拦截器链。
 * <p>
  * {@code ON_MODEL_ERROR} 判断失败是否可重试以及剩余尝试次数；
  * {@code BEFORE_RETRY} 执行退避并发出用户可见警告。
  * 替换该插件即可替换 Loopra 的重试策略。
 * </p>
 */
@AgentPlugin(id = "loopra-retry-policy")
@Slf4j
public final class LoopraRetryPolicyPlugin implements LoopPlugin {

    private static final String RETRY_COUNT_KEY = "loopraModelRetryAttempts";
    private static final String RETRY_DELAY_KEY = "loopraModelRetryDelaySeconds";
    private static final String RETRY_REASON_KEY = "loopraModelRetryReason";

    private final LoopraRetryHost host;

    public LoopraRetryPolicyPlugin(LoopraRetryHost host) {
        this.host = Objects.requireNonNull(host, "host");
    }

    @Override
    public String id() {
        return "loopra-retry-policy";
    }

    @Override
    public void register(LoopRegistrar registrar) {
        registrar.registerInterceptor(InterceptPoint.ON_MODEL_ERROR, -50, this::onModelError);
        registrar.registerInterceptor(InterceptPoint.AFTER_MODEL, 50, this::afterModel);
        registrar.registerInterceptor(InterceptPoint.BEFORE_RETRY, 0, this::beforeRetry);
    }

    /** 成功拿到模型响应后，下一次独立模型调用应重新计算重试预算。 */
    private InterceptDecision afterModel(InterceptContext context) {
        context.context().putVariable(RETRY_COUNT_KEY, 0);
        context.context().putVariable(RETRY_DELAY_KEY, 0);
        context.context().putVariable(RETRY_REASON_KEY, "");
        return InterceptDecision.pass();
    }

    private InterceptDecision onModelError(InterceptContext context) {
        log.info("onModelError: {}", context.payload());
        if (!(context.payload() instanceof ModelCallError error)
                || !ModelApiError.isTransientModelError(error.message(), error.cause())) {
            return InterceptDecision.pass();
        }
        int attempts = intVariable(context, RETRY_COUNT_KEY) + 1;
        if (attempts > host.maxModelRetries()) {
            context.context().putVariable(RETRY_COUNT_KEY, 0);
            return InterceptDecision.pass();
        }
        context.context().putVariable(RETRY_COUNT_KEY, attempts);
        context.context().putVariable(RETRY_DELAY_KEY, host.modelRetryDelaySeconds(attempts - 1));
        context.context().putVariable(RETRY_REASON_KEY, error.message());
        return InterceptDecision.retry("transient model error");
    }

    private InterceptDecision beforeRetry(InterceptContext context) {
        Object delay = context.context().variables().get(RETRY_DELAY_KEY);
        if (!(delay instanceof Number number)) {
            return InterceptDecision.pass();
        }
        int delaySeconds = number.intValue();
        context.context().putVariable(RETRY_DELAY_KEY, 0);
        String reason = String.valueOf(context.context().variables().getOrDefault(RETRY_REASON_KEY, ""));
        String message = "AI 接口暂时不可用（" + reason + "），将在 " + delaySeconds
                + " 秒后重试（" + intVariable(context, RETRY_COUNT_KEY) + "/"
                + host.maxModelRetries() + "）";
        try {
            host.getOutput().onLog(LogLevel.WARN, message);
            if (delaySeconds > 0) {
                Thread.sleep(delaySeconds * 1000L);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        return InterceptDecision.pass();
    }

    private static int intVariable(InterceptContext context, String key) {
        Object value = context.context().variables().get(key);
        return value instanceof Number number ? number.intValue() : 0;
    }
}
