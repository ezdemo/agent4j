package site.sorghum.loopra.bin.model;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ModelApiError 瞬时错误判定测试。
 *
 * <p>重点回归：OpenAI 兼容网关（如智谱 glm）把配额/限流 429 的内层错误
 * {@code type} 标成 {@code invalid_request_error}，此时 HTTP 429/5xx 状态
 * 必须优先于 {@code invalid_request_error} 子串，按瞬时错误参与重试。</p>
 */
class ModelApiErrorTest {

    /** 回归场景：线上真实报文——429 配额错误，内层嵌套 invalid_request_error。 */
    @Test
    void nestedQuota429WithInvalidRequestType_isTransient() {
        String error = "provider stream returned HTTP 429: "
            + "{\"error\":{\"message\":\"[openai-compatible-chat-7294dd17-77b3-45a5-8da8-424a3a61e58f/glm-5.2] [429]: "
            + "{\\\"error\\\":{\\\"message\\\":\\\"Workspace allocated quota exceeded, please increase your quota limit.\\\","
            + "\\\"type\\\":\\\"invalid_request_error\\\",\\\"code\\\":\\\"insufficient_quota\\\"}} (reset after 2s)\","
            + "\"type\":\"upstream_error\",\"param\":\"\",\"code\":null}}";
        assertTrue(ModelApiError.isTransientModelError(error));
    }

    /** HTTP 429 文本标记必须压过内层 invalid_request_error 排除。 */
    @Test
    void http429MarkerWinsOverNestedInvalidRequestType() {
        String error = "provider returned HTTP 429: "
            + "{\"error\":{\"message\":\"rate limited\",\"type\":\"invalid_request_error\",\"code\":\"insufficient_quota\"}}";
        assertTrue(ModelApiError.isTransientModelError(error));
    }

    /** HTTP 500 同理：5xx 是权威转发信号，即使报文里混着 invalid_request_error。 */
    @Test
    void http500MarkerWinsOverNestedInvalidRequestType() {
        String error = "provider stream returned HTTP 500: "
            + "{\"error\":{\"message\":\"upstream boom\",\"type\":\"invalid_request_error\",\"code\":null}}";
        assertTrue(ModelApiError.isTransientModelError(error));
    }

    /** 外层 JSON 能解析出数字 code 429 时，即使内层是 invalid_request_error 也判瞬时。 */
    @Test
    void outerNumericCode429_isTransient() {
        String error = "{\"error\":{\"message\":\"quota exceeded\",\"type\":\"invalid_request_error\",\"code\":429}}";
        assertTrue(ModelApiError.isTransientModelError(error));
    }

    /** 纯无效请求（无 HTTP 429/5xx 信号）仍应排除，重试只会重复同样失败。 */
    @Test
    void plainInvalidRequest_isNotTransient() {
        String error = "{\"error\":{\"message\":\"bad parameter: model does not exist\","
            + "\"type\":\"invalid_request_error\",\"code\":\"invalid_param\"}}";
        assertFalse(ModelApiError.isTransientModelError(error));
    }

    /** 无效请求 + 非瞬时 HTTP 状态（如 400）不应误判为瞬时。 */
    @Test
    void invalidRequestWithHttp400_isNotTransient() {
        String error = "provider returned HTTP 400: "
            + "{\"error\":{\"message\":\"bad request\",\"type\":\"invalid_request_error\",\"code\":\"invalid_param\"}}";
        assertFalse(ModelApiError.isTransientModelError(error));
    }

    /** 上下文超限永远不重试（有独立的折叠恢复），即使报文带 HTTP 429 前缀。 */
    @Test
    void contextLengthExceededWithHttp429_isNotTransient() {
        String error = "provider returned HTTP 429: "
            + "{\"error\":{\"message\":\"maximum context length exceeded\",\"type\":\"invalid_request_error\"}}";
        assertFalse(ModelApiError.isTransientModelError(error));
    }

    /** 传输层只暴露简短 closed 消息时，异常因果链兜底判瞬时。 */
    @Test
    void closedCause_isTransientWhenMessageBlank() {
        IOException cause = new IOException("stream closed");
        assertTrue(ModelApiError.isTransientModelError("", cause));
        assertTrue(ModelApiError.isTransientModelError("provider unavailable", cause));
    }

    /** 常规瞬时信号（限流/超时/上游）不受影响。 */
    @Test
    void classicTransientSignals_stillTransient() {
        assertTrue(ModelApiError.isTransientModelError("rate limit exceeded"));
        assertTrue(ModelApiError.isTransientModelError("upstream timeout"));
        assertTrue(ModelApiError.isTransientModelError("connection reset"));
    }

    /** 空输入不判瞬时。 */
    @Test
    void blankOrNull_isNotTransient() {
        assertFalse(ModelApiError.isTransientModelError(null));
        assertFalse(ModelApiError.isTransientModelError(""));
        assertFalse(ModelApiError.isTransientModelError("   "));
    }
}